package com.undercover.vpn.shared.manager.store

import com.undercover.vpn.shared.manager.models.LocalServer
import com.undercover.vpn.shared.manager.models.LocalStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object LocalServerStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Default directory matching Go CLI on desktop: ~/.config/undercover
     */
    fun getDefaultConfigDir(): File {
        val userHome = System.getProperty("user.home") ?: "."
        return File(userHome, ".config/undercover").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    fun getStoreFile(baseDir: File): File {
        return File(baseDir, "servers.json")
    }

    @Synchronized
    fun loadStore(baseDir: File = getDefaultConfigDir()): LocalStore {
        val file = getStoreFile(baseDir)
        if (!file.exists()) {
            return LocalStore(emptyList())
        }
        return try {
            val content = file.readText(Charsets.UTF_8)
            json.decodeFromString<LocalStore>(content)
        } catch (_: Exception) {
            LocalStore(emptyList())
        }
    }

    @Synchronized
    fun saveStore(baseDir: File = getDefaultConfigDir(), store: LocalStore) {
        val file = getStoreFile(baseDir)
        file.parentFile?.mkdirs()
        val content = json.encodeToString(store)
        file.writeText(content, Charsets.UTF_8)
    }

    @Synchronized
    fun upsertServer(baseDir: File = getDefaultConfigDir(), server: LocalServer) {
        val current = loadStore(baseDir)
        val filtered = current.servers.filterNot { it.host.equals(server.host, ignoreCase = true) }
        val updated = current.copy(servers = filtered + server)
        saveStore(baseDir, updated)
    }

    @Synchronized
    fun removeServer(baseDir: File = getDefaultConfigDir(), host: String) {
        val current = loadStore(baseDir)
        val filtered = current.servers.filterNot { it.host.equals(host, ignoreCase = true) }
        saveStore(baseDir, current.copy(servers = filtered))
    }
}
