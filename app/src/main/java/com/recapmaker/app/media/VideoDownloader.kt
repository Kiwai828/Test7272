package com.recapmaker.app.media

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object VideoDownloader {

    /** A single downloadable format/resolution */
    data class VideoFormat(
        val formatId: String,
        val ext: String,
        val resolution: String,     // e.g. "720p", "1080p", "audio only"
        val fileSize: Long = -1,    // bytes
        val note: String = "",
    ) {
        val label: String get() = buildString {
            append(resolution)
            if (note.isNotBlank()) append(" ($note)")
            if (fileSize > 0) append(" • ${"%.1f".format(fileSize / (1024.0 * 1024.0))}MB")
        }
    }

    /** Info about a video URL — title, thumbnail, available formats */
    data class VideoInfo(
        val url: String,
        val title: String = "",
        val duration: Int = 0,       // seconds
        val thumbnail: String = "",
        val formats: List<VideoFormat> = emptyList(),
        val valid: Boolean = false,
        val error: String? = null,
        val isDirectUrl: Boolean = false, // true = direct mp4 link (no yt-dlp needed)
    )

    data class DownloadResult(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null,
    )

    /**
     * Get video info from URL using yt-dlp.
     * Returns title, duration, and list of available formats/resolutions.
     * Falls back to HEAD request for direct mp4 URLs.
     */
    suspend fun getVideoInfo(url: String, context: Context): VideoInfo = withContext(Dispatchers.IO) {
        val lower = url.lowercase()
        val isDirectLink = lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.contains("mime=video")

        if (isDirectLink) {
            // Direct URL — use HEAD request
            return@withContext getDirectUrlInfo(url)
        }

        // yt-dlp — get video info with available formats
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")
            request.addOption("--no-download")
            val info = YoutubeDL.getInstance().getInfo(request)

            val formats = mutableListOf<VideoFormat>()
            info.formats?.forEach { fmt ->
                val height = fmt.height ?: 0
                val vcodec = fmt.vcodec ?: "none"
                // Only include formats with video (not audio-only)
                if (height > 0 && vcodec != "none") {
                    formats.add(VideoFormat(
                        formatId = fmt.formatId ?: "",
                        ext = fmt.ext ?: "mp4",
                        resolution = "${height}p",
                        fileSize = fmt.filesize ?: -1,
                        note = fmt.formatNote ?: "",
                    ))
                }
            }

            // Sort by resolution descending, remove duplicates
            val uniqueFormats = formats
                .sortedByDescending { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
                .distinctBy { it.resolution }

            // Add "best" option at top
            val allFormats = mutableListOf(
                VideoFormat("best", "mp4", "Auto (Best)", note = "Recommended")
            ) + uniqueFormats

            VideoInfo(
                url = url,
                title = info.title ?: "Video",
                duration = info.duration?.toInt() ?: 0,
                thumbnail = info.thumbnail ?: "",
                formats = allFormats,
                valid = true,
            )
        } catch (e: Exception) {
            // yt-dlp failed — try as direct URL
            val directInfo = getDirectUrlInfo(url)
            if (directInfo.valid) directInfo
            else VideoInfo(url, error = "URL မှ video info ရယူ၍မရပါ: ${e.message?.take(100)}")
        }
    }

    private fun getDirectUrlInfo(url: String): VideoInfo {
        return try {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
            val request = Request.Builder().url(url).head().header("User-Agent", "Mozilla/5.0 RecapMaker").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return VideoInfo(url, error = "HTTP ${response.code}")
            val size = response.header("Content-Length")?.toLongOrNull() ?: -1
            val type = response.header("Content-Type") ?: ""
            VideoInfo(
                url = url,
                title = url.substringAfterLast("/").substringBefore("?").take(50),
                formats = listOf(VideoFormat("direct", "mp4", "Direct Download", fileSize = size)),
                valid = true,
                isDirectUrl = true,
            )
        } catch (e: Exception) {
            VideoInfo(url, error = e.message)
        }
    }

    /**
     * Download video with selected format.
     * Uses yt-dlp for YouTube/TikTok/etc, OkHttp for direct URLs.
     */
    suspend fun download(
        url: String, context: Context,
        formatId: String = "best",
        isDirectUrl: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (isDirectUrl || formatId == "direct") {
            return@withContext downloadDirect(url, context, onProgress)
        }

        // yt-dlp download
        try {
            val outputDir = File(context.cacheDir, "ytdl")
            outputDir.mkdirs()
            val outputTemplate = "${outputDir.absolutePath}/%(title).50s.%(ext)s"

            val request = YoutubeDLRequest(url)
            request.addOption("-f", if (formatId == "best") "best[ext=mp4]/best" else "$formatId+bestaudio/best")
            request.addOption("-o", outputTemplate)
            request.addOption("--merge-output-format", "mp4")
            request.addOption("--no-mtime")

            YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                onProgress(progress / 100f)
            }

            // Find downloaded file
            val files = outputDir.listFiles()?.filter { it.length() > 0 }?.sortedByDescending { it.lastModified() }
            val downloadedFile = files?.firstOrNull()

            if (downloadedFile != null && downloadedFile.exists()) {
                // Move to cache root for consistency
                val destFile = File(context.cacheDir, "dl_${System.currentTimeMillis()}.mp4")
                downloadedFile.renameTo(destFile)
                DownloadResult(true, destFile)
            } else {
                DownloadResult(false, error = "Download ပြီးပေမယ့် file မတွေ့ပါ")
            }
        } catch (e: Exception) {
            DownloadResult(false, error = "Download failed: ${e.message?.take(150)}")
        }
    }

    private fun downloadDirect(url: String, context: Context, onProgress: (Float) -> Unit): DownloadResult {
        return try {
            val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.MINUTES).followRedirects(true).build()
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 RecapMaker").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return DownloadResult(false, error = "HTTP ${response.code}")
            val body = response.body ?: return DownloadResult(false, error = "Empty response")
            val total = body.contentLength()
            val outFile = File(context.cacheDir, "dl_${System.currentTimeMillis()}.mp4")
            var downloaded = 0L
            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8192); var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n); downloaded += n
                        if (total > 0) onProgress(downloaded.toFloat() / total)
                    }
                }
            }
            if (outFile.length() > 0) DownloadResult(true, outFile) else { outFile.delete(); DownloadResult(false, error = "Empty file") }
        } catch (e: Exception) {
            DownloadResult(false, error = e.message)
        }
    }
}
