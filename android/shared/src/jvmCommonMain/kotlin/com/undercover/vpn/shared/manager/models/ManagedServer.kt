package com.undercover.vpn.shared.manager.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SshAuthType {
    @SerialName("password") PASSWORD,
    @SerialName("key_file") KEY_FILE,
    @SerialName("key_content") KEY_CONTENT
}

@Serializable
data class LocalServer(
    @SerialName("host") val host: String,
    @SerialName("ssh_port") val sshPort: Int = 22,
    @SerialName("user") val user: String = "root",
    @SerialName("key_path") val keyPath: String? = null,
    @SerialName("alias") val alias: String,
    @SerialName("protocol") val protocol: String? = null,
    @SerialName("vpn_port") val vpnPort: Int = 443,
    @SerialName("server_name") val serverName: String = "",
    @SerialName("public_key") val publicKey: String? = null,
    @SerialName("short_id") val shortId: String? = null,
    @SerialName("cert_pin_sha256") val certPinSha256: String? = null,
    @SerialName("user_auth") val userAuth: String? = null,
    @SerialName("share_link") val shareLink: String? = null,
    @SerialName("host_key_sha256") val hostKeySha256: String? = null,
    @SerialName("last_connected") val lastConnected: String? = null,

    // Additional fields for mobile/in-memory credentials
    @SerialName("password") val password: String? = null,
    @SerialName("private_key") val privateKey: String? = null,
    @SerialName("passphrase") val passphrase: String? = null
)

@Serializable
data class LocalStore(
    @SerialName("servers") val servers: List<LocalServer> = emptyList()
)
