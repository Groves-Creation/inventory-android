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
}
