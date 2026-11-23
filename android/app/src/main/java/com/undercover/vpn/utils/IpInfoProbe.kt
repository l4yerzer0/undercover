package com.undercover.vpn.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.TimeZone

data class ExitNodeInfo(
    val ip: String,
    val country: String,
    val countryCode: String,
    val city: String,
    val timezone: String,
    val locale: String,
)

object IpInfoProbe {
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    /**
     * Determines the local network IPv4 address of the physical interface (Wi-Fi or Mobile),
     * ignoring loopback, VPN/tun, dummy, or p2p interfaces.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("p2p") || name.startsWith("dummy")) continue
                val addresses = Collections.list(intf.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.isNotEmpty() && !ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "192.168.1.1"
    }

    /**
     * Formats current device system timezone, e.g. "UTC+3 (Moscow)" or "UTC-5 (New York)".
     */
    fun getDeviceTimeZone(): String {
        return try {
            val tz = TimeZone.getDefault()
            val offsetHours = tz.rawOffset / (1000 * 60 * 60)
            val sign = if (offsetHours >= 0) "+" else ""
            val city = tz.id.substringAfterLast('/').replace('_', ' ')
            "UTC$sign$offsetHours ($city)"
        } catch (_: Exception) {
            "UTC+0"
        }
    }

    /**
     * Formats current device locale, e.g. "ru-RU" or "en-US".
     */
    fun getDeviceLocale(): String {
        return try {
            val tag = Locale.getDefault().toLanguageTag()
            if (tag.isNotEmpty()) tag else "en-US"
        } catch (_: Exception) {
            "en-US"
        }
    }

    /**
     * Fetches public exit node IP, city, country, and timezone.
     * When [proxyPort] is provided, routes the probe through the local sing-box loopback
     * mixed inbound (`127.0.0.1:[proxyPort]`) so the query exits through the active VPN
     * tunnel even when our own app is excluded from TUN.
     */
    suspend fun fetchExitNodeInfo(proxyPort: Int? = null): ExitNodeInfo? = withContext(Dispatchers.IO) {
        val proxy = proxyPort?.let {
            Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", it))
        }
        fetchFromIpWhoIs(proxy) ?: fetchFromIpApiCo(proxy) ?: fetchFromIpify(proxy)
    }

    private fun openConnection(url: URL, proxy: Proxy?): HttpURLConnection {
        val raw = if (proxy != null) url.openConnection(proxy) else url.openConnection()
        return (raw as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "Undercover/0.5")
            useCaches = false
        }
    }

    private fun fetchFromIpWhoIs(proxy: Proxy?): ExitNodeInfo? {
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(URL("https://ipwho.is/"), proxy)
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.optBoolean("success", true)) {
                    val ip = json.optString("ip")
                    if (ip.isNotEmpty()) {
                        val country = json.optString("country")
                        val countryCode = json.optString("country_code")
                        val city = json.optString("city")
                        val tzObj = json.optJSONObject("timezone")
                        val tzId = tzObj?.optString("id") ?: ""
                        val tzUtc = tzObj?.optString("utc")
                        val formattedTz = formatTimeZone(tzId, tzUtc)
                        return ExitNodeInfo(
                            ip = ip,
                            country = country,
                            countryCode = countryCode,
                            city = city,
                            timezone = formattedTz,
                            locale = getLocaleForCountry(countryCode),
                        )
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }
        return null
    }

    private fun fetchFromIpApiCo(proxy: Proxy?): ExitNodeInfo? {
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(URL("https://ipapi.co/json/"), proxy)
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val ip = json.optString("ip")
                if (ip.isNotEmpty()) {
                    val country = json.optString("country_name")
                    val countryCode = json.optString("country_code")
                    val city = json.optString("city")
                    val tzId = json.optString("timezone")
                    val tzUtc = json.optString("utc_offset")
                    val formattedTz = formatTimeZone(tzId, tzUtc)
                    return ExitNodeInfo(
                        ip = ip,
                        country = country,
                        countryCode = countryCode,
                        city = city,
                        timezone = formattedTz,
                        locale = getLocaleForCountry(countryCode),
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }
        return null
    }

    private fun fetchFromIpify(proxy: Proxy?): ExitNodeInfo? {
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(URL("https://api.ipify.org?format=json"), proxy)
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val ip = json.optString("ip")
                if (ip.isNotEmpty()) {
                    return ExitNodeInfo(
                        ip = ip,
                        country = "Protected",
                        countryCode = "VPN",
                        city = "In-Tunnel",
                        timezone = "UTC+00",
                        locale = "en-US",
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }
        return null
    }

    private fun formatTimeZone(tzId: String, rawUtc: String?): String {
        if (tzId.isNotEmpty()) {
            try {
                val tz = TimeZone.getTimeZone(tzId)
                val offsetHours = tz.rawOffset / (1000 * 60 * 60)
                val sign = if (offsetHours >= 0) "+" else ""
                val city = tzId.substringAfterLast('/').replace('_', ' ')
                return "UTC$sign$offsetHours ($city)"
            } catch (_: Exception) {
            }
        }
        if (!rawUtc.isNullOrEmpty()) {
            val cleanUtc = if (rawUtc.startsWith("+") || rawUtc.startsWith("-")) "UTC$rawUtc" else rawUtc
            return cleanUtc
        }
        return "UTC+00"
    }

    private fun getLocaleForCountry(countryCode: String): String {
        val code = countryCode.uppercase()
        return when (code) {
            "US" -> "en-US"
            "GB", "UK" -> "en-GB"
            "CA" -> "en-CA"
            "AU" -> "en-AU"
            "RU" -> "ru-RU"
            "BY" -> "be-BY"
            "UA" -> "uk-UA"
            "KZ" -> "kk-KZ"
            "DE" -> "de-DE"
            "FR" -> "fr-FR"
            "NL" -> "nl-NL"
            "ES" -> "es-ES"
            "IT" -> "it-IT"
            "PL" -> "pl-PL"
            "JP" -> "ja-JP"
            "KR" -> "ko-KR"
            "CN" -> "zh-CN"
            "TR" -> "tr-TR"
            "SE" -> "sv-SE"
            "FI" -> "fi-FI"
            "NO" -> "nb-NO"
            "CH" -> "de-CH"
            "AT" -> "de-AT"
            "IS" -> "is-IS"
            "SG" -> "en-SG"
            "HK" -> "zh-HK"
            else -> if (code.length == 2) "${code.lowercase()}-$code" else "en-US"
        }
    }
}
