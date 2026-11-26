package com.undercover.vpn.routing

import android.util.Log
import com.undercover.vpn.Application
import com.undercover.vpn.database.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and caches binary sing-box rule-sets (.srs) in app-private storage so
 * routing rules stay up to date without blocking VPN startup.
 */
object RuleSetManager {
    private const val TAG = "RuleSetManager"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000

    const val TAG_GEOSITE_RU = "undercover-geosite-ru"
    const val TAG_GEOIP_RU = "undercover-geoip-ru"
    const val TAG_RU_APPS = "undercover-ru-apps"
    const val TAG_GEOSITE_AI = "undercover-geosite-ai"
    const val TAG_GEOSITE_YOUTUBE = "undercover-geosite-youtube"
    const val TAG_GEOSITE_DISCORD = "undercover-geosite-discord"
    const val TAG_GEOSITE_GAMES = "undercover-geosite-games"
    const val TAG_GEOSITE_STREAMING = "undercover-geosite-streaming"
    const val TAG_GEOSITE_SOCIAL = "undercover-geosite-social"

    data class RuleSetSpec(
        val tag: String,
        val fileName: String,
        val url: String,
    )

    val SPECS = listOf(
        RuleSetSpec(
            tag = TAG_GEOSITE_RU,
            fileName = "geosite-category-ru.srs",
            url = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs",
        ),
        RuleSetSpec(
            tag = TAG_GEOIP_RU,
            fileName = "geoip-ru.srs",
            url = "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs",
        ),
        RuleSetSpec(
            tag = TAG_RU_APPS,
            fileName = "ru-app-list.srs",
            url = "https://raw.githubusercontent.com/legiz-ru/sb-rule-sets/main/ru-app-list.srs",
        ),
        RuleSetSpec(
            tag = TAG_GEOSITE_AI,
            fileName = "geosite-category-ai-!cn.srs",
            url = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ai-!cn.srs",
        ),
        RuleSetSpec(
            tag = TAG_GEOSITE_YOUTUBE,
            fileName = "geosite-youtube.srs",
            url = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-youtube.srs",
        ),
        RuleSetSpec(
            tag = TAG_GEOSITE_DISCORD,
            fileName = "geosite-discord.srs",
            url = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-discord.srs",
        ),
        RuleSetSpec(
            tag = TAG_GEOSITE_GAMES,
            fileName = "geosite-category-games.srs",
            url = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-games.srs",
        ),
        RuleSetSpec(
            tag = TAG_GEOSITE_STREAMING,
            fileName = "geosite-category-entertainment.srs",
            url = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-entertainment.srs",
        ),
        RuleSetSpec(
            tag = TAG_GEOSITE_SOCIAL,
            fileName = "geosite-category-social-media-!cn.srs",
            url = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-social-media-!cn.srs",
        ),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateMutex = Mutex()

    private fun dir(): File? = try {
        File(Application.application.filesDir, "rule-sets").apply { mkdirs() }
    } catch (_: Throwable) {
        null
    }

    private fun fileFor(spec: RuleSetSpec): File? = dir()?.let { File(it, spec.fileName) }

    fun specForTag(tag: String): RuleSetSpec? = SPECS.firstOrNull { it.tag == tag }

    fun isAvailable(tag: String): Boolean {
        val spec = specForTag(tag) ?: return false
        val file = fileFor(spec) ?: return false
        return file.exists() && file.length() > 0L
    }

    fun filePath(tag: String): String? {
        val spec = specForTag(tag) ?: return null
        val file = fileFor(spec) ?: return null
        return if (file.exists() && file.length() > 0L) file.absolutePath else null
    }

    fun downloadedCount(): Int = SPECS.count { isAvailable(it.tag) }

    fun needsRefresh(): Boolean {
        if (SPECS.any { !isAvailable(it.tag) }) return true
        val updatedAt = Settings.routingRuleSetsUpdatedAt
        if (updatedAt <= 0L) return true
        return System.currentTimeMillis() - updatedAt > REFRESH_INTERVAL_MS
    }

    fun ensureUpdatedInBackground() {
        if (!needsRefresh()) return
        scope.launch {
            updateAll(force = false)
        }
    }

    suspend fun updateAll(force: Boolean = true): Result<Int> = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            if (!force && !needsRefresh()) {
                return@withLock Result.success(0)
            }
            var updated = 0
            var lastError: Exception? = null
            for (spec in SPECS) {
                try {
                    if (downloadSpec(spec)) {
                        updated++
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Failed to update rule-set ${spec.tag}: ${e.message}")
                }
            }
            if (updated > 0) {
                Settings.routingRuleSetsUpdatedAt = System.currentTimeMillis()
            }
            if (updated == 0 && lastError != null) {
                Result.failure(lastError)
            } else {
                Result.success(updated)
            }
        }
    }

    private fun downloadSpec(spec: RuleSetSpec): Boolean {
        val target = fileFor(spec) ?: return false
        val tmp = File(target.parentFile, "${spec.fileName}.tmp")
        var conn: HttpURLConnection? = null
        try {
            val url = URL(spec.url)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Undercover/1.0")
                useCaches = false
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${conn.responseCode} for ${spec.fileName}")
            }
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (tmp.length() <= 0L) {
                tmp.delete()
                throw IllegalStateException("Empty rule-set downloaded for ${spec.fileName}")
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            Log.i(TAG, "Updated rule-set ${spec.fileName} (${target.length()} bytes)")
            return true
        } finally {
            conn?.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }
}
