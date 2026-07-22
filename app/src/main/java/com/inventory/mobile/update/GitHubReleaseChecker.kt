package com.inventory.mobile.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.IOException
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

data class AvailableUpdate(
    val versionName: String,
    val downloadUrl: String,
)

enum class UpdateChannel(
    val id: String,
    val label: String,
    val description: String,
) {
    Stable(
        id = "stable",
        label = "Master stable",
        description = "Receive production releases from Master only.",
    ),
    BetaBrut(
        id = "beta-brut",
        label = "Beta Brut",
        description = "Receive Brutalist beta prereleases before they are promoted to Master.",
    );

    internal fun accepts(isPrerelease: Boolean, isDraft: Boolean, qualifier: String?): Boolean = when (this) {
        Stable -> !isPrerelease && !isDraft && qualifier == null
        BetaBrut -> isPrerelease && !isDraft && qualifier?.endsWith("-brut") == true
    }

    companion object {
        fun fromId(id: String?): UpdateChannel = entries.firstOrNull { it.id == id } ?: Stable
    }
}

object GitHubReleaseChecker {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findAvailableUpdate(
        repository: String,
        currentVersion: String,
        updateChannel: UpdateChannel = UpdateChannel.Stable,
    ): AvailableUpdate? = withContext(Dispatchers.IO) {
        val installedVersion = ReleaseVersion.parse(currentVersion) ?: return@withContext null
        val connection = (URL("https://api.github.com/repos/$repository/releases?per_page=20").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Inventory-Android")
            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub returned HTTP ${connection.responseCode} while checking for updates")
            }

            val releases = connection.inputStream.bufferedReader().use { reader ->
                json.decodeFromString<List<GitHubRelease>>(reader.readText())
            }
            releases
                .mapNotNull { release ->
                    val version = ReleaseVersion.parse(release.tagName) ?: return@mapNotNull null
                    if (!updateChannel.accepts(release.isPrerelease, release.isDraft, version.qualifier)) return@mapNotNull null
                    val asset = release.assets.firstOrNull { it.name.startsWith("Inventory-") && it.name.endsWith(".apk", ignoreCase = true) }
                        ?: return@mapNotNull null
                    ReleaseCandidate(version, AvailableUpdate(version.toString(), asset.downloadUrl))
                }
                .filter { it.version > installedVersion }
                .maxByOrNull { it.version }
                ?.update
        } finally {
            connection.disconnect()
        }
    }

    internal fun isNewer(candidate: String, current: String): Boolean {
        val candidateVersion = ReleaseVersion.parse(candidate) ?: return false
        val currentVersion = ReleaseVersion.parse(current) ?: return false
        return candidateVersion > currentVersion
    }

    internal fun belongsToChannel(
        tag: String,
        isPrerelease: Boolean,
        channel: UpdateChannel,
    ): Boolean {
        val version = ReleaseVersion.parse(tag) ?: return false
        return channel.accepts(isPrerelease, isDraft = false, qualifier = version.qualifier)
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("prerelease") val isPrerelease: Boolean = false,
        @SerialName("draft") val isDraft: Boolean = false,
        val assets: List<GitHubAsset>,
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    private data class ReleaseCandidate(
        val version: ReleaseVersion,
        val update: AvailableUpdate,
    )
}

private data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val qualifier: String?,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)
            .takeIf { it != 0 }
            ?.let { return it }
        if (qualifier == other.qualifier) return 0
        if (qualifier == null) return 1
        if (other.qualifier == null) return -1
        return qualifier.compareTo(other.qualifier)
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        qualifier?.let { append("-$it") }
    }

    companion object {
        private val pattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$")

        fun parse(value: String): ReleaseVersion? {
            val match = pattern.matchEntire(value) ?: return null
            return ReleaseVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                qualifier = match.groupValues[4].ifBlank { null },
            )
        }
    }
}
