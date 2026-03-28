package com.recapmaker.app.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads a direct video URL (mp4, m3u8, etc.) to local cache.
 * Supports progress callback.
 */
object VideoDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class DownloadResult(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null,
    )

    /**
     * Download a video from a direct URL.
     * @param url Direct mp4/webm/m3u8 URL
     * @param context Android context for cache dir
     * @param onProgress (bytesDownloaded, totalBytes) → totalBytes may be -1 if unknown
     */
    suspend fun download(
        url: String,
        context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) RecapMaker/2.2")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext DownloadResult(false, error = "HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: return@withContext DownloadResult(false, error = "Empty response body")
            val contentLength = body.contentLength()

            // Determine extension from URL or content-type
            val ext = when {
                url.contains(".mp4", true) -> "mp4"
                url.contains(".webm", true) -> "webm"
                url.contains(".m3u8", true) -> "m3u8"
                response.header("Content-Type")?.contains("video/mp4") == true -> "mp4"
                else -> "mp4"
            }

            val outputFile = File(context.cacheDir, "dl_${System.currentTimeMillis()}.$ext")
            var downloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, contentLength)
                    }
                }
            }

            if (outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext DownloadResult(false, error = "Downloaded file is empty")
            }

            DownloadResult(true, outputFile)
        } catch (e: Exception) {
            DownloadResult(false, error = e.message ?: "Download failed")
        }
    }

    /**
     * Validate that a URL looks like a direct video link.
     */
    fun isDirectVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http") && (
            lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".mkv") ||
            lower.contains(".m3u8") || lower.contains(".ts") || lower.contains("video") ||
            lower.contains("mime=video")
        )
    }
}
