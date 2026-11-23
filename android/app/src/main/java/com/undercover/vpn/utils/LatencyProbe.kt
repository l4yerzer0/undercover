package com.undercover.vpn.utils

import com.undercover.vpn.routing.RoutingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Measures round-trip latency through the active VPN tunnel.
 *
 * When [probeEndpoint] is supplied, routes the probe via sing-box's authenticated
 * loopback mixed inbound so the measurement traverses sing-box → active outbound → target
 * even when this app's own UID is excluded from TUN for child cores like xray-core.
 */
object LatencyProbe {
    private const val PROBE_URL = "https://cp.cloudflare.com/generate_204"
    private const val TIMEOUT_MS = 5_000

    /** Round trip in milliseconds, or null if the probe failed. */
    suspend fun measure(
        probeEndpoint: RoutingConfig.ProbeEndpoint? = null,
    ): Long? = withContext(Dispatchers.IO) {
        if (probeEndpoint != null) {
            return@withContext try {
                val startedAt = System.nanoTime()
                val resp = TunnelHttpClient.httpsGet(PROBE_URL, probeEndpoint, TIMEOUT_MS)
                if (resp.statusCode in 200..399) {
                    (System.nanoTime() - startedAt) / 1_000_000
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
        var connection: HttpURLConnection? = null
        try {
            val startedAt = System.nanoTime()
            connection = (URL(PROBE_URL).openConnection() as HttpURLConnection).apply {
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
