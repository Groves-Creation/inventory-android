package com.inventory.mobile.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {
    @Test
    fun `recognizes a newer patch release`() {
        assertTrue(GitHubReleaseChecker.isNewer("v0.1.1", "0.1.0"))
    }

    @Test
    fun `recognizes stable release after pilot`() {
        assertTrue(GitHubReleaseChecker.isNewer("v0.1.0", "0.1.0-pilot"))
    }

    @Test
    fun `does not offer the current version`() {
        assertFalse(GitHubReleaseChecker.isNewer("v0.1.0-pilot", "0.1.0-pilot"))
    }

    @Test
    fun `stable channel accepts only stable Master releases`() {
        assertTrue(GitHubReleaseChecker.belongsToChannel("v0.3.1", isPrerelease = false, channel = UpdateChannel.Stable))
        assertFalse(GitHubReleaseChecker.belongsToChannel("v0.3.2-0721261939-brut", isPrerelease = true, channel = UpdateChannel.Stable))
    }

    @Test
    fun `beta brut channel accepts only Brutalist prereleases`() {
        assertTrue(GitHubReleaseChecker.belongsToChannel("v0.3.2-0721261939-brut", isPrerelease = true, channel = UpdateChannel.BetaBrut))
        assertFalse(GitHubReleaseChecker.belongsToChannel("v0.3.1", isPrerelease = false, channel = UpdateChannel.BetaBrut))
        assertFalse(GitHubReleaseChecker.belongsToChannel("v0.3.1-pilot", isPrerelease = true, channel = UpdateChannel.BetaBrut))
    }
}
