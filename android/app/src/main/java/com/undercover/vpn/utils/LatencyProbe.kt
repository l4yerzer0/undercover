package com.undercover.vpn.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * Measures round-trip latency through the active VPN tunnel.
 *
 * When [proxyPort] is supplied, routes the probe via sing-box's loopback mixed inbound
 * (`127.0.0.1:[proxyPort]`) so the measurement traverses sing-box → active outbound → target
 * even when this app's own UID is excluded from TUN for child cores like xray-core.
 */
object LatencyProbe {
    private const val PROBE_URL = "https://cp.cloudflare.com/generate_204"
    private const val TIMEOUT_MS = 5_000

    /** Round trip in milliseconds, or null if the probe failed. */
    suspend fun measure(proxyPort: Int? = null): Long? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val proxy = proxyPort?.let {
                Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", it))
            }
            val startedAt = System.nanoTime()
            val url = URL(PROBE_URL)
            val raw = if (proxy != null) url.openConnection(proxy) else url.openConnection()
            connection = (raw as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Connection", "close")
                useCaches = false
            }
            connection.responseCode
            (System.nanoTime() - startedAt) / 1_000_000
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
