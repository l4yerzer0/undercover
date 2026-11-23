package com.undercover.vpn.shared.parser

import java.util.Base64

/**
 * Normalizes the many shapes a subscription endpoint can return into a sing-box JSON configuration.
 *
 * Providers typically answer in one of three ways:
 *  - a sing-box / JSON configuration object,
 *  - a base64 blob (sometimes line-wrapped) that decodes to JSON or to a newline-separated proxy URI list,
 *  - a plain newline-separated proxy URI list.
 *
 * Only the first shape used to be accepted, which made subscriptions with base64/URI-list payloads
 * import as an empty profile. This parser accepts all of them.
 */
object SubscriptionContentParser {

    private const val MAX_DECODE_DEPTH = 3

    fun toSingBoxConfig(rawContent: String): String? = normalize(rawContent, 0)

    private fun normalize(input: String, depth: Int): String? {
        if (depth > MAX_DECODE_DEPTH) return null

        val text = input.trim().removePrefix("\uFEFF").trim()
        if (text.isEmpty()) return null

        // A sing-box configuration object is returned as-is; the caller migrates/validates it.
        if (text.startsWith("{")) return text
        // A bare JSON array is not a valid sing-box root configuration.
        if (text.startsWith("[")) return null

        // base64 payload: may decode to a JSON config or to a URI list.
        decodeBase64Lenient(text)?.let { decoded ->
            val decodedTrimmed = decoded.trim()
            if (decodedTrimmed.isNotEmpty() && decodedTrimmed != text &&
                (decodedTrimmed.startsWith("{") || decodedTrimmed.contains("://"))
            ) {
                normalize(decodedTrimmed, depth + 1)?.let { return it }
            }
        }

        val uris = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && ConfigParser.isSupportedUri(it) }

        if (uris.isNotEmpty()) {
            return ConfigParser.parseUriListToSingBoxJson(uris)
        }

        return null
    }

    private fun decodeBase64Lenient(value: String): String? {
        val cleaned = value
            .filterNot { it.isWhitespace() }
            .replace('-', '+')
            .replace('_', '/')
        if (cleaned.isEmpty()) return null

        val padded = when (cleaned.length % 4) {
            1 -> return null
            2 -> "$cleaned=="
            3 -> "$cleaned="
            else -> cleaned
        }

        return try {
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
