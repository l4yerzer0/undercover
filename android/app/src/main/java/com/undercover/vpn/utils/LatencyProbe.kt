package com.undercover.vpn.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Measures round-trip latency over whatever path the app itself currently takes.
 *
 * This app sits inside the tunnel like any other, so the number covers
 * app → tun → sing-box → outbound → target. That is what a user reads as "ping",
 * but it is not the same as sing-box's own URLTest: URLTest talks to an outbound
 * directly and needs the target to be an outbound group, which a plain single
 * proxy is not. Once profiles gain a selector group for multiple connections,
 * switching to URLTest gives a per-connection figure instead of one for the
 * whole path.
 */
object LatencyProbe {
    private const val PROBE_URL = "https://cp.cloudflare.com/generate_204"
    private const val TIMEOUT_MS = 5_000

    /** Round trip in milliseconds, or null if the probe failed. */
    suspend fun measure(): Long? = withContext(Dispatchers.IO) {
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
