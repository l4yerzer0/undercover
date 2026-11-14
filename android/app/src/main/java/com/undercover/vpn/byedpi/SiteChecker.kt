package com.undercover.vpn.byedpi

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * Measures how many requests survive when sent through a running ByeDPI instance.
 *
 * Ported from ByeByeDPI's SiteCheckUtils (GPL-3.0). A request counts as blocked
 * when the body is cut short of the declared Content-Length, which is how RKN
 * filtering usually shows up: the response starts and then stops mid-stream.
 */
internal class SiteChecker(
    private val proxyHost: String,
    private val proxyPort: Int,
) {
    private companion object {
        private const val TAG = "ByeDpiSiteChecker"
        private const val MAX_BODY_BYTES = 1024L * 1024
    }

    suspend fun check(
        sites: List<String>,
        requestsPerSite: Int,
        timeoutSeconds: Long,
        concurrency: Int,
        onSiteChecked: ((site: String, success: Int, total: Int) -> Unit)? = null,
    ): List<Pair<String, Int>> {
        val semaphore = Semaphore(concurrency)
        return withContext(Dispatchers.IO) {
            sites.map { site ->
                async {
                    semaphore.withPermit {
                        val success = checkSite(site, requestsPerSite, timeoutSeconds)
                        onSiteChecked?.invoke(site, success, requestsPerSite)
                        site to success
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun checkSite(
        site: String,
        requestsPerSite: Int,
        timeoutSeconds: Long,
    ): Int = withContext(Dispatchers.IO) {
        val formatted = if (site.startsWith("http://") || site.startsWith("https://")) {
            site
        } else {
            "https://$site"
        }

        val url = try {
            URL(formatted)
        } catch (_: Exception) {
            Log.e(TAG, "invalid url: $formatted")
            return@withContext 0
        }

        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyPort))
        val timeoutMs = (timeoutSeconds * 1000).toInt()
        var successes = 0

        repeat(requestsPerSite) {
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection(proxy) as HttpURLConnection).apply {
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = true
                    setRequestProperty("Connection", "close")
                }

                val responseCode = connection.responseCode
                val declaredLength = connection.contentLengthLong
                val actualLength = readBody(connection, responseCode, declaredLength)

                if (declaredLength <= 0L || actualLength >= declaredLength) {
                    successes++
                } else {
                    Log.w(TAG, "$site truncated: $actualLength of $declaredLength")
                }
            } catch (e: Exception) {
                Log.d(TAG, "$site failed: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }

        successes
    }

    private fun readBody(
        connection: HttpURLConnection,
        responseCode: Int,
        declaredLength: Long,
    ): Long {
        var read = 0L
        try {
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            } ?: return 0L

            val buffer = ByteArray(8192)
            val limit = if (declaredLength > 0) declaredLength else MAX_BODY_BYTES
            while (read < limit) {
                val remaining = limit - read
                val toRead = if (remaining > buffer.size) buffer.size else remaining.toInt()
                val bytesRead = stream.read(buffer, 0, toRead)
                if (bytesRead == -1) break
                read += bytesRead
            }
        } catch (_: IOException) {
            // A cut stream is itself the signal; report what we managed to read.
        }
        return read
    }
}
