package com.undercover.vpn.vendor

import android.os.Build
import io.nekohasekai.libbox.Libbox
import com.undercover.vpn.BuildConfig
import com.undercover.vpn.ktx.unwrap
import com.undercover.vpn.update.UpdateInfo
import com.undercover.vpn.update.UpdateTrack
import com.undercover.vpn.utils.HTTPClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable

class GitHubUpdateChecker : Closeable {
    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/l4yerzer0/undercover/releases"
        private const val METADATA_FILENAME = "SFA-version-metadata.json"
        private const val ALT_METADATA_FILENAME = "version-metadata.json"
    }

    private val client = Libbox.newHTTPClient().apply {
        modernTLS()
        keepAlive()
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun checkUpdate(track: UpdateTrack, githubToken: String): UpdateInfo? {
        val releases = getReleases(githubToken)
        var selected: ReleaseCandidate? = null

        for (release in releases) {
            if (!isReleaseInTrack(release, track)) {
                continue
            }
            val metadata = runCatching { downloadMetadata(release) }.getOrNull()
                ?: run {
                    val cleanTag = release.tagName.removePrefix("v").trim()
                    if (cleanTag.isNotEmpty()) {
                        VersionMetadata(versionCode = 0, versionName = cleanTag)
                    } else null
                } ?: continue

            if (!isNewerThanCurrent(metadata)) {
                continue
            }
            val currentBest = selected
            if (currentBest == null || isBetterVersion(metadata, currentBest.metadata)) {
                selected = ReleaseCandidate(release, metadata)
            }
        }

        val release = selected?.release ?: return null
        val metadata = selected.metadata

        val isLegacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull()
        val apkAsset = release.assets.find { asset ->
            asset.name.endsWith(".apk") &&
                !asset.name.contains("play") &&
                primaryAbi != null &&
                asset.name.contains(primaryAbi)
        } ?: release.assets.find { asset ->
            asset.name.endsWith(".apk") &&
                !asset.name.contains("play") &&
                asset.name.contains("universal")
        } ?: release.assets.find { asset ->
            asset.name.endsWith(".apk") &&
                !asset.name.contains("play") &&
                asset.name.contains("legacy-android-5") == isLegacy
        } ?: release.assets.find { it.name.endsWith(".apk") }

        return UpdateInfo(
            versionCode = metadata.versionCode,
            versionName = metadata.versionName,
            downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl,
            releaseUrl = release.htmlUrl,
            releaseNotes = release.body,
            isPrerelease = release.prerelease,
            fileSize = apkAsset?.size ?: 0,
        )
    }

    private fun getReleases(githubToken: String): List<GitHubRelease> {
        val request = client.newRequest()
        request.setURL(RELEASES_URL)
        request.setHeader("Accept", "application/vnd.github.v3+json")
        val token = githubToken.trim()
        if (token.isNotEmpty()) {
            request.setHeader("Authorization", "Bearer $token")
        }
        request.setUserAgent(HTTPClient.userAgent)

        val response = request.execute()
        val content = response.content.unwrap

        return json.decodeFromString(content)
    }

    private fun isReleaseInTrack(release: GitHubRelease, track: UpdateTrack): Boolean {
        if (release.draft) {
            return false
        }
        return when (track) {
            UpdateTrack.STABLE -> !release.prerelease
            UpdateTrack.BETA -> true
        }
    }

    private fun isNewerThanCurrent(metadata: VersionMetadata): Boolean {
        val cmp = compareVersionNames(metadata.versionName, BuildConfig.VERSION_NAME)
        if (cmp != 0) return cmp > 0
        return metadata.versionCode > BuildConfig.VERSION_CODE
    }

    private fun isBetterVersion(version: VersionMetadata, other: VersionMetadata): Boolean {
        val cmp = compareVersionNames(version.versionName, other.versionName)
        if (cmp != 0) return cmp > 0
        return version.versionCode > other.versionCode
    }

    internal fun compareVersionNames(left: String, right: String): Int {
        val cleanLeft = left.removePrefix("v").substringBefore('-').trim()
        val cleanRight = right.removePrefix("v").substringBefore('-').trim()
        val leftParts = cleanLeft.split('.').map { it.toIntOrNull() ?: 0 }
        val rightParts = cleanRight.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until maxLen) {
            val l = leftParts.getOrElse(i) { 0 }
            val r = rightParts.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun downloadMetadata(release: GitHubRelease): VersionMetadata? {
        val metadataAsset = release.assets.find { it.name == METADATA_FILENAME || it.name == ALT_METADATA_FILENAME }
            ?: return null

        val request = client.newRequest()
        request.setURL(metadataAsset.browserDownloadUrl)
        request.setUserAgent(HTTPClient.userAgent)

        val response = request.execute()
        val content = response.content.unwrap

        return json.decodeFromString<VersionMetadata>(content)
    }

    override fun close() {
        client.close()
    }

    @Serializable
    data class GitHubRelease(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    data class GitHubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        val size: Long = 0,
    )

    @Serializable
    data class VersionMetadata(
        @SerialName("version_code") val versionCode: Int = 0,
        @SerialName("version_name") val versionName: String = "",
    )

    private data class ReleaseCandidate(
        val release: GitHubRelease,
        val metadata: VersionMetadata,
    )
}
