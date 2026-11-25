package com.undercover.vpn.database

import android.os.Build
import androidx.room.Room
import com.undercover.vpn.Application
import com.undercover.vpn.BuildConfig
import com.undercover.vpn.bg.ProxyService
import com.undercover.vpn.bg.VPNService
import com.undercover.vpn.constant.Path
import com.undercover.vpn.constant.ServiceMode
import com.undercover.vpn.constant.SettingsKey
import com.undercover.vpn.database.preference.KeyValueDatabase
import com.undercover.vpn.database.preference.RoomPreferenceDataStore
import com.undercover.vpn.routing.RoutingSection
import com.undercover.vpn.ktx.boolean
import com.undercover.vpn.ktx.int
import com.undercover.vpn.ktx.long
import com.undercover.vpn.ktx.map
import com.undercover.vpn.ktx.string
import com.undercover.vpn.ktx.stringSet
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object Settings {
    @OptIn(DelicateCoroutinesApi::class)
    private val instance by lazy {
        Application.application.getDatabasePath(Path.SETTINGS_DATABASE_PATH).parentFile?.mkdirs()
        Room.databaseBuilder(
            Application.application,
            KeyValueDatabase::class.java,
            Path.SETTINGS_DATABASE_PATH,
        ).allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .enableMultiInstanceInvalidation()
            .setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }
    val dataStore = RoomPreferenceDataStore(instance.keyValuePairDao())
    var selectedProfile by dataStore.long(SettingsKey.SELECTED_PROFILE) { -1L }
    var activeUserProfileId by dataStore.long("active_user_profile_id") { 1L }
    var serviceMode by dataStore.string(SettingsKey.SERVICE_MODE) { ServiceMode.NORMAL }
    var startedByUser by dataStore.boolean(SettingsKey.STARTED_BY_USER)

    var updateSource by dataStore.string(SettingsKey.UPDATE_SOURCE) { "github" }
    var checkUpdateEnabled by dataStore.boolean(SettingsKey.CHECK_UPDATE_ENABLED) { false }
    var updateCheckPrompted by dataStore.boolean(SettingsKey.UPDATE_CHECK_PROMPTED) { false }
    var updateTrack by dataStore.string(SettingsKey.UPDATE_TRACK) {
        val versionName = BuildConfig.VERSION_NAME.lowercase()
        if (versionName.contains("-alpha") ||
            versionName.contains("-beta") ||
            versionName.contains("-rc")
        ) {
            "beta"
        } else {
            "stable"
        }
    }
    var githubToken by dataStore.string(SettingsKey.GITHUB_TOKEN) { "" }
    var silentInstallEnabled by dataStore.boolean(SettingsKey.SILENT_INSTALL_ENABLED) { false }
    var silentInstallMethod by dataStore.string(SettingsKey.SILENT_INSTALL_METHOD) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "PACKAGE_INSTALLER"
        } else {
            "SHIZUKU"
        }
    }
    var fdroidMirrorUrl by dataStore.string(SettingsKey.FDROID_MIRROR_URL) { "https://f-droid.org/repo" }
    var fdroidCustomMirrors by dataStore.stringSet(SettingsKey.FDROID_CUSTOM_MIRRORS) { emptySet() }
    var autoUpdateEnabled by dataStore.boolean(SettingsKey.AUTO_UPDATE_ENABLED) { false }
    var dynamicNotification by dataStore.boolean(SettingsKey.DYNAMIC_NOTIFICATION) { true }
    var disableDeprecatedWarnings by dataStore.boolean(SettingsKey.DISABLE_DEPRECATED_WARNINGS) { false }

    const val PER_APP_PROXY_DISABLED = 0
    const val PER_APP_PROXY_EXCLUDE = 1
    const val PER_APP_PROXY_INCLUDE = 2

    var autoRedirect by dataStore.boolean(SettingsKey.AUTO_REDIRECT) { false }
    var perAppProxyEnabled by dataStore.boolean(SettingsKey.PER_APP_PROXY_ENABLED) { false }
    var perAppProxyMode by dataStore.int(SettingsKey.PER_APP_PROXY_MODE) { PER_APP_PROXY_EXCLUDE }
    var perAppProxyList by dataStore.stringSet(SettingsKey.PER_APP_PROXY_LIST) { emptySet() }
    var perAppProxyManagedMode by dataStore.boolean(SettingsKey.PER_APP_PROXY_MANAGED_MODE) { false }
    var perAppProxyManagedList by dataStore.stringSet(SettingsKey.PER_APP_PROXY_MANAGED_LIST) { emptySet() }

    const val PACKAGE_QUERY_MODE_SHIZUKU = "SHIZUKU"
    const val PACKAGE_QUERY_MODE_ROOT = "ROOT"
    var perAppProxyPackageQueryMode by dataStore.string(SettingsKey.PER_APP_PROXY_PACKAGE_QUERY_MODE) { PACKAGE_QUERY_MODE_SHIZUKU }

    fun getEffectivePerAppProxyMode(): Int = if (perAppProxyManagedMode) {
        PER_APP_PROXY_EXCLUDE
    } else {
        perAppProxyMode
    }

    fun getEffectivePerAppProxyList(): Set<String> = if (perAppProxyManagedMode) {
        perAppProxyManagedList
    } else {
        perAppProxyList
    }

    var routingDirectList by dataStore.stringSet(SettingsKey.ROUTING_DIRECT_LIST) { emptySet() }
    var routingByeDpiList by dataStore.stringSet(SettingsKey.ROUTING_BYEDPI_LIST) { emptySet() }
    var routingDirectRuSites by dataStore.boolean(SettingsKey.ROUTING_DIRECT_RU_SITES) { true }
    var routingDirectRuApps by dataStore.boolean(SettingsKey.ROUTING_DIRECT_RU_APPS) { true }
    var routingRuleSetsUpdatedAt by dataStore.long(SettingsKey.ROUTING_RULE_SETS_UPDATED_AT) { 0L }
    var routingByeDpiYouTube by dataStore.boolean(SettingsKey.ROUTING_BYEDPI_YOUTUBE) { false }
    var routingByeDpiDiscord by dataStore.boolean(SettingsKey.ROUTING_BYEDPI_DISCORD) { false }
    var routingCustomDirectDomains by dataStore.stringSet(SettingsKey.ROUTING_CUSTOM_DIRECT_DOMAINS) { emptySet() }
    var routingCustomByeDpiDomains by dataStore.stringSet(SettingsKey.ROUTING_CUSTOM_BYEDPI_DOMAINS) { emptySet() }

    fun routingList(section: RoutingSection): Set<String> {
        val userProfile = runCatching { kotlinx.coroutines.runBlocking { UserProfileManager.getActiveProfile() } }.getOrNull()
        return when (section) {
            RoutingSection.DIRECT -> userProfile?.directApps ?: routingDirectList
            RoutingSection.BYEDPI -> userProfile?.byeDpiApps ?: routingByeDpiList
            RoutingSection.SECONDARY -> userProfile?.secondaryApps ?: emptySet()
        }
    }

    fun setRoutingList(section: RoutingSection, packages: Set<String>) {
        val userProfile = runCatching { kotlinx.coroutines.runBlocking { UserProfileManager.getActiveProfile() } }.getOrNull()
        when (section) {
            RoutingSection.DIRECT -> {
                routingDirectList = packages
                if (userProfile != null) {
                    userProfile.directApps = packages
                    runCatching { kotlinx.coroutines.runBlocking { UserProfileManager.update(userProfile) } }
                }
            }
            RoutingSection.BYEDPI -> {
                routingByeDpiList = packages
                if (userProfile != null) {
                    userProfile.byeDpiApps = packages
                    runCatching { kotlinx.coroutines.runBlocking { UserProfileManager.update(userProfile) } }
                }
            }
            RoutingSection.SECONDARY -> {
                if (userProfile != null) {
                    userProfile.secondaryApps = packages
                    runCatching { kotlinx.coroutines.runBlocking { UserProfileManager.update(userProfile) } }
                }
            }
        }
    }

    fun routingChannelList(channelId: String): Set<String> {
        val userProfile = runCatching { kotlinx.coroutines.runBlocking { UserProfileManager.getActiveProfile() } }.getOrNull()
        return userProfile?.channels?.firstOrNull { it.id == channelId }?.apps ?: emptySet()
    }

    fun setRoutingChannelList(channelId: String, packages: Set<String>) {
        val userProfile = runCatching { kotlinx.coroutines.runBlocking { UserProfileManager.getActiveProfile() } }.getOrNull() ?: return
        val updated = userProfile.channels.map { ch ->
            if (ch.id == channelId) ch.copy(apps = packages) else ch
        }
        userProfile.channels = updated
        runCatching { kotlinx.coroutines.runBlocking { UserProfileManager.update(userProfile) } }
    }

    var byeDpiEnabled by dataStore.boolean(SettingsKey.BYEDPI_ENABLED) { false }

    /** ByeDPI command line, without -i/-p which [ByeDpiManager] supplies itself. */
    var byeDpiStrategy by dataStore.string(SettingsKey.BYEDPI_STRATEGY) { "-Ar -s3 -q1" }

    var allowBypass by dataStore.boolean(SettingsKey.ALLOW_BYPASS) { false }
    var showDeveloperWarning by dataStore.boolean(SettingsKey.SHOW_DEVELOPER_WARNING) { true }
    var systemProxyEnabled by dataStore.boolean(SettingsKey.SYSTEM_PROXY_ENABLED) { true }

    var privilegeSettingsEnabled by dataStore.boolean(SettingsKey.PRIVILEGE_SETTINGS_ENABLED) { false }
    var privilegeSettingsList by dataStore.stringSet(SettingsKey.PRIVILEGE_SETTINGS_LIST) { emptySet() }
    var privilegeSettingsInterfaceRenameEnabled by dataStore.boolean(
        SettingsKey.PRIVILEGE_SETTINGS_INTERFACE_RENAME_ENABLED,
    ) { false }
    var privilegeSettingsInterfacePrefix by dataStore.string(SettingsKey.PRIVILEGE_SETTINGS_INTERFACE_PREFIX) { "wlan" }

    var oomKillerEnabled by dataStore.boolean(SettingsKey.OOM_KILLER_ENABLED) { false }
    var oomKillerDisabled by dataStore.boolean(SettingsKey.OOM_KILLER_DISABLED) { true }
    var oomMemoryLimitMB by dataStore.int(SettingsKey.OOM_MEMORY_LIMIT_MB) { 50 }

    var dashboardItemOrder by dataStore.string(SettingsKey.DASHBOARD_ITEM_ORDER) { "" }
    var dashboardDisabledItems by dataStore.stringSet(SettingsKey.DASHBOARD_DISABLED_ITEMS) { emptySet() }

    var activeRemoteServerId by dataStore.long(SettingsKey.ACTIVE_REMOTE_SERVER_ID) { 0L }

    // Tailscale SSH
    var tailscaleSSHRememberedUsernames by dataStore.map(SettingsKey.TAILSCALE_SSH_REMEMBERED_USERNAMES)
    var tailscaleSSHQuickConnectPeers by dataStore.stringSet(SettingsKey.TAILSCALE_SSH_QUICK_CONNECT_PEERS)
    var tailscaleSSHLightTheme by dataStore.string(SettingsKey.TAILSCALE_SSH_LIGHT_THEME) { "base16-3024-light" }
    var tailscaleSSHDarkTheme by dataStore.string(SettingsKey.TAILSCALE_SSH_DARK_THEME) { "argonaut" }
    
    var appTheme by dataStore.int(SettingsKey.APP_THEME) { 0 }
    var hasSeenOnboarding by dataStore.boolean(SettingsKey.HAS_SEEN_ONBOARDING) { false }
    var isProMode by dataStore.boolean(SettingsKey.IS_PRO_MODE) { false }

    var tailscaleSSHFontFamily by dataStore.string(SettingsKey.TAILSCALE_SSH_FONT_FAMILY)
    var tailscaleSSHFontSize by dataStore.int(SettingsKey.TAILSCALE_SSH_FONT_SIZE) { 14 }
    var tailscaleSSHCustomFontPath by dataStore.string(SettingsKey.TAILSCALE_SSH_CUSTOM_FONT_PATH)

    var cachedUpdateInfo by dataStore.string(SettingsKey.CACHED_UPDATE_INFO) { "" }
    var cachedApkPath by dataStore.string(SettingsKey.CACHED_APK_PATH) { "" }
    var lastShownUpdateVersion by dataStore.int(SettingsKey.LAST_SHOWN_UPDATE_VERSION) { 0 }

    fun serviceClass(): Class<*> = when (serviceMode) {
        ServiceMode.VPN -> VPNService::class.java
        else -> ProxyService::class.java
    }

    suspend fun rebuildServiceMode(): Boolean {
        var newMode = ServiceMode.NORMAL
        try {
            if (needVPNService()) {
                newMode = ServiceMode.VPN
            }
        } catch (_: Exception) {
        }
        if (serviceMode == newMode) {
            return false
        }
        serviceMode = newMode
        return true
    }

    private suspend fun needVPNService(): Boolean {
        val selectedProfileId = selectedProfile
        if (selectedProfileId == -1L) return false
        val profile = ProfileManager.get(selectedProfile) ?: return false
        val content = JSONObject(File(profile.typed.path).readText())
        val inbounds = content.getJSONArray("inbounds")
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.getJSONObject(index)
            if (inbound.getString("type") == "tun") {
                return true
            }
        }
        return false
    }
}

object ThemeState {
    val themeFlow = kotlinx.coroutines.flow.MutableStateFlow(Settings.appTheme)
}

object AppModeState {
    val isProFlow = kotlinx.coroutines.flow.MutableStateFlow(Settings.isProMode)

    fun setProMode(enabled: Boolean) {
        Settings.isProMode = enabled
        isProFlow.value = enabled
    }
}
