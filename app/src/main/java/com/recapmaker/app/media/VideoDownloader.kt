package com.recapmaker.app.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object VideoDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true).followSslRedirects(true)
        .build()

    /** Video info from HEAD request */
    data class VideoInfo(
        val url: String,
        val fileSize: Long = -1,        // bytes, -1 = unknown
        val contentType: String = "",   // e.g. video/mp4
        val valid: Boolean = false,
        val error: String? = null,
    ) {
        val fileSizeText: String get() = when {
            fileSize < 0 -> "Unknown"
            fileSize < 1024 * 1024 -> "${"%.1f".format(fileSize / 1024.0)} KB"
            else -> "${"%.1f".format(fileSize / (1024.0 * 1024.0))} MB"
        }
        val isVideo: Boolean get() = contentType.contains("video") || contentType.contains("octet-stream")
                || url.lowercase().let { it.contains(".mp4") || it.contains(".webm") || it.contains(".mkv") }
    }

    data class DownloadResult(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null,
    )

    /** Check video URL info without downloading (HEAD request) */
    suspend fun getVideoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).head()
                .header("User-Agent", "Mozilla/5.0 RecapMaker/2.2")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext VideoInfo(url, error = "HTTP ${response.code}")
            }
            val size = response.header("Content-Length")?.toLongOrNull() ?: -1
            val type = response.header("Content-Type") ?: ""
            VideoInfo(url, fileSize = size, contentType = type, valid = true)
        } catch (e: Exception) {
            VideoInfo(url, error = e.message ?: "Connection failed")
        }
    }

    /** Download video with progress */
    suspend fun download(
        url: String, context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 RecapMaker/2.2")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext DownloadResult(false, error = "HTTP ${response.code}")
            val body = response.body ?: return@withContext DownloadResult(false, error = "Empty response")
            val contentLength = body.contentLength()

            val ext = when {
                url.contains(".mp4", true) -> "mp4"
                url.contains(".webm", true) -> "webm"
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
            if (outputFile.length() == 0L) { outputFile.delete(); return@withContext DownloadResult(false, error = "File empty") }
            DownloadResult(true, outputFile)
        } catch (e: Exception) {
            DownloadResult(false, error = e.message ?: "Download failed")
        }
    }
}
