package com.undercover.vpn.shared.parser

import com.undercover.vpn.shared.parser.happ.HappDecoder
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Base64

data class SubscriptionMetadata(
    val title: String? = null,
    val updateIntervalHours: Int? = null,
    val brandName: String? = null,
    val userinfo: String? = null,
)

data class FetchSubscriptionResult(
    val metadata: SubscriptionMetadata,
    val content: String,
)

object SubscriptionMetadataFetcher {

    fun parseMetadata(
        headers: Map<String, List<String>>,
        url: String? = null,
        bodyContent: String? = null,
    ): SubscriptionMetadata {
        fun getHeader(name: String): String? {
            return headers.entries
                .firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value?.firstOrNull()?.trim()
        }

        // 1. Profile title header
        var title: String? = null
        val profileTitleHeader = getHeader("profile-title")
        if (!profileTitleHeader.isNullOrBlank()) {
            title = decodeProfileTitle(profileTitleHeader)
        }

        // 2. Content-Disposition filename
        if (title.isNullOrBlank()) {
            val contentDisp = getHeader("content-disposition")
            if (!contentDisp.isNullOrBlank()) {
                title = extractFilenameFromContentDisposition(contentDisp)
            }
        }

        // 3. x-4net-brand-name
        val brand = getHeader("x-4net-brand-name")
        if (title.isNullOrBlank() && !brand.isNullOrBlank()) {
            title = brand.trim()
        }

        // 4. Fallback to selector tag in body JSON
        if (title.isNullOrBlank() && !bodyContent.isNullOrBlank()) {
            title = extractSelectorTag(bodyContent)
        }

        // 5. Fallback to URL fragment
        if (title.isNullOrBlank() && url != null && url.contains("#")) {
            val fragment = url.substringAfter("#").trim()
            if (fragment.isNotEmpty()) {
                title = try {
                    URLDecoder.decode(fragment, "UTF-8").trim()
                } catch (_: Exception) {
                    fragment
                }
            }
        }

        // 6. Fallback to domain/path from URL
        if (title.isNullOrBlank() && url != null) {
            title = extractFallbackFromUrl(url)
        }

        // Interval (headers typically provide interval in hours)
        val intervalHeader = getHeader("profile-update-interval")
        val intervalHours = intervalHeader?.toIntOrNull()

        val userinfo = getHeader("subscription-userinfo")

        return SubscriptionMetadata(
            title = title?.takeIf { it.isNotBlank() },
            updateIntervalHours = intervalHours,
            brandName = brand,
            userinfo = userinfo,
        )
    }

    fun decodeProfileTitle(raw: String): String {
        val trimmed = raw.trim()
        return try {
            when {
                trimmed.startsWith("base64:", ignoreCase = true) -> {
                    val b64 = trimmed.substring(7).trim()
                    val clean = b64.replace('-', '+').replace('_', '/').let {
                        when (it.length % 4) {
                            2 -> "$it=="
                            3 -> "$it="
                            else -> it
                        }
                    }
                    val bytes = Base64.getDecoder().decode(clean)
                    String(bytes, Charsets.UTF_8).trim()
                }
                trimmed.startsWith("utf-8''", ignoreCase = true) -> {
                    val encoded = trimmed.substring(7).trim()
                    URLDecoder.decode(encoded, "UTF-8").trim()
                }
                trimmed.contains("%") -> {
                    try {
                        URLDecoder.decode(trimmed, "UTF-8").trim()
                    } catch (_: Exception) {
                        trimmed
                    }
                }
                else -> trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    fun extractFilenameFromContentDisposition(header: String): String? {
        val starMatch = Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE).find(header)
        if (starMatch != null) {
            return try {
                URLDecoder.decode(starMatch.groupValues[1].trim('"', '\''), "UTF-8")
            } catch (_: Exception) {
                starMatch.groupValues[1].trim('"', '\'')
            }
        }
        val quoteMatch = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(header)
        if (quoteMatch != null) {
            return quoteMatch.groupValues[1]
        }
        val simpleMatch = Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(header)
        if (simpleMatch != null) {
            return simpleMatch.groupValues[1].trim('"', '\'')
        }
        return null
    }

    private fun extractSelectorTag(content: String): String? {
        val regex = Regex(""""type"\s*:\s*"selector"[^}]*?"tag"\s*:\s*"([^"]+)"""")
        val match = regex.find(content) ?: Regex(""""tag"\s*:\s*"([^"]+)"[^}]*?"type"\s*:\s*"selector"""").find(content)
        return match?.groupValues?.get(1)?.replace("→", "")?.replace("⚡", "")?.trim()
    }

    private fun extractFallbackFromUrl(urlStr: String): String {
        return try {
            val url = URL(urlStr)
            val pathSegment = url.path.trim('/').substringAfterLast('/')
            if (pathSegment.isNotEmpty() && pathSegment.length > 2) {
                pathSegment
            } else {
                url.host.removePrefix("www.")
            }
        } catch (_: Exception) {
            "Subscription"
        }
    }

    fun fetch(urlStr: String, userAgent: String): FetchSubscriptionResult {
        var resolvedUrl = urlStr.trim()
        if (HappDecoder.isHappOrV2Link(resolvedUrl)) {
            val decrypted = HappDecoder.decrypt(resolvedUrl)
            if (decrypted != null) {
                resolvedUrl = decrypted
            }
        }
        val url = URL(resolvedUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode: ${conn.responseMessage}")
        }

        val headers = conn.headerFields
        val rawBody = conn.inputStream.bufferedReader().use { it.readText() }
        val metadata = parseMetadata(headers, resolvedUrl, rawBody)

        return FetchSubscriptionResult(metadata, rawBody)
    }
}
