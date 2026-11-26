package com.undercover.vpn.byedpi

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import kotlin.random.Random

/**
 * Diagnostic probe for ByeDPI anti-censorship strategies.
 *
 * Simulates real-world 2026 TLS traffic (Chrome 124+ with post-quantum X25519MLKEM768
 * ~1.8 KB ClientHello) and tests against behavioural DPI throttling/freezing (16-20 KB stall).
 */
object ByeDpiProbe {
    private const val TAG = "ByeDpiProbe"

    // First ever video on YouTube ("Me at the zoo") thumbnail, ~28-32 KB.
    // Perfect for checking whether TSPU behavioral freeze (stalling at 16-20 KB) triggers.
    const val YOUTUBE_PROBE_IMAGE = "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg"

    const val YOUTUBE_HOST = "www.youtube.com"
    const val DISCORD_GATEWAY_HOST = "gateway.discord.gg"

    data class ProbeResult(
        val ytClientHello: Boolean = false,
        val ytMediaDownload: Boolean = false,
        val discordClientHello: Boolean = false,
        val elapsedMs: Long = 0L,
    ) {
        val ytPassed: Boolean get() = ytClientHello && ytMediaDownload
        val discordPassed: Boolean get() = discordClientHello
        val allPassed: Boolean get() = ytPassed && discordPassed

        val score: Int get() {
            var s = 0
            if (ytClientHello) s += 30
            if (ytMediaDownload) s += 40
            if (discordClientHello) s += 30
            return s
        }
    }

    /**
     * Runs a multi-stage check of a running ByeDPI proxy instance on [proxyPort].
     *
     * @param quickCheck If true, runs only the ClientHello round (for fast filtering).
     */
    suspend fun probe(
        proxyPort: Int,
        auth: ByeDpiManager.ByeDpiAuth? = ByeDpiManager.currentAuth,
        quickCheck: Boolean = false,
    ): ProbeResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()

        // 1. YouTube ClientHello probe (Realistic TLS 1.3 ~1.8 KB with ML-KEM)
        val ytHello = testRealisticTlsClientHello(
            proxyPort = proxyPort,
            targetHost = YOUTUBE_HOST,
            timeoutMs = 2500,
            auth = auth,
        )

        // If even ClientHello fails for YouTube, TSPU is dropping the strategy outright.
        if (!ytHello && quickCheck) {
            return@withContext ProbeResult(
                ytClientHello = false,
                ytMediaDownload = false,
                discordClientHello = false,
                elapsedMs = System.currentTimeMillis() - start,
            )
        }

        // 2. Discord ClientHello probe
        val discordHello = testRealisticTlsClientHello(
            proxyPort = proxyPort,
            targetHost = DISCORD_GATEWAY_HOST,
            timeoutMs = 2000,
            auth = auth,
        )

        if (quickCheck) {
            return@withContext ProbeResult(
                ytClientHello = ytHello,
                ytMediaDownload = false,
                discordClientHello = discordHello,
                elapsedMs = System.currentTimeMillis() - start,
            )
        }

        // 3. YouTube Media Freeze probe: download > 20 KB thumbnail through SOCKS5.
        // TSPU allows the TLS handshake through on flawed split strategies, then freezes TCP stream after 16-20 KB.
        val ytMedia = if (ytHello) {
            testDownloadMedia(
                proxyPort = proxyPort,
                urlStr = YOUTUBE_PROBE_IMAGE,
                minBytes = 20_000,
                timeoutMs = 4000,
                auth = auth,
            )
        } else {
            false
        }

