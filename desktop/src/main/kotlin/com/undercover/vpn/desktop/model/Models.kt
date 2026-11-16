package com.undercover.vpn.desktop.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ProfileType {
    SINGLE_LINK,
    REMOTE_SUBSCRIPTION,
    CUSTOM_CONFIG
}

@Serializable
enum class ProxyMode {
    SYSTEM_PROXY,
    TUN_VPN
}

@Serializable
data class ProxyNode(
    val tag: String,
    val type: String,
    val server: String = "",
    val port: Int = 0,
    val latencyMs: Int? = null
)

@Serializable
data class DesktopProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProfileType,
    val sourceUrl: String? = null,
    val rawConfig: String,
    val migratedConfig: String,
    val outbounds: List<ProxyNode> = emptyList(),
    val selectedOutboundTag: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val latencyMs: Int? = null
)

@Serializable
data class DesktopSettings(
    val proxyMode: ProxyMode = ProxyMode.SYSTEM_PROXY,
    val socksPort: Int = 2080,
    val httpPort: Int = 2081,
    val autoConnect: Boolean = false,
    val minimizeToTray: Boolean = true,
    val selectedProfileId: String? = null,
    val customSingBoxPath: String? = null
)

sealed class VpnState {
    data object Disconnected : VpnState()
    data class Connecting(val profileName: String) : VpnState()
    data class Connected(
        val profileName: String,
        val nodeName: String,
        val connectedSince: Long,
        val localSocksPort: Int,
        val localHttpPort: Int,
        val mode: ProxyMode
    ) : VpnState()
    data object Disconnecting : VpnState()
    data class Error(val message: String) : VpnState()
}
