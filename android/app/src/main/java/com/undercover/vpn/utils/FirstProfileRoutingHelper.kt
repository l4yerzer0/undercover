package com.undercover.vpn.utils

import android.content.Context
import android.util.Log
import com.undercover.vpn.database.ProfileManager
import com.undercover.vpn.database.Settings
import com.undercover.vpn.database.UserProfileManager
import com.undercover.vpn.routing.RuleSetManager

object FirstProfileRoutingHelper {
    private const val TAG = "FirstProfileRoutingHelper"

    /**
     * Checks if this is the first profile being added to the app,
     * and if the user is in Russia (and not in Pro mode), enables
     * Russian routing defaults (direct RU sites/apps, ByeDPI for YouTube/Discord)
     * and returns true to indicate that routing setup / strategy testing should be run.
     */
    suspend fun applyRussianRoutingIfFirstProfile(context: Context): Boolean {
        try {
            val allProfiles = ProfileManager.list()
            // Only auto-apply if this is the first server/subscription in the app
            if (allProfiles.size <= 1) {
                if (!Settings.isProMode) {
                    val isRussia = LocationDetector.detectIsRussia(context)
                    if (isRussia) {
                        Log.i(TAG, "First profile imported: user in Russia (simple mode), applying RU routing defaults")
                        Settings.byeDpiEnabled = true
                        Settings.routingDirectRuSites = true
                        Settings.routingDirectRuApps = true
                        Settings.routingByeDpiYouTube = true
                        Settings.routingByeDpiDiscord = true

                        val activeProf = UserProfileManager.getActiveProfile()
                        activeProf.routingDirectRuSites = true
                        activeProf.routingDirectRuApps = true
                        activeProf.routingByeDpiYouTube = true
                        activeProf.routingByeDpiDiscord = true
                        UserProfileManager.update(activeProf)

                        RuleSetManager.ensureUpdatedInBackground()
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check or apply RU routing: ${e.message}")
        }
        return false
    }
}