        ProbeResult(
            ytClientHello = ytHello,
            ytMediaDownload = ytMedia,
            discordClientHello = discordHello,
            elapsedMs = System.currentTimeMillis() - start,
        )
    }

    /**
     * Establishes a SOCKS5 TCP tunnel to [targetHost]:[targetPort] and writes a
     * full-sized (~1.8 KB) Chrome 124+ TLS 1.3 ClientHello containing the post-quantum
     * X25519MLKEM768 key share.
     *
     * Returns true if a valid TLS ServerHello (record type 0x16, handshake 0x02) is received.
     */
    fun testRealisticTlsClientHello(
        proxyPort: Int,
        targetHost: String,
        targetPort: Int = 443,
        timeoutMs: Int = 2500,
        auth: ByeDpiManager.ByeDpiAuth? = ByeDpiManager.currentAuth,
    ): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress("127.0.0.1", proxyPort), 1500)

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. SOCKS5 greeting
            if (auth != null) {
                output.write(byteArrayOf(0x05, 0x01, 0x02))
                output.flush()

                val greetingResp = ByteArray(2)
                readExact(input, greetingResp)
                if (greetingResp[0] != 0x05.toByte() || greetingResp[1] != 0x02.toByte()) {
                    Log.w(TAG, "SOCKS5 auth greeting failed on port $proxyPort: method ${greetingResp[1]}")
                    return false
                }

                val uBytes = auth.username.toByteArray(Charsets.US_ASCII)
                val pBytes = auth.password.toByteArray(Charsets.US_ASCII)
                val authReq = ByteArrayOutputStream()
                authReq.write(0x01)
                authReq.write(uBytes.size)
                authReq.write(uBytes)
                authReq.write(pBytes.size)
                authReq.write(pBytes)
                output.write(authReq.toByteArray())
                output.flush()

                val authResp = ByteArray(2)
                readExact(input, authResp)
                if (authResp[0] != 0x01.toByte() || authResp[1] != 0x00.toByte()) {
                    Log.w(TAG, "SOCKS5 auth failed on port $proxyPort: status ${authResp[1]}")
                    return false
                }
            } else {
                output.write(byteArrayOf(0x05, 0x01, 0x00))
                output.flush()

                val greetingResp = ByteArray(2)
                readExact(input, greetingResp)
                if (greetingResp[0] != 0x05.toByte() || greetingResp[1] != 0x00.toByte()) {
                    Log.w(TAG, "SOCKS5 handshake failed on port $proxyPort")
                    return false
                }
            }

            // 2. SOCKS5 connect command: DOMAIN
            val hostBytes = targetHost.toByteArray(Charsets.US_ASCII)
            val connectReq = ByteArrayOutputStream()
            connectReq.write(byteArrayOf(0x05, 0x01, 0x00, 0x03))
            connectReq.write(hostBytes.size)
            connectReq.write(hostBytes)
            connectReq.write((targetPort shr 8) and 0xff)
            connectReq.write(targetPort and 0xff)
            output.write(connectReq.toByteArray())
            output.flush()

            // Read connect response: 0x05 0x00 ...
            val connHeader = ByteArray(4)
            readExact(input, connHeader)
            if (connHeader[0] != 0x05.toByte() || connHeader[1] != 0x00.toByte()) {
                Log.d(TAG, "SOCKS5 connect to $targetHost returned status ${connHeader[1]}")
                return false
            }
            // Skip bound address
            when (connHeader[3].toInt()) {
                0x01 -> readExact(input, ByteArray(4 + 2)) // IPv4 + port
                0x04 -> readExact(input, ByteArray(16 + 2)) // IPv6 + port
                0x03 -> {
                    val len = input.read()
                    if (len > 0) readExact(input, ByteArray(len + 2))
                }
            }

            // 3. Send Chrome-style Post-Quantum ClientHello
            val clientHello = buildChromeClientHello(targetHost)
            output.write(clientHello)
            output.flush()

            // 4. Read TLS Record Header: 5 bytes
            // [0] = 0x16 (Handshake), [1..2] = 0x03 0x03 (TLS 1.2 record layer), [3..4] = length
            val recordHeader = ByteArray(5)
            readExact(input, recordHeader)

            if (recordHeader[0] != 0x16.toByte()) {
                Log.d(TAG, "Non-TLS record received from $targetHost: ${recordHeader[0]}")
                return false
            }

            // Read handshake type (1 byte): 0x02 = ServerHello
            val hsType = input.read()
            hsType == 0x02
        } catch (e: Exception) {
            Log.d(TAG, "TLS ClientHello probe failed for $targetHost: ${e.message}")
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    /**
     * Downloads an asset through SOCKS5 proxy and verifies that stream does not get frozen mid-way.
     */
    fun testDownloadMedia(
        proxyPort: Int,
        urlStr: String,
        minBytes: Long = 20_000,
        timeoutMs: Int = 4000,
        auth: ByeDpiManager.ByeDpiAuth? = ByeDpiManager.currentAuth,
    ): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            if (auth != null) {
                ByeDpiManager.ensureAuthenticator()
            }
            val url = URL(urlStr)
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort))
            connection = (url.openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                setRequestProperty("Connection", "close")
            }

            val code = connection.responseCode
            if (code !in 200..399) {
                Log.d(TAG, "Media download probe returned HTTP $code")
                return false
            }

            val stream = connection.inputStream
            val buf = ByteArray(4096)
            var totalRead = 0L

            while (totalRead < minBytes) {
                val r = stream.read(buf)
                if (r == -1) break
                totalRead += r
            }

            totalRead >= minBytes
        } catch (e: Exception) {
            Log.d(TAG, "Media download probe error: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun readExact(stream: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = stream.read(target, offset, target.size - offset)
            if (read == -1) throw java.io.EOFException("Premature EOF reading ${target.size} bytes (got $offset)")
            offset += read
        }
    }

    /**
     * Synthesizes a realistic Chrome 124+ TLS 1.3 ClientHello (~1.8 KB).
     *
     * Crucial features:
     * - SNI extension matching targetHost
     * - ALPN: h2, http/1.1
     * - Supported Versions: TLS 1.3, TLS 1.2
     * - Supported Groups: 0x11ec (X25519MLKEM768), 0x001d (x25519), 0x0017 (secp256r1)
     * - KeyShare with 1184-byte ML-KEM public key + 32-byte X25519 public key.
     *   This naturally blows up the packet size to 1.8 KB+, forcing it across 2 TCP segments
     *   unless fragmented or manipulated by ByeDPI.
     */
    fun buildChromeClientHello(sniHost: String): ByteArray {
        val rand = SecureRandom()
        val randomBytes = ByteArray(32).also { rand.nextBytes(it) }
        val sessionId = ByteArray(32).also { rand.nextBytes(it) }

        // --- Build Extensions ---
        val exts = ByteArrayOutputStream()

        // 1. SNI (0x0000)
        run {
            val sniBytes = sniHost.toByteArray(Charsets.US_ASCII)
            val sniPayload = ByteArrayOutputStream()
            // ServerNameList length
            val listLen = sniBytes.size + 3
            sniPayload.write((listLen shr 8) and 0xff)
            sniPayload.write(listLen and 0xff)
            sniPayload.write(0x00) // NameType: host_name
            sniPayload.write((sniBytes.size shr 8) and 0xff)
            sniPayload.write(sniBytes.size and 0xff)
            sniPayload.write(sniBytes)
            writeExtension(exts, 0x0000, sniPayload.toByteArray())
        }

        // 2. Extended Master Secret (0x0017)
        writeExtension(exts, 0x0017, byteArrayOf())

        // 3. Renegotiation Info (0xff01)
        writeExtension(exts, 0xff01, byteArrayOf(0x00))

        // 4. Supported Groups (0x000a): X25519MLKEM768 (0x11ec), X25519 (0x001d), secp256r1 (0x0017), secp384r1 (0x0018)
        run {
            val groups = byteArrayOf(
                0x00, 0x08, // list length: 8
                0x11.toByte(), 0xec.toByte(), // x25519_mlkem768
                0x00, 0x1d, // x25519
                0x00, 0x17, // secp256r1
                0x00, 0x18, // secp384r1
            )
            writeExtension(exts, 0x000a, groups)
        }

        // 5. EC Point Formats (0x000b)
        writeExtension(exts, 0x000b, byteArrayOf(0x01, 0x00))

        // 6. SessionTicket (0x0023)
        writeExtension(exts, 0x0023, byteArrayOf())

        // 7. ALPN (0x0010): h2, http/1.1
        run {
            val alpn = byteArrayOf(
                0x00, 0x0c, // list len 12
                0x02, 'h'.code.toByte(), '2'.code.toByte(),
                0x08, 'h'.code.toByte(), 't'.code.toByte(), 't'.code.toByte(), 'p'.code.toByte(),
                '/'.code.toByte(), '1'.code.toByte(), '.'.code.toByte(), '1'.code.toByte(),
            )
            writeExtension(exts, 0x0010, alpn)
        }

        // 8. Supported Versions (0x002b): TLS 1.3 (0x0304), TLS 1.2 (0x0303)
        run {
            val versions = byteArrayOf(0x04, 0x03, 0x04, 0x03, 0x03)
            writeExtension(exts, 0x002b, versions)
        }

        // 9. Key Share (0x0033):
        // Group 0x11ec: length 1216 (1184 ML-KEM + 32 X25519)
        // Group 0x001d: length 32
        run {
            val keySharePayload = ByteArrayOutputStream()
            val totalSharesLen = (4 + 1216) + (4 + 32)
            keySharePayload.write((totalSharesLen shr 8) and 0xff)
            keySharePayload.write(totalSharesLen and 0xff)

            // Entry 1: x25519_mlkem768 (0x11ec)
            keySharePayload.write(0x11)
            keySharePayload.write(0xec)
            val mlkemShare = ByteArray(1216).also { rand.nextBytes(it) }
            keySharePayload.write((mlkemShare.size shr 8) and 0xff)
            keySharePayload.write(mlkemShare.size and 0xff)
            keySharePayload.write(mlkemShare)

            // Entry 2: x25519 (0x001d)
            keySharePayload.write(0x00)
            keySharePayload.write(0x1d)
            val x25519Share = ByteArray(32).also { rand.nextBytes(it) }
            keySharePayload.write((x25519Share.size shr 8) and 0xff)
            keySharePayload.write(x25519Share.size and 0xff)
            keySharePayload.write(x25519Share)

            writeExtension(exts, 0x0033, keySharePayload.toByteArray())
        }

        // 10. Signature Algorithms (0x000d)
        run {
            val sigAlgs = byteArrayOf(
                0x00, 0x10, // length 16
                0x04, 0x03, // ecdsa_secp256r1_sha256
                0x08, 0x04, // rsa_pss_rsae_sha256
                0x04, 0x01, // rsa_pkcs1_sha256
                0x05, 0x03, // ecdsa_secp384r1_sha384
                0x08, 0x05, // rsa_pss_rsae_sha384
                0x05, 0x01, // rsa_pkcs1_sha384
                0x08, 0x06, // rsa_pss_rsae_sha512
                0x02, 0x01, // rsa_pkcs1_sha1
            )
            writeExtension(exts, 0x000d, sigAlgs)
        }

        val extensionsBytes = exts.toByteArray()

        // --- Cipher Suites (standard Chrome suites) ---
        val ciphers = byteArrayOf(
            0x00, 0x12, // 18 bytes (9 suites)
            0x13, 0x01, // TLS_AES_128_GCM_SHA256
            0x13, 0x02, // TLS_AES_256_GCM_SHA384
            0x13, 0x03, // TLS_CHACHA20_POLY1305_SHA256
            0xc0.toByte(), 0x2b.toByte(), // ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
            0xc0.toByte(), 0x2f.toByte(), // ECDHE_RSA_WITH_AES_128_GCM_SHA256
            0xc0.toByte(), 0x2c.toByte(), // ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
            0xc0.toByte(), 0x30.toByte(), // ECDHE_RSA_WITH_AES_256_GCM_SHA384
            0xcc.toByte(), 0xa9.toByte(), // ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256
            0xcc.toByte(), 0xa8.toByte(), // ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
        )

        // --- Handshake ClientHello Body ---
        val hsBody = ByteArrayOutputStream()
        hsBody.write(0x03)
        hsBody.write(0x03) // TLS 1.2 legacy version
        hsBody.write(randomBytes) // 32 bytes
        hsBody.write(sessionId.size)
        hsBody.write(sessionId)
        hsBody.write(ciphers)
        hsBody.write(0x01) // compression methods length: 1
        hsBody.write(0x00) // null compression
        hsBody.write((extensionsBytes.size shr 8) and 0xff)
        hsBody.write(extensionsBytes.size and 0xff)
        hsBody.write(extensionsBytes)

        val hsBodyBytes = hsBody.toByteArray()

        // --- Handshake Message (Type 0x01, 3-byte len) ---
        val hsMsg = ByteArrayOutputStream()
        hsMsg.write(0x01) // ClientHello
        hsMsg.write((hsBodyBytes.size shr 16) and 0xff)
        hsMsg.write((hsBodyBytes.size shr 8) and 0xff)
        hsMsg.write(hsBodyBytes.size and 0xff)
        hsMsg.write(hsBodyBytes)
        val hsMsgBytes = hsMsg.toByteArray()

        // --- TLS Record Header (Type 0x16, TLS 1.0 0x0301, 2-byte len) ---
        val record = ByteArrayOutputStream()
        record.write(0x16) // Handshake
        record.write(0x03)
        record.write(0x01) // TLS 1.0 legacy record version
        record.write((hsMsgBytes.size shr 8) and 0xff)
        record.write(hsMsgBytes.size and 0xff)
        record.write(hsMsgBytes)

        return record.toByteArray()
    }

    private fun writeExtension(out: ByteArrayOutputStream, type: Int, payload: ByteArray) {
        out.write((type shr 8) and 0xff)
        out.write(type and 0xff)
        out.write((payload.size shr 8) and 0xff)
        out.write(payload.size and 0xff)
        out.write(payload)
    }
}
