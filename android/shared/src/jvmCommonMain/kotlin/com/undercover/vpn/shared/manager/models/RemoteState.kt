package com.undercover.vpn.shared.manager.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ManagerProtocols {
    const val VLESS_REALITY = "vless-reality"
    const val HYSTERIA2 = "hysteria2"
}

@Serializable
data class StateUser(
    @SerialName("name") val name: String,
    @SerialName("uuid") val uuid: String? = null,
    @SerialName("password") val password: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RemoteState(
    @SerialName("version") val version: String = "1.0.0",
    @SerialName("sing_box_version") val singBoxVersion: String = "",
    @SerialName("protocol") val protocol: String = ManagerProtocols.VLESS_REALITY,
    @SerialName("port") val port: Int = 443,
    @SerialName("server_name") val serverName: String = "",
    @SerialName("public_key") val publicKey: String? = null,
    @SerialName("short_id") val shortId: String? = null,
    @SerialName("cert_pin_sha256") val certPinSha256: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("users") val users: List<StateUser> = emptyList()
)
