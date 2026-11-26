package com.undercover.vpn.byedpi

import android.content.Context
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Runs candidate ByeDPI strategies and scores them using realistic TLS 1.3 probes
 * with ML-KEM post-quantum key shares and media streaming / freeze checks.
 *
 * The ByeDPI core keeps a single global instance per process, so strategies are tested
 * sequentially and the tester owns that instance for the duration.
 */
object ByeDpiStrategyTester {
    private const val TAG = "ByeDpiTester"
    private const val STRATEGIES_ASSET = "byedpi_strategies.list"
    private const val SITES_ASSET = "byedpi_sites.list"
    private const val SETTLE_DELAY_MS = 250L

    data class StrategyResult(
        val command: String,
        val success: Int = 0,
        val total: Int = 100,
        val ytClientHello: Boolean = false,
        val ytMediaDownload: Boolean = false,
        val discordClientHello: Boolean = false,
        val elapsedMs: Long = 0L,
        val completed: Boolean = false,
        val failedToStart: Boolean = false,
    ) {
        val ratio: Float get() = if (total == 0) 0f else success.toFloat() / total
        val percent: Int get() = (ratio * 100).toInt()
        val score: Int get() = success

        val isYouTubeWorking: Boolean get() = ytClientHello && ytMediaDownload
        val isDiscordWorking: Boolean get() = discordClientHello
        val isPerfect: Boolean get() = isYouTubeWorking && isDiscordWorking
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
     * Modern testing entry-point: tests [strategies] using realistic TLS 1.3 and media probes.
     *
     * @param earlyStopOnPerfect If true, returns immediately when a 100% strategy is found.
     * @param onProgress Callback invoked as each strategy starts and completes.
     * @return Best strategy found, or null if all failed or were cancelled.
     */
    suspend fun runProbes(
        strategies: List<String>,
        earlyStopOnPerfect: Boolean = false,
        onProgress: (index: Int, result: StrategyResult) -> Unit,
    ): StrategyResult? {
        var bestResult: StrategyResult? = null

        try {
            for ((index, command) in strategies.withIndex()) {
                if (!currentCoroutineContext().isActive) break

                var current = StrategyResult(command = command, total = 100)
                onProgress(index, current)

                val port = ByeDpiManager.start(command)
                if (port == null) {
                    Log.w(TAG, "strategy failed to start: $command")
                    val failed = current.copy(completed = true, failedToStart = true)
                    onProgress(index, failed)
                    continue
                }

                delay(SETTLE_DELAY_MS)

                // First round: quick check
                val probe1 = ByeDpiProbe.probe(port, quickCheck = true)
                val finalProbe = if (!probe1.ytClientHello) {
                    // Fast rejection: if YouTube ClientHello is blocked, don't waste time on download
                    probe1
                } else {
                    // Deep probe: ClientHello + media freeze check
                    ByeDpiProbe.probe(port, quickCheck = false)
                }

                current = current.copy(
                    success = finalProbe.score,
                    total = 100,
                    ytClientHello = finalProbe.ytClientHello,
                    ytMediaDownload = finalProbe.ytMediaDownload,
                    discordClientHello = finalProbe.discordClientHello,
                    elapsedMs = finalProbe.elapsedMs,
                    completed = true,
                )

                onProgress(index, current)

                if (bestResult == null || current.success > bestResult.success) {
                    bestResult = current
                }

                ByeDpiManager.stop()
                delay(SETTLE_DELAY_MS)

                if (earlyStopOnPerfect && current.isPerfect) {
                    Log.i(TAG, "found 100% working strategy early: $command")
                    return current
                }
            }
        } finally {
            ByeDpiManager.stop()
        }

        return bestResult
    }

    /**
     * Backward compatible wrapper for legacy UI callers (e.g. ByeDpiScreen).
     */
    suspend fun run(
        strategies: List<String>,
        sites: List<String> = emptyList(),
        requestsPerSite: Int = 1,
        timeoutSeconds: Long = 5,
        concurrency: Int = 20,
        onProgress: (index: Int, result: StrategyResult) -> Unit,
    ) {
        runProbes(strategies, earlyStopOnPerfect = false, onProgress = onProgress)
    }
}
