package com.undercover.vpn.byedpi

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global runtime state for dynamic ByeDPI fallback.
 *
 * If the current ISP/network blocks ByeDPI strategies completely, YouTube and Discord
 * are dynamically routed through the primary VPN tunnel instead of being routed
 * into a dead SOCKS5 proxy.
 */
object ByeDpiRuntimeState {
    private const val TAG = "ByeDpiRuntimeState"

    private val _forceVpnForYouTube = MutableStateFlow(false)
    val forceVpnForYouTube = _forceVpnForYouTube.asStateFlow()

    private val _forceVpnForDiscord = MutableStateFlow(false)
    val forceVpnForDiscord = _forceVpnForDiscord.asStateFlow()

    val isYouTubeVpnFallback: Boolean get() = _forceVpnForYouTube.value
    val isDiscordVpnFallback: Boolean get() = _forceVpnForDiscord.value

    data class HealthStatus(
        val ytWorking: Boolean,
        val discordWorking: Boolean,
        val fallbackTriggered: Boolean,
    )

    fun resetFallback() {
        Log.i(TAG, "resetting VPN fallback state")
        _forceVpnForYouTube.value = false
        _forceVpnForDiscord.value = false
    }

    fun setFallback(youtubeVpn: Boolean, discordVpn: Boolean) {
        Log.i(TAG, "setFallback: youtubeVpn=$youtubeVpn, discordVpn=$discordVpn")
        _forceVpnForYouTube.value = youtubeVpn
        _forceVpnForDiscord.value = discordVpn
    }

    /**
     * Checks whether ByeDPI is actually functioning on the current network connection.
     * If ByeDPI is completely throttled or dropped by the ISP, updates fallback flags.
     */
    suspend fun checkHealth(port: Int): HealthStatus {
        Log.i(TAG, "running ByeDPI health check on port $port...")
        val result = ByeDpiProbe.probe(port, quickCheck = false)

        val ytOk = result.ytPassed
        val discordOk = result.discordPassed

        val fallbackNeeded = !ytOk || !discordOk
        Log.i(TAG, "health check result: YT=$ytOk, Discord=$discordOk (score=${result.score}, elapsed=${result.elapsedMs}ms)")

        if (!ytOk) {
            Log.w(TAG, "ISP blocks ByeDPI for YouTube -> engaging VPN fallback")
            _forceVpnForYouTube.value = true
        } else {
            _forceVpnForYouTube.value = false
        }

        if (!discordOk) {
            Log.w(TAG, "ISP blocks ByeDPI for Discord -> engaging VPN fallback")
            _forceVpnForDiscord.value = true
        } else {
            _forceVpnForDiscord.value = false
        }

        return HealthStatus(
            ytWorking = ytOk,
            discordWorking = discordOk,
            fallbackTriggered = fallbackNeeded,
        )
    }
}
