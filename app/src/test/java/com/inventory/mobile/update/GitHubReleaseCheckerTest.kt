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
    fun `brutalist channel accepts only brutalist releases`() {
        assertTrue(GitHubReleaseChecker.isNewerInChannel("v0.3.1-0721260230-brut", "v0.3.0-0720260230-brut", "brutalist"))
        assertTrue(GitHubReleaseChecker.isNewerInChannel("v0.3.1-0721260231-brut", "v0.3.1-0721260230-brut", "brutalist"))
        assertFalse(GitHubReleaseChecker.isNewerInChannel("v0.4.0", "0.3.0-brutalist", "brutalist"))
    }

    @Test
    fun `stable channel ignores prereleases and brutalist releases`() {
        assertTrue(GitHubReleaseChecker.isNewerInChannel("v0.3.1", "0.3.0", "stable"))
        assertFalse(GitHubReleaseChecker.isNewerInChannel("v0.4.0-pilot", "0.3.0", "stable"))
        assertFalse(GitHubReleaseChecker.isNewerInChannel("v0.4.0-0721260230-brut", "0.3.0", "stable"))
    }
}
