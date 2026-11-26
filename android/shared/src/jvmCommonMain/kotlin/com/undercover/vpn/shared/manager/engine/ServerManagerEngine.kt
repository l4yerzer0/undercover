package com.undercover.vpn.shared.manager.engine

import com.undercover.vpn.shared.manager.models.ManagerProtocols
import com.undercover.vpn.shared.manager.models.RemoteState
import com.undercover.vpn.shared.manager.models.ServerStatus
import com.undercover.vpn.shared.manager.models.SingBoxInboundUser
import com.undercover.vpn.shared.manager.models.SingBoxServerConfig
import com.undercover.vpn.shared.manager.models.StateUser
import com.undercover.vpn.shared.manager.models.UserOperationResult
import com.undercover.vpn.shared.manager.share.ServerShareLinkBuilder
import com.undercover.vpn.shared.manager.ssh.SshTargetExecutor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

object ServerManagerEngine {

    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    private val secureRandom = SecureRandom()

    fun generatePassword(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateUuid(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Reads and parses /etc/undercover/state.json from the remote server.
     */
    suspend fun loadServerState(executor: SshTargetExecutor): Result<RemoteState> = runCatching {
        val bytes = executor.readFile("/etc/undercover/state.json")
        val content = String(bytes, Charsets.UTF_8)
        json.decodeFromString<RemoteState>(content)
    }

    /**
     * Saves remote state to /etc/undercover/state.json (perm 0600).
     */
    suspend fun saveServerState(executor: SshTargetExecutor, state: RemoteState): Result<Unit> = runCatching {
        val serialized = json.encodeToString(state)
        executor.writeFile("/etc/undercover/state.json", serialized.toByteArray(Charsets.UTF_8), 384 /* 0600 */)
    }

    /**
     * Reads /etc/undercover/config.json from the remote server.
     */
    suspend fun loadServerConfig(executor: SshTargetExecutor): Result<SingBoxServerConfig> = runCatching {
        val bytes = executor.readFile("/etc/undercover/config.json")
        val content = String(bytes, Charsets.UTF_8)
        json.decodeFromString<SingBoxServerConfig>(content)
    }

    /**
     * Retrieves runtime status of the Undercover service on the target host.
     */
    suspend fun getServerStatus(executor: SshTargetExecutor, host: String): Result<ServerStatus> = runCatching {
        val state = loadServerState(executor).getOrThrow()

        val svcRes = executor.execute("systemctl is-active undercover.service")
        val svcState = svcRes.stdout.trim()
        val isActive = svcState == "active"

        val uptimeRes = executor.execute("systemctl show undercover.service --property=ActiveEnterTimestamp")
        val rawUptime = uptimeRes.stdout.trim().removePrefix("ActiveEnterTimestamp=").trim()
        val uptime = if (rawUptime.isNotBlank()) rawUptime else "unknown"

        ServerStatus(
            serviceState = if (svcState.isNotBlank()) svcState else "inactive",
            uptime = uptime,
            singBoxVersion = state.singBoxVersion,
            protocol = state.protocol,
            port = state.port,
            serverName = state.serverName,
            usersCount = state.users.size,
            isActive = isActive
        )
    }

    /**
     * Returns the most recent systemd logs for the undercover service.
     */
    suspend fun getLogs(executor: SshTargetExecutor, lines: Int = 50): Result<String> = runCatching {
        val n = if (lines <= 0) 50 else lines
        val res = executor.execute("journalctl -u undercover.service -n $n --no-pager")
        if (!res.isSuccess) {
            throw IllegalStateException("Failed to fetch logs: ${res.combined}")
        }
        res.stdout
    }

    /**
     * Validates sing-box configuration file syntax on the remote host.
     */
    suspend fun checkConfig(executor: SshTargetExecutor): Result<String> = runCatching {
        val res = executor.execute("/usr/local/bin/sing-box check -c /etc/undercover/config.json")
        if (!res.isSuccess) {
            throw IllegalStateException("Configuration check failed: ${res.combined}")
        }
        res.combined
    }

    /**
     * Restarts the undercover service on the remote server.
     */
    suspend fun restartService(executor: SshTargetExecutor): Result<Unit> = runCatching {
        val res = executor.execute("systemctl restart undercover.service")
        if (!res.isSuccess) {
            throw IllegalStateException("Failed to restart service: ${res.combined}")
        }
    }

    /**
     * Starts the undercover service on the remote server.
     */
    suspend fun startService(executor: SshTargetExecutor): Result<Unit> = runCatching {
        val res = executor.execute("systemctl start undercover.service")
        if (!res.isSuccess) {
            throw IllegalStateException("Failed to start service: ${res.combined}")
        }
    }

    /**
     * Stops the undercover service on the remote server.
     */
    suspend fun stopService(executor: SshTargetExecutor): Result<Unit> = runCatching {
        val res = executor.execute("systemctl stop undercover.service")
        if (!res.isSuccess) {
            throw IllegalStateException("Failed to stop service: ${res.combined}")
        }
    }

    /**
     * Adds a new user to sing-box config and state.json, validates config, reloads service,
     * and returns the resulting share link.
     */
    suspend fun addUser(
        executor: SshTargetExecutor,
        host: String,
        userName: String
    ): Result<UserOperationResult> = runCatching {
        val cleanUserName = userName.trim()
        require(cleanUserName.isNotBlank()) { "User name cannot be empty" }

        val state = loadServerState(executor).getOrThrow()
        if (state.users.any { it.name.equals(cleanUserName, ignoreCase = true) }) {
            throw IllegalArgumentException("User '$cleanUserName' already exists on this server")
        }

        val originalCfgBytes = executor.readFile("/etc/undercover/config.json")
        val cfg = json.decodeFromString<SingBoxServerConfig>(String(originalCfgBytes, Charsets.UTF_8))
        if (cfg.inbounds.isEmpty()) {
            throw IllegalStateException("Invalid server configuration: no inbounds defined")
        }

        val isHy2 = state.protocol == ManagerProtocols.HYSTERIA2
        val newPassword = if (isHy2) generatePassword() else null
        val newUuid = if (!isHy2) generateUuid() else null

        val newInboundUser = if (isHy2) {
            SingBoxInboundUser(name = cleanUserName, password = newPassword)
        } else {
            SingBoxInboundUser(name = cleanUserName, uuid = newUuid, flow = "xtls-rprx-vision")
        }

        val updatedInbound = cfg.inbounds[0].copy(
            users = cfg.inbounds[0].users + newInboundUser
        )
        val updatedCfg = cfg.copy(
            inbounds = listOf(updatedInbound) + cfg.inbounds.drop(1)
        )

        val newCfgBytes = json.encodeToString(updatedCfg).toByteArray(Charsets.UTF_8)

        // Backup existing config
        try {
            executor.writeFile("/etc/undercover/config.json.bak", originalCfgBytes, 384)
        } catch (_: Exception) {}

        // Write new config
        executor.writeFile("/etc/undercover/config.json", newCfgBytes, 384)

        // Validate config
        val checkRes = executor.execute("/usr/local/bin/sing-box check -c /etc/undercover/config.json")
        if (!checkRes.isSuccess) {
            // Roll back
            executor.writeFile("/etc/undercover/config.json", originalCfgBytes, 384)
            throw IllegalStateException("New configuration validation failed (rolled back): ${checkRes.combined}")
        }

        // Restart service
        val restartRes = executor.execute("systemctl restart undercover.service")
        if (!restartRes.isSuccess) {
            throw IllegalStateException("Failed to reload service: ${restartRes.combined}")
        }

        // Update state.json
        val newStateUser = StateUser(
            name = cleanUserName,
            uuid = newUuid,
            password = newPassword,
            createdAt = Instant.now().toString()
        )
        val updatedState = state.copy(users = state.users + newStateUser)
        saveServerState(executor, updatedState).getOrThrow()

        // Build share link
        val shareLink = if (isHy2) {
            ServerShareLinkBuilder.buildHysteria2Link(
                password = newPassword ?: "",
                host = host,
                port = state.port,
                serverName = state.serverName,
                insecure = true,
                pinSha256 = state.certPinSha256,
                alias = cleanUserName
            )
        } else {
            ServerShareLinkBuilder.buildVlessRealityLink(
                uuid = newUuid ?: "",
                host = host,
                port = state.port,
                publicKey = state.publicKey ?: "",
                serverName = state.serverName,
                shortId = state.shortId,
                alias = cleanUserName
            )
        }

        UserOperationResult(
            shareLink = shareLink,
            userName = cleanUserName,
            protocol = state.protocol,
            host = host,
            port = state.port,
            uuid = newUuid,
            password = newPassword,
            serverName = state.serverName,
            publicKey = state.publicKey,
            shortId = state.shortId,
            certPinSha256 = state.certPinSha256
        )
    }

    /**
     * Removes a user from sing-box config and state.json, and restarts the service.
     */
    suspend fun removeUser(
        executor: SshTargetExecutor,
        userName: String
    ): Result<Unit> = runCatching {
        val cleanUserName = userName.trim()
        val state = loadServerState(executor).getOrThrow()

        val userToRemove = state.users.find { it.name.equals(cleanUserName, ignoreCase = true) }
            ?: throw IllegalArgumentException("User '$cleanUserName' not found")

        val remainingUsers = state.users.filterNot { it.name.equals(cleanUserName, ignoreCase = true) }
        if (remainingUsers.isEmpty()) {
            throw IllegalStateException("Cannot remove the last remaining user (at least one user is required)")
        }

        val originalCfgBytes = executor.readFile("/etc/undercover/config.json")
        val cfg = json.decodeFromString<SingBoxServerConfig>(String(originalCfgBytes, Charsets.UTF_8))
        if (cfg.inbounds.isEmpty()) {
            throw IllegalStateException("Invalid server configuration: no inbounds defined")
        }

        val newInboundUsers = cfg.inbounds[0].users.filterNot { it.name.equals(cleanUserName, ignoreCase = true) }
        val updatedInbound = cfg.inbounds[0].copy(users = newInboundUsers)
        val updatedCfg = cfg.copy(
            inbounds = listOf(updatedInbound) + cfg.inbounds.drop(1)
        )

        val newCfgBytes = json.encodeToString(updatedCfg).toByteArray(Charsets.UTF_8)

        // Backup
        try {
            executor.writeFile("/etc/undercover/config.json.bak", originalCfgBytes, 384)
        } catch (_: Exception) {}

        // Write
        executor.writeFile("/etc/undercover/config.json", newCfgBytes, 384)

        // Validate
        val checkRes = executor.execute("/usr/local/bin/sing-box check -c /etc/undercover/config.json")
        if (!checkRes.isSuccess) {
            executor.writeFile("/etc/undercover/config.json", originalCfgBytes, 384)
            throw IllegalStateException("Configuration validation failed after removing user: ${checkRes.combined}")
        }

        // Restart service
        val restartRes = executor.execute("systemctl restart undercover.service")
        if (!restartRes.isSuccess) {
            throw IllegalStateException("Failed to restart service: ${restartRes.combined}")
        }

        // Update state.json
        val updatedState = state.copy(users = remainingUsers)
        saveServerState(executor, updatedState).getOrThrow()
    }

    /**
     * Builds share link for an existing user on the server.
     */
    suspend fun getUserShareLink(
        executor: SshTargetExecutor,
        host: String,
        userName: String
    ): Result<UserOperationResult> = runCatching {
        val cleanUserName = userName.trim()
        val state = loadServerState(executor).getOrThrow()

        val user = state.users.find { it.name.equals(cleanUserName, ignoreCase = true) }
            ?: throw IllegalArgumentException("User '$cleanUserName' not found")

        val isHy2 = state.protocol == ManagerProtocols.HYSTERIA2
        val shareLink = if (isHy2) {
            ServerShareLinkBuilder.buildHysteria2Link(
                password = user.password ?: "",
                host = host,
                port = state.port,
                serverName = state.serverName,
                insecure = true,
                pinSha256 = state.certPinSha256,
                alias = cleanUserName
            )
        } else {
            ServerShareLinkBuilder.buildVlessRealityLink(
                uuid = user.uuid ?: "",
                host = host,
                port = state.port,
                publicKey = state.publicKey ?: "",
                serverName = state.serverName,
                shortId = state.shortId,
                alias = cleanUserName
            )
        }

        UserOperationResult(
            shareLink = shareLink,
            userName = cleanUserName,
            protocol = state.protocol,
            host = host,
            port = state.port,
            uuid = user.uuid,
            password = user.password,
            serverName = state.serverName,
            publicKey = state.publicKey,
            shortId = state.shortId,
            certPinSha256 = state.certPinSha256
        )
    }
}
