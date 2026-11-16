package com.undercover.vpn.desktop.core

import com.undercover.vpn.desktop.model.DesktopProfile
import com.undercover.vpn.desktop.model.DesktopSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object StorageManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val configDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val dir = if (os.contains("win")) {
            val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
            File(appData, "undercover")
        } else {
            val home = System.getProperty("user.home")
            File(home, ".config/undercover")
        }
        dir.mkdirs()
        dir
    }

    private val profilesFile: File get() = File(configDir, "profiles.json")
    private val settingsFile: File get() = File(configDir, "settings.json")
    val runningConfigFile: File get() = File(configDir, "running_config.json")

    fun loadSettings(): DesktopSettings {
        return try {
            if (settingsFile.exists()) {
                json.decodeFromString<DesktopSettings>(settingsFile.readText())
            } else {
                DesktopSettings().also { saveSettings(it) }
            }
        } catch (_: Exception) {
            DesktopSettings()
        }
    }

    fun saveSettings(settings: DesktopSettings) {
        try {
            settingsFile.writeText(json.encodeToString(settings))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadProfiles(): List<DesktopProfile> {
        return try {
            if (profilesFile.exists()) {
                json.decodeFromString<List<DesktopProfile>>(profilesFile.readText())
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProfiles(profiles: List<DesktopProfile>) {
        try {
            profilesFile.writeText(json.encodeToString(profiles))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
