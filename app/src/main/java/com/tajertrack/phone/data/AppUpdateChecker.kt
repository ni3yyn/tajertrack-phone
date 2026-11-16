package com.tajertrack.phone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val version: String,
    val downloadUrl: String
)

sealed interface AppUpdateCheckResult {
    data object UpToDate : AppUpdateCheckResult
    data class Available(val update: AppUpdateInfo) : AppUpdateCheckResult
}

class AppUpdateCheckException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Checks the latest published stable GitHub release.
 *
 * Only the APK asset attached to a valid TajerTrack Phone release is returned.
 * The APK is opened in the user's browser; this checker never installs it.
 */
class AppUpdateChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun checkForUpdates(installedVersion: String): AppUpdateCheckResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "TajerTrack-Phone-Android")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            val release = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val message = when (response.code) {
                            404 -> "لا يوجد إصدار منشور متاح حالياً."
                            403, 429 -> "تعذر التحقق الآن بسبب حدّ طلبات GitHub. حاول بعد قليل."
                            else -> "تعذر الوصول إلى خادم التحديثات (HTTP ${response.code})."
                        }
                        throw AppUpdateCheckException(message)
                    }

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        throw AppUpdateCheckException("استجابة خادم التحديثات فارغة.")
                    }
                    JSONObject(body)
                }
            } catch (error: AppUpdateCheckException) {
                throw error
            } catch (error: Exception) {
                throw AppUpdateCheckException("تعذر الاتصال بخادم التحديثات. تحقق من الإنترنت وحاول مجدداً.", error)
            }

            if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                throw AppUpdateCheckException("لم يتم العثور على إصدار مستقر صالح للتنزيل.")
            }

            val releaseVersion = release.optString("tag_name")
                .trim()
                .removePrefix("v")
                .takeIf { it.isNotBlank() }
                ?: throw AppUpdateCheckException("بيانات إصدار التطبيق غير مكتملة.")

            val installedParts = parseVersion(installedVersion)
            val releaseParts = parseVersion(releaseVersion)
            if (compareVersions(releaseParts, installedParts) <= 0) {
                return@withContext AppUpdateCheckResult.UpToDate
            }

            val assets = release.optJSONArray("assets")
                ?: throw AppUpdateCheckException("لم يُرفق ملف APK بهذا الإصدار.")

            var downloadUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") == APK_ASSET_NAME) {
                    downloadUrl = validateDownloadUrl(asset.optString("browser_download_url"))
                    break
                }
            }

            val safeDownloadUrl = downloadUrl
                ?: throw AppUpdateCheckException("الإصدار الجديد منشور، لكن ملف APK غير متاح للتنزيل بعد.")

            AppUpdateCheckResult.Available(
                AppUpdateInfo(
                    version = releaseVersion,
                    downloadUrl = safeDownloadUrl
                )
            )
        }

    private fun parseVersion(value: String): List<Long> {
        val normalized = value.trim().removePrefix("v")
        val parts = normalized.split('.')
        if (parts.size != 3) {
            throw AppUpdateCheckException("تعذر قراءة رقم إصدار التطبيق.")
        }
        return parts.map { part ->
            part.toLongOrNull()
                ?: throw AppUpdateCheckException("تعذر قراءة رقم إصدار التطبيق.")
        }
    }

    private fun compareVersions(left: List<Long>, right: List<Long>): Int {
        for (index in 0..2) {
            val comparison = left[index].compareTo(right[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun validateDownloadUrl(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull()
        val valid = uri != null &&
            uri.scheme?.equals("https", ignoreCase = true) == true &&
            uri.host?.equals("github.com", ignoreCase = true) == true &&
            uri.path.startsWith(RELEASE_DOWNLOAD_PATH)

        if (!valid) {
            throw AppUpdateCheckException("تعذر التحقق من رابط تنزيل APK الخاص بالإصدار.")
        }
        return value
    }

    private companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/ni3yyn/tajertrack-phone/releases/latest"
        const val APK_ASSET_NAME = "tajertrack-phone.apk"
        const val RELEASE_DOWNLOAD_PATH =
            "/ni3yyn/tajertrack-phone/releases/download/"
    }
}
