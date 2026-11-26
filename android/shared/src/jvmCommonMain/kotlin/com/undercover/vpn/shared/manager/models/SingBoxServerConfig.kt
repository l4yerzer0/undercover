package com.undercover.vpn.shared.manager.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SingBoxInboundUser(
    @SerialName("name") val name: String? = null,
    @SerialName("uuid") val uuid: String? = null,
    @SerialName("flow") val flow: String? = null,
    @SerialName("password") val password: String? = null
)

@Serializable
data class SingBoxHandshakeOpts(
    @SerialName("server") val server: String,
    @SerialName("server_port") val serverPort: Int
)

@Serializable
data class SingBoxRealityOpts(
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("handshake") val handshake: SingBoxHandshakeOpts,
    @SerialName("private_key") val privateKey: String,
    @SerialName("short_id") val shortId: List<String>
)

@Serializable
data class SingBoxInboundTLS(
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("server_name") val serverName: String? = null,
    @SerialName("certificate_path") val certificatePath: String? = null,
    @SerialName("key_path") val keyPath: String? = null,
    @SerialName("reality") val reality: SingBoxRealityOpts? = null
)

@Serializable
data class SingBoxInboundConfig(
    @SerialName("type") val type: String,
    @SerialName("tag") val tag: String,
    @SerialName("listen") val listen: String = "::",
    @SerialName("listen_port") val listenPort: Int,
    @SerialName("users") val users: List<SingBoxInboundUser> = emptyList(),
    @SerialName("tls") val tls: SingBoxInboundTLS? = null,
    @SerialName("ignore_client_bandwidth") val ignoreClientBandwidth: Boolean? = null
)

@Serializable
data class SingBoxLogConfig(
    @SerialName("disabled") val disabled: Boolean = false,
    @SerialName("level") val level: String = "warn",
    @SerialName("timestamp") val timestamp: Boolean = true
)

@Serializable
data class SingBoxOutbound(
    @SerialName("type") val type: String,
    @SerialName("tag") val tag: String
)

@Serializable
data class SingBoxServerConfig(
    @SerialName("log") val log: SingBoxLogConfig = SingBoxLogConfig(),
    @SerialName("inbounds") val inbounds: List<SingBoxInboundConfig> = emptyList(),
    @SerialName("outbounds") val outbounds: List<SingBoxOutbound> = emptyList(),
    @SerialName("route") val route: JsonObject? = null,
    @SerialName("experimental") val experimental: JsonObject? = null,
    @SerialName("dns") val dns: JsonObject? = null
)
