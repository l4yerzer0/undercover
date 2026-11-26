package com.undercover.vpn.shared.manager.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerStatus(
    @SerialName("service_state") val serviceState: String,
    @SerialName("uptime") val uptime: String,
    @SerialName("sing_box_version") val singBoxVersion: String,
    @SerialName("protocol") val protocol: String,
    @SerialName("port") val port: Int,
    @SerialName("server_name") val serverName: String,
    @SerialName("users_count") val usersCount: Int,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class UserOperationResult(
    @SerialName("share_link") val shareLink: String,
    @SerialName("user_name") val userName: String,
    @SerialName("protocol") val protocol: String,
    @SerialName("host") val host: String,
    @SerialName("port") val port: Int,
    @SerialName("uuid") val uuid: String? = null,
    @SerialName("password") val password: String? = null,
    @SerialName("server_name") val serverName: String = "",
    @SerialName("public_key") val publicKey: String? = null,
    @SerialName("short_id") val shortId: String? = null,
    @SerialName("cert_pin_sha256") val certPinSha256: String? = null
)
