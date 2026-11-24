package com.undercover.vpn.vendor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {

    @Test
    fun testCompareVersionNames_stable() {
        // Higher version is newer
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.5", "0.4") > 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.4", "0.5") < 0)
        assertEquals(0, GitHubUpdateChecker.compareVersionNames("0.5", "0.5"))

        // Prefix 'v' is stripped
        assertEquals(0, GitHubUpdateChecker.compareVersionNames("v0.5", "0.5"))
        assertTrue(GitHubUpdateChecker.compareVersionNames("v1.0.0", "0.9.9") > 0)
    }

    @Test
    fun testCompareVersionNames_beta() {
        // Base version with beta is newer than previous stable
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.6-beta1", "0.5") > 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.5", "0.6-beta1") < 0)

        // Final release is newer than its beta prerelease
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.5", "0.5-beta1") > 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.5-beta1", "0.5") < 0)

        // Higher beta is newer
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.5-beta2", "0.5-beta1") > 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.5-beta1", "0.5-beta2") < 0)
    }
}
