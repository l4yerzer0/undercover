package com.undercover.vpn.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UserProfileManager {
    private val callbacks = mutableListOf<() -> Unit>()

    fun registerCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    fun unregisterCallback(callback: () -> Unit) {
        callbacks.remove(callback)
    }

    private fun notifyCallbacks() {
        for (callback in callbacks.toList()) {
            callback()
        }
    }

    suspend fun list(): List<UserProfile> = withContext(Dispatchers.IO) {
        ProfileManager.userProfileDao().list()
    }

    suspend fun get(id: Long): UserProfile? = withContext(Dispatchers.IO) {
        ProfileManager.userProfileDao().get(id)
    }

    suspend fun getActiveProfile(): UserProfile = withContext(Dispatchers.IO) {
        val dao = ProfileManager.userProfileDao()
        val activeId = Settings.activeUserProfileId
        var profile = if (activeId > 0) dao.get(activeId) else null
        if (profile == null) {
            val all = dao.list()
            profile = all.firstOrNull()
        }
        if (profile == null) {
            // First run or empty: create default profile from current global settings
            val defaultPrimary = Settings.selectedProfile
            val newProfile = UserProfile(
                name = "Основной",
                primaryServerId = defaultPrimary,
                routingDirectRuSites = Settings.routingDirectRuSites,
                routingDirectRuApps = Settings.routingDirectRuApps,
                routingByeDpiYouTube = Settings.routingByeDpiYouTube,
                routingByeDpiDiscord = Settings.routingByeDpiDiscord,
            ).apply {
                directApps = Settings.routingDirectList
                byeDpiApps = Settings.routingByeDpiList
                directDomains = Settings.routingCustomDirectDomains.toList()
                byeDpiDomains = Settings.routingCustomByeDpiDomains.toList()
            }
            newProfile.id = dao.insert(newProfile)
            Settings.activeUserProfileId = newProfile.id
            profile = newProfile
        }
        profile
    }

    suspend fun setActiveProfile(id: Long): UserProfile? = withContext(Dispatchers.IO) {
        val profile = get(id) ?: return@withContext null
        Settings.activeUserProfileId = profile.id
        if (profile.primaryServerId > 0L) {
            Settings.selectedProfile = profile.primaryServerId
        }
        notifyCallbacks()
        profile
    }

    suspend fun create(profile: UserProfile, andSelect: Boolean = false): UserProfile = withContext(Dispatchers.IO) {
        val nextOrder = ProfileManager.userProfileDao().nextOrder() ?: 0L
        profile.userOrder = nextOrder
        profile.id = ProfileManager.userProfileDao().insert(profile)
        if (andSelect) {
            Settings.activeUserProfileId = profile.id
            if (profile.primaryServerId > 0L) {
                Settings.selectedProfile = profile.primaryServerId
            }
        }
        notifyCallbacks()
        profile
    }

    suspend fun update(profile: UserProfile): Int = withContext(Dispatchers.IO) {
        try {
            val count = ProfileManager.userProfileDao().update(profile)
            if (profile.id == Settings.activeUserProfileId && profile.primaryServerId > 0L) {
                Settings.selectedProfile = profile.primaryServerId
            }
            count
        } finally {
            notifyCallbacks()
        }
    }

    suspend fun delete(profile: UserProfile): Int = withContext(Dispatchers.IO) {
        try {
            val count = ProfileManager.userProfileDao().delete(profile)
            if (Settings.activeUserProfileId == profile.id) {
                val remaining = ProfileManager.userProfileDao().list()
                if (remaining.isNotEmpty()) {
                    setActiveProfile(remaining.first().id)
                } else {
                    // Recreate default
                    getActiveProfile()
                }
            }
            count
        } finally {
            notifyCallbacks()
        }
    }
}
