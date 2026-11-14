package com.undercover.vpn.byedpi

import android.content.Context
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Runs every candidate ByeDPI strategy in turn and scores it by how many probe
 * requests survive.
 *
 * The ByeDPI core keeps a single global instance, so strategies are tested one
 * at a time and the tester owns that instance for the duration — the VPN service
 * must not be running with ByeDPI enabled while a test is in progress.
 */
object ByeDpiStrategyTester {
    private const val TAG = "ByeDpiTester"
    private const val STRATEGIES_ASSET = "byedpi_strategies.list"
    private const val SITES_ASSET = "byedpi_sites.list"
    private const val SETTLE_DELAY_MS = 500L

    data class StrategyResult(
        val command: String,
        val success: Int = 0,
        val total: Int = 0,
        val completed: Boolean = false,
        val failedToStart: Boolean = false,
    ) {
        /** Share of probe requests that came back intact, 0f..1f. */
        val ratio: Float get() = if (total == 0) 0f else success.toFloat() / total
        val percent: Int get() = (ratio * 100).toInt()
    }

    fun loadStrategies(context: Context, sni: String = "google.com"): List<String> =
        readAsset(context, STRATEGIES_ASSET).map { it.replace("{sni}", sni) }

    fun loadSites(context: Context): List<String> = readAsset(context, SITES_ASSET)

    private fun readAsset(context: Context, name: String): List<String> = runCatching {
        context.assets.open(name).bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }.getOrElse {
        Log.e(TAG, "failed to read asset $name", it)
        emptyList()
    }

    /**
     * Tests [strategies] against [sites], reporting each strategy through
     * [onProgress] as it starts, advances and completes. Cancelling the calling
     * coroutine stops the run and shuts the proxy down.
     */
    suspend fun run(
        strategies: List<String>,
        sites: List<String>,
        requestsPerSite: Int = 1,
        timeoutSeconds: Long = 5,
        concurrency: Int = 20,
        onProgress: (index: Int, result: StrategyResult) -> Unit,
    ) {
        try {
            strategies.forEachIndexed { index, command ->
                if (!currentCoroutineContext().isActive) return

                var result = StrategyResult(command = command, total = sites.size * requestsPerSite)
                onProgress(index, result)

                val port = ByeDpiManager.start(command)
                if (port == null) {
                    Log.w(TAG, "strategy did not start: $command")
                    onProgress(index, result.copy(completed = true, failedToStart = true))
                    return@forEachIndexed
                }

                delay(SETTLE_DELAY_MS)

                SiteChecker("127.0.0.1", port).check(
                    sites = sites,
                    requestsPerSite = requestsPerSite,
                    timeoutSeconds = timeoutSeconds,
                    concurrency = concurrency,
                ) { _, success, _ ->
                    result = result.copy(success = result.success + success)
                    onProgress(index, result)
                }

                result = result.copy(completed = true)
                onProgress(index, result)

                ByeDpiManager.stop()
                delay(SETTLE_DELAY_MS)
            }
        } finally {
            // Covers cancellation as well: never leave the proxy running behind us.
            ByeDpiManager.stop()
        }
    }
}
