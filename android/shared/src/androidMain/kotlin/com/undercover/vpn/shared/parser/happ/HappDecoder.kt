package com.undercover.vpn.shared.parser.happ

import java.math.BigInteger
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Arrays
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object HappDecoder {

    private val rsaKeyCache = ConcurrentHashMap<String, PrivateKey>()
    private val AES_GCM_IV = "kkkkkkkkkkkk".toByteArray(Charsets.US_ASCII)

    private val SUPPORTED_SCHEMES = listOf(
        "http://", "https://", "vless://", "vmess://", "trojan://", "ss://",
        "hy2://", "hysteria2://", "socks://", "socks5://", "wireguard://", "wg://", "tuic://"
    )

    fun isHappOrV2Link(link: String): Boolean {
        val trimmed = link.trim()
        if (trimmed.startsWith("happ://", ignoreCase = true) ||
            trimmed.startsWith("v2raytun://", ignoreCase = true) ||
            trimmed.startsWith("incy://", ignoreCase = true)
        ) {
            return true
        }
        val unwrapped = unwrap(trimmed)
        return unwrapped.startsWith("happ://", ignoreCase = true) ||
                unwrapped.startsWith("v2raytun://", ignoreCase = true) ||
                unwrapped.startsWith("incy://", ignoreCase = true)
    }

    /**
     * Resolves wrappers such as happ://add/, v2raytun://import/, incy://add/,
     * or embedded happ/v2raytun links in HTTP URLs.
     */
    fun unwrap(input: String): String {
        var cur = input.trim()
        for (i in 0 until 6) {
            val next = unwrapOnce(cur)
            if (next != null && next != cur) {
                cur = next
            } else {
                break
            }
        }
        return cur
    }

    private fun unwrapOnce(s: String): String? {
        val t = s.trim()
        unwrapWrapper(t)?.let { return it }
        stripIncyPrefix(t)?.let { return it }

        if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) {
            extractEmbedded(t, "happ")?.let { return it }
            extractEmbedded(t, "v2raytun")?.let { return it }
            extractEmbeddedIncy(t)?.let { return it }
        }
        return null
    }

    private fun unwrapWrapper(s: String): String? {
        val t = s.trim()
        val inner = when {
            t.startsWith("happ://add/", ignoreCase = true) -> t.substring("happ://add/".length)
            t.startsWith("v2raytun://import/", ignoreCase = true) -> t.substring("v2raytun://import/".length)
            else -> return null
        }
        var rest = inner.trim()
        if (rest.isEmpty()) return null

        if (rest.contains('%')) {
            val dec = percentDecode(rest)
            if (dec.isNotEmpty()) {
                rest = dec.trim()
            }
        }
        if (looksLikeSchemeLink(rest)) {
            return rest
        }
        decodeBase64Link(rest)?.let { return it }
        return rest.ifEmpty { null }
    }

    private fun stripIncyPrefix(s: String): String? {
        val t = s.trim()
        val inner = when {
            t.startsWith("incy://add/", ignoreCase = true) -> t.substring("incy://add/".length)
            t.startsWith("incy://import/", ignoreCase = true) -> t.substring("incy://import/".length)
            else -> return null
        }
        var rest = inner.trim()
        if (rest.isEmpty()) return null

        if (rest.contains('%')) {
            val dec = percentDecode(rest)
            if (dec.isNotEmpty()) {
                rest = dec.trim()
            }
        }
        if (looksLikeSchemeLink(rest)) {
            return rest
        }
        decodeBase64Link(rest)?.let { return it }
        return rest.ifEmpty { null }
    }

    private fun extractEmbedded(raw: String, scheme: String): String? {
        return extractEmbeddedDepth(raw, scheme, 0)
    }

    private fun extractEmbeddedDepth(raw: String, scheme: String, depth: Int): String? {
        val trimmed = raw.trim()
        val full = "$scheme://"
        if (trimmed.startsWith(full, ignoreCase = true)) return null
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return null
        }

        val start = indexOfScheme(trimmed, scheme)
        if (start == -1) {
            if (depth < 2 && trimmed.contains("$scheme%", ignoreCase = true)) {
                val once = percentDecode(trimmed)
                if (once != trimmed) {
                    return extractEmbeddedDepth(once, scheme, depth + 1)
                }
            }
            return null
        }

        val candidate = carveCandidate(trimmed, start)
        var decoded = percentDecode(candidate).trim()
        var guard = 0
        while (guard < 2 && decoded.startsWith("$scheme%", ignoreCase = true)) {
            val next = percentDecode(decoded)
            val nextTrimmed = next.trim()
            if (nextTrimmed == decoded) break
            decoded = nextTrimmed
            guard++
        }
        if (!decoded.startsWith(full, ignoreCase = true) || decoded.length <= full.length || decoded == trimmed) {
            return null
        }
        return decoded
    }

    private fun extractEmbeddedIncy(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("incy://", ignoreCase = true)) return null
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return null
        }

        val start = indexOfScheme(trimmed, "incy")
        if (start == -1) {
            if (trimmed.contains("incy%", ignoreCase = true)) {
                val once = percentDecode(trimmed)
                if (once != trimmed) {
                    return extractEmbedded(once, "incy")
                }
            }
            return null
        }

        val candidate = carveCandidate(trimmed, start)
        val decoded = percentDecode(candidate).trim()
        if (decoded.startsWith("incy://add/", ignoreCase = true) || decoded.startsWith("incy://import/", ignoreCase = true)) {
            return decoded
        }
        return null
    }

    private fun indexOfScheme(s: String, scheme: String): Int {
        val lower = s.lowercase()
        val full = "$scheme://"
        val encoded = "$scheme%3a"
        val idx1 = lower.indexOf(full)
        val idx2 = lower.indexOf(encoded)
        return when {
            idx1 != -1 && idx2 != -1 -> minOf(idx1, idx2)
            idx1 != -1 -> idx1
            idx2 != -1 -> idx2
            else -> -1
        }
    }

    private fun carveCandidate(s: String, start: Int): String {
        var end = start
        while (end < s.length && !isLinkDelimiter(s[end])) {
            end++
        }
        return s.substring(start, end)
    }

    private fun isLinkDelimiter(c: Char): Boolean {
        if (c.code !in 0x20..0x7e) return true
        return c in " &#\"'`<>\\|^}{]["
    }

    private fun looksLikeSchemeLink(s: String): Boolean {
        val lower = s.lowercase()
        return SUPPORTED_SCHEMES.any { lower.startsWith(it) }
    }

    private fun decodeBase64Link(s: String): String? {
        return try {
            val decoded = String(b64Dec(s.toByteArray(Charsets.US_ASCII)), Charsets.UTF_8).trim()
            if (looksLikeSchemeLink(decoded)) decoded else null
        } catch (_: Exception) {
            null
        }
    }

    private fun percentDecode(s: String): String {
        return try {
            URLDecoder.decode(s, "UTF-8")
        } catch (_: Exception) {
            s
        }
    }

    /**
     * Decrypts any Happ or v2RayTun encrypted link.
     * Returns the decrypted URL / proxy URI, or the unwrapped link if not encrypted,
     * or null if decryption fails.
     */
    fun decrypt(rawInput: String): String? {
        val unwrapped = unwrap(rawInput)
        if (looksLikeSchemeLink(unwrapped)) {
            return unwrapped
        }

        val result = try {
            when {
                unwrapped.startsWith("happ://crypt/", ignoreCase = true) -> {
                    val payload = unwrapped.substring("happ://crypt/".length)
                    decryptCrypt(0, payload)
                }
                unwrapped.startsWith("happ://crypt2/", ignoreCase = true) -> {
                    val payload = unwrapped.substring("happ://crypt2/".length)
                    decryptCrypt(1, payload)
                }
                unwrapped.startsWith("happ://crypt3/", ignoreCase = true) -> {
                    val payload = unwrapped.substring("happ://crypt3/".length)
                    decryptCrypt(2, payload)
                }
                unwrapped.startsWith("happ://crypt4/", ignoreCase = true) -> {
                    val payload = unwrapped.substring("happ://crypt4/".length)
                    decryptCrypt(3, payload)
                }
                unwrapped.startsWith("happ://crypt5/", ignoreCase = true) -> {
                    val payload = unwrapped.substring("happ://crypt5/".length)
                    decryptCrypt5(payload)
                }
                unwrapped.startsWith("v2raytun://crypt/", ignoreCase = true) -> {
                    val payload = unwrapped.substring("v2raytun://crypt/".length)
                    decryptV2(payload)
                }
                else -> unwrapped
            }
        } catch (e: Exception) {
            null
        }

        if (result != null) {
            val nested = unwrap(result)
            return if (isHappOrV2Link(nested) && nested != rawInput) {
                decrypt(nested)
            } else {
                nested
            }
        }
        return null
    }

    // --- crypt..crypt4 RSA Decryption ---
    private fun decryptCrypt(ordinal: Int, payload: String): String {
        val pkcs8Base64 = HappKeys.CRYPT_PKCS8[ordinal]
        val privKey = getPrivateKey(pkcs8Base64)
        val ct = b64Dec(payload.toByteArray(Charsets.US_ASCII))
        val ks = (privKey as java.security.interfaces.RSAKey).modulus.bitLength() / 8
        if (ct.isEmpty() || ct.size % ks != 0) {
            throw IllegalArgumentException("Block alignment error: length ${ct.size}, keySize $ks")
        }

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privKey)
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < ct.size) {
            val chunk = cipher.doFinal(ct, i, ks)
            out.write(chunk)
            i += ks
        }
        return out.toString("UTF-8").trim()
    }

    // --- crypt5 ChaCha20-Poly1305 + RSA Decryption ---
    private fun decryptCrypt5(payload: String): String {
        val shuffled = blockPairSwap(payload.toByteArray(Charsets.US_ASCII))
        if (shuffled.size < 8) {
            throw IllegalArgumentException("crypt5 payload too short")
        }
        val n = shuffled.size
        val markerBytes = ByteArray(8)
        System.arraycopy(shuffled, 0, markerBytes, 0, 4)
        System.arraycopy(shuffled, n - 4, markerBytes, 4, 4)
        val marker = String(markerBytes, Charsets.US_ASCII)

        val pkcs8Base64 = HappKeys.CRYPT5_PKCS8[marker]
            ?: throw IllegalArgumentException("Unknown crypt5 marker: $marker")
        val privKey = getPrivateKey(pkcs8Base64)
        val body = shuffled.copyOfRange(4, n - 4)

        val preferSalted = body.size > 12 && !body[12].toInt().toChar().isDigit()
        val layouts = if (preferSalted) listOf(true, false) else listOf(false, true)

        var lastError: Exception? = null
        for (salted in layouts) {
            try {
                return decryptCrypt5Body(body, privKey, salted)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalArgumentException("Failed to decrypt crypt5 body")
    }

    private fun decryptCrypt5Body(body: ByteArray, privKey: PrivateKey, salted: Boolean): String {
        val nonce = body.copyOfRange(0, 12)
        val salt: ByteArray?
        val lenStart: Int
        if (salted) {
            if (body.size < 22) throw IllegalArgumentException("Salted header too short")
            salt = body.copyOfRange(14, 22)
            lenStart = 22
        } else {
            salt = null
            lenStart = 12
        }

        val rest = body.copyOfRange(lenStart, body.size)
        var digitCount = 0
        while (digitCount < rest.size && rest[digitCount].toInt().toChar().isDigit()) {
            digitCount++
        }
        if (digitCount == 0) throw IllegalArgumentException("Segment length missing")

        val segLenStr = String(rest, 0, digitCount, Charsets.US_ASCII)
        val segLen = segLenStr.toInt()

        val packed = rest.copyOfRange(digitCount, rest.size)
        if (packed.isEmpty() || segLen > packed.size - 1) {
            throw IllegalArgumentException("Segment truncated")
        }
        val urlB64 = packed.copyOfRange(1, 1 + segLen)
        val rsaCipher = packed.copyOfRange(1 + segLen, packed.size)

        val rsaCt = b64Dec(rsaCipher)
        val rsaCipherInst = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsaCipherInst.init(Cipher.DECRYPT_MODE, privKey)
        val rsaPlain = rsaCipherInst.doFinal(rsaCt)

        val rsaValue = b64Dec(swapPairs(rsaPlain))
        if (rsaValue.size != 32) {
            throw IllegalArgumentException("Invalid ChaCha key length: ${rsaValue.size}")
        }

        val chachaKey = if (salt != null) {
            val key = ByteArray(32)
            for (i in 0 until 32) {
                key[i] = (rsaValue[i].toInt() xor salt[i % 8].toInt()).toByte()
            }
            key
        } else {
            rsaValue
        }

        val ciphertextWithTag = b64Dec(urlB64)
        val intermediate = chachaDecrypt(chachaKey, nonce, ciphertextWithTag)
        val plain = b64Dec(swapPairs(intermediate))
        return String(plain, Charsets.UTF_8).trim()
    }

    // --- v2raytun Decryption ---
    private fun decryptV2(payload: String): String {
        val ct = b64Dec(payload.toByteArray(Charsets.US_ASCII))
        if (ct.isEmpty()) throw IllegalArgumentException("v2raytun empty ciphertext")

        for ((_, pkcs8Base64) in HappKeys.V2_KEYS) {
            try {
                val privKey = getPrivateKey(pkcs8Base64)
                val ks = (privKey as java.security.interfaces.RSAKey).modulus.bitLength() / 8
                if (ct.size % ks != 0) continue

                val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(Cipher.DECRYPT_MODE, privKey)
                val out = java.io.ByteArrayOutputStream()
                var off = 0
                var ok = true
                while (off < ct.size) {
                    try {
                        val chunk = cipher.doFinal(ct, off, ks)
                        out.write(chunk)
                    } catch (_: Exception) {
                        ok = false
                        break
                    }
                    off += ks
                }
                if (!ok) continue

                val text = out.toString("UTF-8").trim()
                if (looksLikeSchemeLink(text)) {
                    return text
                }
            } catch (_: Exception) {
                continue
            }
        }
        throw IllegalArgumentException("No matching v2raytun key found")
    }

    // --- Subscription Body Decryption (AES-128-GCM) ---
    fun decryptResponseBody(url: String, body: ByteArray, encryptTag: String?): ByteArray {
        val keyName = extractKeyParam(url) ?: return body
        val key = HappKeys.AES_GCM_KEYS[keyName] ?: return body
        val tagStr = encryptTag?.trim() ?: return body
        if (tagStr.isEmpty()) return body

        return try {
            val ct = b64Dec(body)
            val tag = b64Dec(tagStr.toByteArray(Charsets.US_ASCII))
            if (tag.size != 16) return body

            val joined = ByteArray(ct.size + tag.size)
            System.arraycopy(ct, 0, joined, 0, ct.size)
            System.arraycopy(tag, 0, joined, ct.size, tag.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, AES_GCM_IV)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            cipher.doFinal(joined)
        } catch (_: Exception) {
            body
        }
    }

    private fun extractKeyParam(url: String): String? {
        val qIdx = url.indexOf('?')
        if (qIdx == -1) return null
        val query = url.substring(qIdx + 1)
        for (part in query.split('&')) {
            val eq = part.indexOf('=')
            if (eq != -1) {
                val k = part.substring(0, eq)
                val v = part.substring(eq + 1)
                if (k.equals("key", ignoreCase = true)) {
                    return v
                }
            }
        }
        return null
    }

    // --- ChaCha20-Poly1305 Decryption (Platform + Pure Fallback) ---
    private fun chachaDecrypt(key: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        // Try platform cipher first (Java 11+ / Android API 28+)
        try {
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            val spec = IvParameterSpec(nonce)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), spec)
            return cipher.doFinal(ciphertextWithTag)
        } catch (_: GeneralSecurityException) {
            // Fall back to pure Kotlin RFC 8439 implementation
        } catch (_: NoSuchMethodError) {
        } catch (_: Exception) {
        }
        return ChaCha20Poly1305Pure.decrypt(key, nonce, ciphertextWithTag)
    }

    // --- Helpers ---
    private fun getPrivateKey(pkcs8Base64: String): PrivateKey {
        return rsaKeyCache.computeIfAbsent(pkcs8Base64) {
            val der = Base64.getDecoder().decode(it)
            val kf = KeyFactory.getInstance("RSA")
            kf.generatePrivate(PKCS8EncodedKeySpec(der))
        }
    }

    fun swapPairs(b: ByteArray): ByteArray {
        val r = b.clone()
        var i = 0
        while (i + 1 < r.size) {
            val tmp = r[i]
            r[i] = r[i + 1]
            r[i + 1] = tmp
            i += 2
        }
        return r
    }

    fun blockPairSwap(b: ByteArray): ByteArray {
        val r = b.clone()
        val full = r.size - (r.size % 4)
        var i = 0
        while (i < full) {
            val t0 = r[i]
            val t1 = r[i + 1]
            r[i] = r[i + 2]
            r[i + 1] = r[i + 3]
            r[i + 2] = t0
            r[i + 3] = t1
            i += 4
        }
        return r
    }

    fun b64Dec(input: ByteArray): ByteArray {
        val clean = ByteArray(input.size + 4)
        var cleanLen = 0
        for (b in input) {
            val c = b.toInt().toChar()
            when (c) {
                ' ', '\n', '\r', '\t' -> continue
                '-' -> clean[cleanLen++] = '+'.code.toByte()
                '_' -> clean[cleanLen++] = '/'.code.toByte()
                else -> clean[cleanLen++] = b
            }
        }
        val rem = cleanLen % 4
        if (rem != 0) {
            val pad = 4 - rem
            for (p in 0 until pad) {
                clean[cleanLen++] = '='.code.toByte()
            }
        }
        return Base64.getDecoder().decode(clean.copyOf(cleanLen))
    }

    // --- RFC 8439 Pure Kotlin ChaCha20-Poly1305 Fallback ---
    private object ChaCha20Poly1305Pure {
        private fun rotl(v: Int, c: Int): Int = (v shl c) or (v ushr (32 - c))

        private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
            x[a] += x[b]; x[d] = rotl(x[d] xor x[a], 16)
            x[c] += x[d]; x[b] = rotl(x[b] xor x[c], 12)
            x[a] += x[b]; x[d] = rotl(x[d] xor x[a], 8)
            x[c] += x[d]; x[b] = rotl(x[b] xor x[c], 7)
        }

        private fun chacha20Block(key: ByteArray, counter: Int, nonce: ByteArray): IntArray {
            val state = IntArray(16)
            state[0] = 0x61707865
            state[1] = 0x3320646e
            state[2] = 0x79622d32
            state[3] = 0x6b206574

            val keyBuf = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until 8) {
                state[4 + i] = keyBuf.int
            }
            state[12] = counter
            val nonceBuf = ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until 3) {
                state[13 + i] = nonceBuf.int
            }

            val working = state.clone()
            for (i in 0 until 10) {
                quarterRound(working, 0, 4, 8, 12)
                quarterRound(working, 1, 5, 9, 13)
                quarterRound(working, 2, 6, 10, 14)
                quarterRound(working, 3, 7, 11, 15)
                quarterRound(working, 0, 5, 10, 15)
                quarterRound(working, 1, 6, 11, 12)
                quarterRound(working, 2, 7, 8, 13)
                quarterRound(working, 3, 4, 9, 14)
            }
            for (i in 0 until 16) {
                working[i] += state[i]
            }
            return working
        }

        fun chacha20Xor(key: ByteArray, counter: Int, nonce: ByteArray, input: ByteArray): ByteArray {
            val out = ByteArray(input.size)
            var currCounter = counter
            var inOff = 0
            while (inOff < input.size) {
                val blockInts = chacha20Block(key, currCounter, nonce)
                val blockBytes = ByteArray(64)
                val buf = ByteBuffer.wrap(blockBytes).order(ByteOrder.LITTLE_ENDIAN)
                for (v in blockInts) buf.putInt(v)

                val toXor = minOf(64, input.size - inOff)
                for (i in 0 until toXor) {
                    out[inOff + i] = (input[inOff + i].toInt() xor blockBytes[i].toInt()).toByte()
                }
                inOff += toXor
                currCounter++
            }
            return out
        }

        private fun poly1305Mac(key: ByteArray, message: ByteArray): ByteArray {
            val rBytes = ByteArray(16)
            System.arraycopy(key, 0, rBytes, 0, 16)
            rBytes[3] = (rBytes[3].toInt() and 15).toByte()
            rBytes[7] = (rBytes[7].toInt() and 15).toByte()
            rBytes[11] = (rBytes[11].toInt() and 15).toByte()
            rBytes[15] = (rBytes[15].toInt() and 15).toByte()
            rBytes[4] = (rBytes[4].toInt() and 252).toByte()
            rBytes[8] = (rBytes[8].toInt() and 252).toByte()
            rBytes[12] = (rBytes[12].toInt() and 252).toByte()

            val rRev = rBytes.reversedArray()
            val r = BigInteger(1, rRev)

            val sBytes = ByteArray(16)
            System.arraycopy(key, 16, sBytes, 0, 16)
            val sRev = sBytes.reversedArray()
            val s = BigInteger(1, sRev)

            val p = BigInteger.valueOf(2).pow(130).subtract(BigInteger.valueOf(5))
            var a = BigInteger.ZERO

            var off = 0
            while (off < message.size) {
                val len = minOf(16, message.size - off)
                val chunk = ByteArray(len + 1)
                System.arraycopy(message, off, chunk, 0, len)
                chunk[len] = 1.toByte()
                val chunkRev = chunk.reversedArray()
                val n = BigInteger(1, chunkRev)
                a = (a.add(n)).multiply(r).mod(p)
                off += len
            }

            a = a.add(s).mod(BigInteger.valueOf(2).pow(128))
            val aBytes = a.toByteArray()
            val result = ByteArray(16)
            var srcIdx = aBytes.size - 1
            var dstIdx = 0
            while (dstIdx < 16 && srcIdx >= 0) {
                result[dstIdx++] = aBytes[srcIdx--]
            }
            return result
        }

        fun decrypt(key: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
            if (ciphertextWithTag.size < 16) throw IllegalArgumentException("Ciphertext too short")
            val ctLen = ciphertextWithTag.size - 16
            val ct = ciphertextWithTag.copyOfRange(0, ctLen)
            val tag = ciphertextWithTag.copyOfRange(ctLen, ciphertextWithTag.size)

            val polyKeyBlock = chacha20Block(key, 0, nonce)
            val polyKey = ByteArray(32)
            val buf = ByteBuffer.wrap(polyKey).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until 8) buf.putInt(polyKeyBlock[i])

            val macData = java.io.ByteArrayOutputStream()
            macData.write(ct)
            if (ct.size % 16 != 0) macData.write(ByteArray(16 - (ct.size % 16)))
            val lenBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            lenBuf.putLong(0L) // AAD len = 0
            lenBuf.putLong(ct.size.toLong())
            macData.write(lenBuf.array())

            val computedTag = poly1305Mac(polyKey, macData.toByteArray())
            if (!Arrays.equals(tag, computedTag)) {
                throw GeneralSecurityException("Poly1305 tag mismatch")
            }

            return chacha20Xor(key, 1, nonce, ct)
        }
    }
}
