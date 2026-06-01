package com.aistudio.sharmakhata.pqmzvk.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.aistudio.sharmakhata.pqmzvk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
//  UpdateInfo — parsed from the version.json hosted on your server
// ─────────────────────────────────────────────────────────────────────────────
data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val isMandatory: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
//  DownloadState — progress tracking for the APK download
// ─────────────────────────────────────────────────────────────────────────────
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data class Done(val apkFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  AppUpdateManager — singleton
// ─────────────────────────────────────────────────────────────────────────────
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"

    /**
     * URL of your version.json — hosted on your Render server.
     * Expected JSON format:
     * {
     *   "versionCode": 2,
     *   "versionName": "1.1",
     *   "apkUrl": "https://wpapp-xz9l.onrender.com/downloads/grahbook-latest.apk",
     *   "releaseNotes": "Bug fixes and performance improvements.",
     *   "mandatory": false
     * }
     */
    private const val VERSION_CHECK_URL =
        "https://wpapp-xz9l.onrender.com/api/app/version"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    // ── Version Check ──────────────────────────────────────────────────────────

    /**
     * Returns an [UpdateInfo] if a newer version is available, null otherwise.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(VERSION_CHECK_URL).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Version check failed: HTTP ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val serverVersionCode = json.getInt("versionCode")
            val currentVersionCode = BuildConfig.VERSION_CODE

            Log.d(TAG, "Current: $currentVersionCode  Server: $serverVersionCode")

            if (serverVersionCode > currentVersionCode) {
                UpdateInfo(
                    latestVersionCode = serverVersionCode,
                    latestVersionName = json.getString("versionName"),
                    apkUrl = json.getString("apkUrl"),
                    releaseNotes = json.optString("releaseNotes", ""),
                    isMandatory = json.optBoolean("mandatory", false)
                )
            } else {
                null // already up-to-date
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check error", e)
            null
        }
    }

    // ── APK Download & Install ─────────────────────────────────────────────────

    /**
     * Downloads the APK from [updateInfo.apkUrl] into the app's cache directory
     * and immediately launches the system installer.
     */
    suspend fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) {
        _downloadState.value = DownloadState.Downloading(0)
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(updateInfo.apkUrl).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    _downloadState.value = DownloadState.Error("Download failed: HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body ?: run {
                    _downloadState.value = DownloadState.Error("Empty response body")
                    return@withContext
                }

                // Write to cache/apk_updates/
                val updateDir = File(context.cacheDir, "apk_updates").also { it.mkdirs() }
                val apkFile = File(updateDir, "grahbook-${updateInfo.latestVersionName}.apk")

                val contentLength = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            if (contentLength > 0) {
                                val progress = ((downloaded * 100) / contentLength).toInt()
                                _downloadState.value = DownloadState.Downloading(progress)
                            }
                        }
                    }
                }

                _downloadState.value = DownloadState.Done(apkFile)

                // Trigger system installer on main thread
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                _downloadState.value = DownloadState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
