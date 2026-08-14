package com.recapmaker.app.ui.editor

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recapmaker.app.data.local.TokenManager
import com.recapmaker.app.data.local.VideoHistoryDao
import com.recapmaker.app.data.local.VideoHistoryEntity
import com.recapmaker.app.data.model.*
import com.recapmaker.app.data.repository.MainRepository
import com.recapmaker.app.data.repository.Result
import com.recapmaker.app.media.FFmpegProcessor
import com.recapmaker.app.media.VideoDownloader
import com.recapmaker.app.media.VideoProcessService
import com.recapmaker.app.media.VoxCpmClient
import com.arthenica.ffmpegkit.ReturnCode
import com.recapmaker.app.util.copyToFile
import com.recapmaker.app.util.getCostForDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.UUID
import javax.inject.Inject

data class VoxCpmVoiceSample(val id: String, val label: String, val description: String)

val builtInVoxCpmSamples = listOf(
    VoxCpmVoiceSample("anut_khway_mu", "ကိုအန့်ခေတ်မှူး", "Built-in voice sample"),
)

data class EditorState(
    val gold: Int = 0, val silver: Int = 0, val pricingTiers: List<PricingTier> = emptyList(),
    val videoUri: Uri? = null, val videoLocalPath: String? = null, val videoFilename: String? = null, val videoDuration: Int = 0,
    val videoWidth: Int = 0, val videoHeight: Int = 0,
    val previewWidth: Int = 0, val previewHeight: Int = 0,
    val urlInput: String = "", val isCheckingUrl: Boolean = false, val videoInfo: VideoDownloader.VideoInfo? = null,
    val showResolutionPopup: Boolean = false, val isDownloading: Boolean = false, val downloadProgress: Float = 0f,
    val flipEnabled: Boolean = false, val speedEnabled: Boolean = false, val pitchEnabled: Boolean = false, val noiseEnabled: Boolean = false,
    val blurEnabled: Boolean = false, val blurAreas: List<BlurArea> = emptyList(),
    val logoUri: Uri? = null, val logoArea: BlurArea = BlurArea(),
    val wmText: String = "", val wmPosition: String = "bottom_center", val wmSize: Int = 24, val wmColor: String = "#FFFFFF",
    val wmScroll: Boolean = false, val wmBox: Boolean = false, val wmBoxOpacity: Float = 0.5f,
    val aiText: String = "", val selectedVoice: String = "Puck", val voiceTab: String = "google", val voiceSearch: String = "",
    val isAnalyzing: Boolean = false, val isProcessing: Boolean = false, val processStatus: String = "",
    val error: String? = null, val success: String? = null,
    val history: List<VideoHistoryEntity> = emptyList(), val showHistory: Boolean = false,
    val videoEffects: FFmpegProcessor.VideoEffectsState = FFmpegProcessor.VideoEffectsState(),
    val bgMusicUri: Uri? = null, val bgMusicVolume: Float = 0.3f, val autoDuck: Boolean = true,
    val audioEffects: FFmpegProcessor.AudioEffectsState = FFmpegProcessor.AudioEffectsState(),
    val extraClips: List<String> = emptyList(),
    val subtitleEnabled: Boolean = false, val subtitleText: String = "",
    val useEdgeTts: Boolean = false, val edgeTtsAvailable: Boolean = false,
    val useVoxCpm: Boolean = false, val voxcpmReferencePath: String? = null, val voxcpmReferenceName: String? = null,
    val voxcpmSampleSource: String? = null,
)


@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: MainRepository,
    private val historyDao: VideoHistoryDao,
    private val tokenManager: TokenManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    var state by mutableStateOf(EditorState()); private set
    init {
        loadCoins(); loadHistory(); checkEdgeTts()
        val prefs = appContext.getSharedPreferences("voxcpm", Context.MODE_PRIVATE)
        val reference = prefs.getString("reference_path", null)?.takeIf { File(it).exists() }
        state = state.copy(
            useVoxCpm = prefs.getBoolean("enabled", false),
            voxcpmReferencePath = reference,
            voxcpmReferenceName = prefs.getString("reference_name", null),
            voxcpmSampleSource = prefs.getString("sample_source", null),
        )
    }

    private fun checkEdgeTts() { viewModelScope.launch { when (val r = repo.getEdgeTtsConfig()) { is Result.Success -> state = state.copy(edgeTtsAvailable = true); is Result.Error -> state = state.copy(edgeTtsAvailable = false) } } }

    private fun loadCoins() { viewModelScope.launch { when (val r = repo.getUserInfo()) { is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver, pricingTiers = r.data.pricing_tiers ?: emptyList()); is Result.Error -> {} } } }
    val costText: String get() { val d = state.videoDuration; if (d == 0 && state.videoLocalPath == null) return ""; val c = getCostForDuration(d, state.pricingTiers); if (c == -1) return "(ရှည်လွန်း)"; if (c == 0) return "(အခမဲ့)"; return if (state.aiText.isNotBlank() && VoiceData.isGeminiVoice(state.selectedVoice)) "(🥇 $c Gold)" else "($c Coins)" }
    val filteredVoices: List<VoiceInfo> get() { val s = if (state.voiceTab == "google") VoiceData.googleVoices else VoiceData.microsoftVoices; val q = state.voiceSearch.lowercase().trim(); return if (q.isEmpty()) s else s.filter { it.label.lowercase().contains(q) || it.gender.name.lowercase().contains(q) } }

    // ═══ VIDEO SOURCE ═══
    fun onVideoSelected(uri: Uri, context: Context) { viewModelScope.launch { state = state.copy(videoUri = uri, error = null); try { val t = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4"); uri.copyToFile(context, t); if (!t.exists() || t.length() == 0L) { state = state.copy(error = "File ဖတ်မရ"); return@launch }; val m = MediaMetadataRetriever(); m.setDataSource(t.absolutePath); val d = (m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000; val vw = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0; val vh = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0; m.release(); state = state.copy(videoLocalPath = t.absolutePath, videoDuration = d.toInt(), videoFilename = uri.lastPathSegment ?: "video.mp4", videoWidth = vw, videoHeight = vh) } catch (e: Exception) { state = state.copy(error = "${e.message}") } } }

    // ═══ URL DOWNLOAD ═══
    fun updateUrl(v: String) { state = state.copy(urlInput = v) }
    fun checkUrlInfo(ctx: Context) { val u = state.urlInput.trim(); if (u.isBlank()) return; viewModelScope.launch { state = state.copy(isCheckingUrl = true, error = null); val i = VideoDownloader.getVideoInfo(u, ctx); if (i.valid) state = state.copy(isCheckingUrl = false, videoInfo = i, showResolutionPopup = true) else state = state.copy(isCheckingUrl = false, error = i.error ?: "URL စစ်မရ") } }
    fun dismissResolutionPopup() { state = state.copy(showResolutionPopup = false) }
    fun downloadWithFormat(ctx: Context, fmt: VideoDownloader.VideoFormat) { val i = state.videoInfo ?: return; state = state.copy(showResolutionPopup = false, isDownloading = true, downloadProgress = 0f); viewModelScope.launch { val r = VideoDownloader.download(i.url, ctx, fmt.formatId, i.isDirectUrl) { p -> state = state.copy(downloadProgress = p) }; if (r.success && r.file != null) { var d = 0; var vw = 0; var vh = 0; try { val m = MediaMetadataRetriever(); m.setDataSource(r.file.absolutePath); d = ((m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000).toInt(); vw = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0; vh = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0; m.release() } catch (_: Exception) {}; state = state.copy(isDownloading = false, videoUri = Uri.fromFile(r.file), videoLocalPath = r.file.absolutePath, videoFilename = i.title.take(50).ifBlank { r.file.name }, videoDuration = if (d > 0) d else i.duration, videoWidth = vw, videoHeight = vh, urlInput = "", videoInfo = null) } else state = state.copy(isDownloading = false, error = r.error) } }

    // ═══ EFFECTS ═══
    fun toggleFlip(v: Boolean) { state = state.copy(flipEnabled = v) }
    fun toggleSpeed(v: Boolean) { state = state.copy(speedEnabled = v) }
    fun togglePitch(v: Boolean) { state = state.copy(pitchEnabled = v) }
    fun toggleNoise(v: Boolean) { state = state.copy(noiseEnabled = v) }
    fun toggleBlur(v: Boolean) { state = if (v && state.blurAreas.isEmpty()) state.copy(blurEnabled = true, blurAreas = listOf(BlurArea(50, 50, 120, 60))) else state.copy(blurEnabled = v) }
    fun addBlurBox() { state = state.copy(blurAreas = state.blurAreas + BlurArea(30 + state.blurAreas.size * 20, 30 + state.blurAreas.size * 20, 120, 60)) }
    fun removeBlurBox(i: Int) { state = state.copy(blurAreas = state.blurAreas.toMutableList().apply { if (i in indices) removeAt(i) }) }
    fun updateBlurBox(i: Int, a: BlurArea) { state = state.copy(blurAreas = state.blurAreas.toMutableList().apply { if (i in indices) set(i, a) }) }
    fun onLogoSelected(u: Uri) { state = state.copy(logoUri = u) }
    fun removeLogo() { state = state.copy(logoUri = null) }
    fun updateLogoArea(a: BlurArea) { state = state.copy(logoArea = a) }
    fun updatePreviewSize(w: Int, h: Int) { state = state.copy(previewWidth = w, previewHeight = h) }
    fun setWmText(v: String) { state = state.copy(wmText = v) }
    fun setWmPosition(v: String) { state = state.copy(wmPosition = v) }
    fun setWmSize(v: Int) { state = state.copy(wmSize = v) }
    fun setWmColor(v: String) { state = state.copy(wmColor = v) }
    fun setWmScroll(v: Boolean) { state = state.copy(wmScroll = v) }
    fun setWmBox(v: Boolean) { state = state.copy(wmBox = v) }
    fun setWmBoxOpacity(v: Float) { state = state.copy(wmBoxOpacity = v) }
    fun setAiText(v: String) { state = state.copy(aiText = v) }
    fun selectVoice(n: String) { val v = VoiceData.allVoices.find { it.name == n } ?: return; state = state.copy(selectedVoice = n, voiceTab = if (v.provider == VoiceProvider.Google) "google" else "microsoft", useVoxCpm = false, useEdgeTts = false) }
    fun switchVoiceTab(t: String) { state = state.copy(voiceTab = t, voiceSearch = "") }
    fun setVoiceSearch(q: String) { state = state.copy(voiceSearch = q) }

    // ═══ VIDEO EFFECTS ═══
    fun setVideoEffectGrayscale(v: Boolean) { state = state.copy(videoEffects = state.videoEffects.copy(grayscale = v)) }
    fun setVideoEffectSepia(v: Boolean) { state = state.copy(videoEffects = state.videoEffects.copy(sepia = v)) }
    fun setVideoEffectVignette(v: Boolean) { state = state.copy(videoEffects = state.videoEffects.copy(vignette = v)) }
    fun setVideoEffectBrightness(v: Float) { state = state.copy(videoEffects = state.videoEffects.copy(brightness = v)) }
    fun setVideoEffectContrast(v: Float) { state = state.copy(videoEffects = state.videoEffects.copy(contrast = v)) }

    // ═══ BACKGROUND MUSIC ═══
    fun setBgMusicUri(uri: Uri?) { state = state.copy(bgMusicUri = uri) }
    fun setBgMusicVolume(v: Float) { state = state.copy(bgMusicVolume = v) }
    fun setAutoDuck(v: Boolean) { state = state.copy(autoDuck = v) }

    // ═══ AUDIO EFFECTS ═══
    fun setAudioEffectEcho(v: Boolean) { state = state.copy(audioEffects = state.audioEffects.copy(echo = v)) }
    fun setAudioEffectReverb(v: Boolean) { state = state.copy(audioEffects = state.audioEffects.copy(reverb = v)) }
    fun setAudioEffectBassBoost(v: Boolean) { state = state.copy(audioEffects = state.audioEffects.copy(bassBoost = v)) }
    fun setEchoDelay(v: Float) { state = state.copy(audioEffects = state.audioEffects.copy(echoDelay = v)) }
    fun setEchoDecay(v: Float) { state = state.copy(audioEffects = state.audioEffects.copy(echoDecay = v)) }
    fun setReverbAmount(v: Float) { state = state.copy(audioEffects = state.audioEffects.copy(reverbAmount = v)) }
    fun setBassAmount(v: Float) { state = state.copy(audioEffects = state.audioEffects.copy(bassAmount = v)) }

    // ═══ MULTI-CLIP ═══
    // Content URIs can't be read by FFmpeg — copy each clip into cache before adding
    fun addExtraClip(uri: Uri, context: Context) {
        viewModelScope.launch {
            val f = File(context.cacheDir, "clip_${System.currentTimeMillis()}.mp4")
            if (uri.copyToFile(context, f) && f.length() > 0) state = state.copy(extraClips = state.extraClips + f.absolutePath)
            else state = state.copy(error = "Clip ဖတ်မရ")
        }
    }
    fun removeExtraClip(path: String) {
        state = state.copy(extraClips = state.extraClips - path)
        try { File(path).delete() } catch (_: Exception) {}
    }

    // ═══ SUBTITLE ═══
    fun setSubtitleEnabled(v: Boolean) { state = state.copy(subtitleEnabled = v) }
    fun setSubtitleText(v: String) { state = state.copy(subtitleText = v) }

    // ═══ EDGE TTS ═══
    fun setUseEdgeTts(v: Boolean) {
        if (!v) { state = state.copy(useEdgeTts = false); return }
        // Edge TTS (Azure) only supports the Microsoft Neural voices — switch off Google voices
        val voice = if (VoiceData.isGeminiVoice(state.selectedVoice)) "ThihaNeural" else state.selectedVoice
        state = state.copy(useEdgeTts = true, useVoxCpm = false, selectedVoice = voice, voiceTab = "microsoft")
    }
    fun setEdgeTtsAvailable(v: Boolean) { state = state.copy(edgeTtsAvailable = v) }

    fun setUseVoxCpm(v: Boolean) {
        state = state.copy(useVoxCpm = v, useEdgeTts = if (v) false else state.useEdgeTts)
        appContext.getSharedPreferences("voxcpm", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", v).apply()
    }

    fun setProvider(provider: String) {
        when (provider) {
            "voxcpm" -> setUseVoxCpm(true)
            "edge" -> {
                if (state.edgeTtsAvailable) setUseEdgeTts(true)
                else state = state.copy(error = "Edge TTS မရနိုင်ပါ")
            }
            "google" -> {
                state = state.copy(useVoxCpm = false, useEdgeTts = false, voiceTab = "google", voiceSearch = "")
                appContext.getSharedPreferences("voxcpm", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            }
        }
    }

    fun onVoxCpmReferenceSelected(uri: Uri, context: Context) {
        viewModelScope.launch {
            val dir = File(context.filesDir, "voxcpm").apply { mkdirs() }
            val displayName = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                }
            }.getOrNull() ?: uri.lastPathSegment ?: "voice_sample.wav"
            val extension = displayName.substringAfterLast('.', "wav").lowercase()
                .takeIf { it in setOf("wav", "mp3", "m4a", "ogg", "aac", "flac") } ?: "wav"
            val target = File(dir, "reference_${System.currentTimeMillis()}.$extension")
            if (!uri.copyToFile(context, target) || !target.exists() || target.length() == 0L) {
                target.delete()
                state = state.copy(error = "Voice sample ဖတ်မရပါ")
                return@launch
            }
            val durationSec = runCatching {
                MediaMetadataRetriever().let { retriever ->
                    retriever.setDataSource(target.absolutePath)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                    duration / 1000L
                }
            }.getOrDefault(0L)
            if (durationSec > 50L) {
                target.delete()
                state = state.copy(error = "VoxCPM voice sample သည် 50 seconds ထက် မကျော်ရပါ")
                return@launch
            }
            state.voxcpmReferencePath?.let { old -> runCatching { File(old).delete() } }
            state = state.copy(voxcpmReferencePath = target.absolutePath, voxcpmReferenceName = displayName, voxcpmSampleSource = "custom", error = null)
            context.getSharedPreferences("voxcpm", Context.MODE_PRIVATE).edit()
                .putString("reference_path", target.absolutePath)
                .putString("reference_name", displayName)
                .putString("sample_source", "custom")
                .apply()
        }
    }

    fun selectVoxCpmBuiltIn(id: String, context: Context) {
        val sample = builtInVoxCpmSamples.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val target = File(context.filesDir, "voxcpm/prebuilt_${sample.id}.mp3").apply { parentFile?.mkdirs() }
            runCatching {
                context.resources.openRawResource(com.recapmaker.app.R.raw.ko_anut_khway_mu).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure {
                state = state.copy(error = "Pre-built voice sample ဖတ်မရပါ")
                return@launch
            }
            state.voxcpmReferencePath?.takeIf { it != target.absolutePath }?.let { old -> runCatching { File(old).delete() } }
            state = state.copy(voxcpmReferencePath = target.absolutePath, voxcpmReferenceName = sample.label, voxcpmSampleSource = "prebuilt:$id", error = null)
            appContext.getSharedPreferences("voxcpm", Context.MODE_PRIVATE).edit()
                .putString("reference_path", target.absolutePath)
                .putString("reference_name", sample.label)
                .putString("sample_source", "prebuilt:$id")
                .apply()
        }
    }

    fun removeVoxCpmReference(context: Context) {
        state.voxcpmReferencePath?.let { runCatching { File(it).delete() } }
        state = state.copy(voxcpmReferencePath = null, voxcpmReferenceName = null, voxcpmSampleSource = null)
        context.getSharedPreferences("voxcpm", Context.MODE_PRIVATE).edit()
            .remove("reference_path").remove("reference_name").remove("sample_source").apply()
    }

    // ═══ AI ═══
    fun analyzeScript(ctx: Context) { val vp = state.videoLocalPath ?: run { state = state.copy(error = "Video ရွေးပါ"); return }; viewModelScope.launch { state = state.copy(isAnalyzing = true, error = null, processStatus = "Audio extract..."); try { val ap = FFmpegProcessor.extractAudio(vp, ctx) ?: run { state = state.copy(isAnalyzing = false, processStatus = "", error = "Audio extract မရ"); return@launch }; state = state.copy(processStatus = "AI Transcribe..."); val af = File(ap); val b64 = android.util.Base64.encodeToString(af.readBytes(), android.util.Base64.NO_WRAP); af.delete(); when (val r = repo.analyzeText(text = "", instruction = "Listen to this audio. Transcribe to English, translate to natural spoken Burmese. Output ONLY Burmese text.", audioBase64 = b64)) { is Result.Success -> { val t = r.data.text ?: ""; if (t.isBlank()) state = state.copy(isAnalyzing = false, processStatus = "", error = "စကားမတွေ့") else state = state.copy(aiText = t, isAnalyzing = false, processStatus = "") }; is Result.Error -> state = state.copy(isAnalyzing = false, processStatus = "", error = "AI: ${r.message}") } } catch (e: Exception) { state = state.copy(isAnalyzing = false, processStatus = "", error = "${e.message}") } } }
    fun translateScript() { if (state.aiText.isBlank()) return; viewModelScope.launch { state = state.copy(isAnalyzing = true); when (val r = repo.analyzeText(text = state.aiText, instruction = "Translate to natural spoken Burmese. Output ONLY Burmese text.")) { is Result.Success -> state = state.copy(aiText = r.data.text ?: state.aiText, isAnalyzing = false); is Result.Error -> state = state.copy(isAnalyzing = false, error = "${r.message}") } } }

    // ═══════════════════════════════════════════
    // PROCESS VIDEO
    //
    // TTS flow:
    //   1. Split long text into chunks (max 800 chars each)
    //   2. Generate TTS for each chunk → collect audio files
    //   3. Concatenate all chunks → single audio file
    //   4. Speed-match TTS audio to video duration
    //   5. Start FFmpeg process with matched audio
    // ═══════════════════════════════════════════

    fun startProcessing(context: Context) {
        if (state.isProcessing) return
        val inputPath = state.videoLocalPath ?: run { state = state.copy(error = "Video ရွေးပါ"); return }
        viewModelScope.launch {
            state = state.copy(isProcessing = true, error = null, success = null)
            val normalizedText = normalizeTtsText(state.aiText)
            if (state.aiText.isNotBlank() && normalizedText.isBlank()) {
                state = state.copy(isProcessing = false, processStatus = "", error = "အသံဖန်တီးရန် စာသားမရှိပါ။ စာသားကို ပြန်စစ်ပါ။")
                return@launch
            }
            state = state.copy(aiText = normalizedText)

            // ── 1. VoxCPM2 configuration preflight before any coin deduction ──
            val voxCpm2Token = if (state.useVoxCpm && normalizedText.isNotBlank()) {
                state = state.copy(processStatus = "VoxCPM2 authentication စစ်ဆေးနေသည်...")
                tokenManager.getVoxCpm2Token().orEmpty()
            } else ""
            if (state.useVoxCpm && normalizedText.isNotBlank()) {
                val preflightError = VoxCpmClient.preflight(voxCpm2Token)
                if (preflightError != null) {
                    state = state.copy(isProcessing = false, processStatus = "", error = preflightError)
                    return@launch
                }
            }

            // ── 2. Coins ──
            state = state.copy(processStatus = "Coins စစ်ဆေးနေသည်...")
            val billingInfo = when (val user = repo.getUserInfo()) {
                is Result.Success -> {
                    val freshTiers = user.data.pricing_tiers ?: emptyList()
                    state = state.copy(gold = user.data.gold, silver = user.data.silver, pricingTiers = freshTiers)
                    freshTiers
                }
                is Result.Error -> {
                    state = state.copy(isProcessing = false, processStatus = "", error = "Coins: ${user.message}")
                    return@launch
                }
            }
            val cost = getCostForDuration(state.videoDuration, billingInfo)
            val billingReason = "Video ${state.videoDuration}s job=${UUID.randomUUID()}"
            var coinTypeUsed = "auto"
            if (cost > 0) {
                val isGemini = state.aiText.isNotBlank() && VoiceData.isGeminiVoice(state.selectedVoice)
                coinTypeUsed = if (isGemini) "gold" else "auto"
                when (val r = repo.deductCoins(cost, billingReason, coinTypeUsed)) {
                    is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver)
                    is Result.Error -> { state = state.copy(isProcessing = false, processStatus = "", error = "Coins: ${r.message}"); return@launch }
                }
            } else if (cost == -1) { state = state.copy(isProcessing = false, processStatus = "", error = "Video ရှည်လွန်း"); return@launch }

            // ── 3. TTS ──
            var ttsAudioPath: String? = null
            if (state.aiText.isNotBlank()) {
                ttsAudioPath = when {
                    state.useVoxCpm -> {
                        state = state.copy(processStatus = "VoxCPM2 Cloudflare voice generating...")
                        generateVoxCpmAudio(context, voxCpm2Token)
                    }
                    state.useEdgeTts && state.edgeTtsAvailable -> {
                        state = state.copy(processStatus = "Edge TTS generating...")
                        generateEdgeTtsAudio(context, state.aiText, state.selectedVoice)
                    }
                    else -> generateFullTtsAudio(context, state.aiText, state.selectedVoice, state.videoDuration)
                }
                if (ttsAudioPath == null) {
                    if (cost > 0) {
                        repo.refundCoins(cost, "$billingReason failed: TTS generation", if (coinTypeUsed == "gold") "gold" else "silver")
                        loadCoins()
                    }
                    val detail = state.error?.takeIf { it.isNotBlank() } ?: "အသံဖန်တီးမရပါ"
                    state = state.copy(isProcessing = false, processStatus = "", error = detail)
                    return@launch
                }
            }

            // ── 4. SRT Subtitles ──
            var srtPath: String? = null
            if (state.subtitleEnabled && state.aiText.isNotBlank() && ttsAudioPath != null) {
                state = state.copy(processStatus = "Generating SRT...")
                srtPath = generateSrtFromTts(context, state.aiText, ttsAudioPath)
            }

            // ── 5. Logo ──
            var logoPath: String? = null
            state.logoUri?.let { uri -> val f = File(context.cacheDir, "logo_${System.currentTimeMillis()}.png"); if (uri.copyToFile(context, f) && f.exists()) logoPath = f.absolutePath }

            // ── 6. Background music ──
            // Note: no pre-ducking here — FFmpegProcessor.buildCommand already applies
            // sidechaincompress (auto-duck) when bg music + TTS are both present. Mixing the
            // TTS into the music file first duplicated the voice track and ducked it twice.
            var bgMusicPath: String? = null
            state.bgMusicUri?.let { uri ->
                state = state.copy(processStatus = "Preparing background music...")
                val f = File(context.cacheDir, "bgmusic_${System.currentTimeMillis()}.mp3")
                if (uri.copyToFile(context, f) && f.exists()) bgMusicPath = f.absolutePath
            }

            // ── 7. Build options + start service ──
            state = state.copy(processStatus = "Video processing...")
            val opts = FFmpegProcessor.ProcessOptions(
                flip = state.flipEnabled, speed = state.speedEnabled, pitch = state.pitchEnabled, noise = state.noiseEnabled,
                blurAreas = if (state.blurEnabled) state.blurAreas else emptyList(),
                logoPath = logoPath, logoX = state.logoArea.x, logoY = state.logoArea.y, logoW = state.logoArea.w.coerceAtLeast(10), logoH = state.logoArea.h.coerceAtLeast(10),
                watermarkText = state.wmText, watermarkPosition = state.wmPosition, watermarkSize = state.wmSize, watermarkColor = state.wmColor,
                watermarkScroll = state.wmScroll, watermarkBox = state.wmBox, watermarkBoxOpacity = state.wmBoxOpacity,
                ttsAudioPath = ttsAudioPath, videoDurationSec = state.videoDuration,
                videoWidth = state.videoWidth, videoHeight = state.videoHeight,
                previewWidth = state.previewWidth, previewHeight = state.previewHeight,
                videoEffects = state.videoEffects, bgMusicPath = bgMusicPath, bgMusicVolume = state.bgMusicVolume, autoDuck = state.autoDuck,
                audioEffects = state.audioEffects, extraClips = state.extraClips,
                subtitlePath = srtPath,
            )

            VideoProcessService.reset()
            VideoProcessService.pendingInputPath = inputPath
            VideoProcessService.pendingOptions = opts

            try {
                val intent = Intent(context, VideoProcessService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            } catch (e: Exception) {
                Log.e("Editor", "Service start fail: ${e.message}, fallback direct")
                state = state.copy(processStatus = "Direct processing...")
                val result = FFmpegProcessor.process(inputPath, context, opts)
                finishProcess(result, cost, coinTypeUsed, billingReason)
                cleanup(ttsAudioPath, logoPath, bgMusicPath, srtPath); return@launch
            }

            delay(1000)
            var maxWait = 600
            while (VideoProcessService.isRunning && maxWait > 0) {
                state = state.copy(processStatus = VideoProcessService.currentStatus.ifBlank { "Processing..." })
                delay(500); maxWait--
            }

            val success = VideoProcessService.resultSuccess
            if (success == true) {
                historyDao.insert(VideoHistoryEntity(fileName = state.videoFilename ?: "video.mp4", filePath = VideoProcessService.resultOutputPath ?: "", status = "completed", duration = state.videoDuration))
                state = state.copy(isProcessing = false, processStatus = "", success = VideoProcessService.resultMessage); loadCoins()
            } else {
                if (cost > 0) { repo.refundCoins(cost, "$billingReason failed", if (coinTypeUsed == "gold") "gold" else "silver"); loadCoins() }
                historyDao.insert(VideoHistoryEntity(fileName = state.videoFilename ?: "video.mp4", filePath = "", status = "failed", duration = state.videoDuration))
                state = state.copy(isProcessing = false, processStatus = "", error = VideoProcessService.resultMessage ?: "Failed")
            }
            loadHistory(); cleanup(ttsAudioPath, logoPath, bgMusicPath, srtPath); VideoProcessService.reset()
        }
    }

    /**
     * Generate TTS audio for full text:
     * 1. Split text into chunks (max ~800 chars at sentence boundaries)
     * 2. Generate TTS for each chunk via API
     * 3. Concatenate all chunk audio files
     * 4. Speed-match to video duration
     */
    private suspend fun generateFullTtsAudio(context: Context, text: String, voice: String, videoDurSec: Int): String? {
        val chunks = splitTextIntoChunks(text, 800)
        Log.d("TTS", "Text ${text.length} chars → ${chunks.size} chunks")

        val chunkFiles = mutableListOf<String>()
        for ((idx, chunk) in chunks.withIndex()) {
            state = state.copy(processStatus = "AI Voice ${idx + 1}/${chunks.size}...")
            val audioPath = generateSingleTtsChunk(context, chunk, voice)
            if (audioPath != null) {
                chunkFiles.add(audioPath)
            } else {
                Log.w("TTS", "Chunk $idx failed")
                chunkFiles.forEach { File(it).delete() }
                state = state.copy(processStatus = "⚠ TTS audio generate မရ")
                return null
            }
        }

        if (chunkFiles.isEmpty()) {
            state = state.copy(processStatus = "⚠ TTS audio generate မရ")
            return null
        }

        // Concatenate if multiple chunks
        val fullAudioPath: String
        if (chunkFiles.size == 1) {
            fullAudioPath = chunkFiles[0]
        } else {
            state = state.copy(processStatus = "TTS audio ပေါင်းနေသည်...")
            fullAudioPath = concatenateAudioFiles(context, chunkFiles) ?: run {
                chunkFiles.forEach { File(it).delete() }
                state = state.copy(processStatus = "⚠ Audio concat fail")
                return null
            }
            // Cleanup chunk files
            chunkFiles.forEach { File(it).delete() }
        }

        // Speed-match TTS audio to video duration
        if (videoDurSec > 0) {
            state = state.copy(processStatus = "Audio duration matching...")
            val matched = FFmpegProcessor.matchAudioToVideoDuration(fullAudioPath, videoDurSec, context)
            if (matched != null && matched != fullAudioPath) {
                File(fullAudioPath).delete()
                return matched
            }
        }
        return fullAudioPath
    }

    /** Generate TTS for a single chunk of text */
    private suspend fun generateSingleTtsChunk(context: Context, text: String, voice: String): String? {
        return when (val r = repo.geminiTts(text, voice)) {
            is Result.Success -> {
                if (r.data.audio_data == null) return null
                try {
                    val rawFile = File(context.cacheDir, "tts_raw_${System.currentTimeMillis()}.raw")
                    rawFile.writeBytes(android.util.Base64.decode(r.data.audio_data, android.util.Base64.DEFAULT))
                    val headerBytes = ByteArray(4); rawFile.inputStream().use { it.read(headerBytes) }
                    val isMP3 = (headerBytes[0] == 0xFF.toByte() && (headerBytes[1].toInt() and 0xE0) == 0xE0) || (headerBytes[0] == 'I'.code.toByte() && headerBytes[1] == 'D'.code.toByte())
                    val isWAV = String(headerBytes) == "RIFF"
                    when {
                        isMP3 -> { val f = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3"); rawFile.renameTo(f); f.absolutePath }
                        isWAV -> { val o = File(context.cacheDir, "tts_${System.currentTimeMillis()}.m4a"); com.arthenica.ffmpegkit.FFmpegKit.execute("-i ${rawFile.absolutePath} -c:a aac -b:a 128k -y ${o.absolutePath}"); rawFile.delete(); if (o.exists() && o.length() > 0) o.absolutePath else null }
                        else -> { val o = FFmpegProcessor.convertPcmToAac(rawFile.absolutePath, context); rawFile.delete(); o }
                    }
                } catch (e: Exception) { Log.e("TTS", "Decode: ${e.message}"); null }
            }
            is Result.Error -> { Log.e("TTS", "API: ${r.message}"); null }
        }
    }

    private fun normalizeTtsText(raw: String): String {
        val nfc = Normalizer.normalize(raw.replace('\u00A0', ' '), Normalizer.Form.NFC)
        return nfc
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), "")
            .replace(Regex("[\\u200B-\\u200D\\u2060\\uFEFF]"), "")
            .replace(Regex("[ \\t\\r\\n]+"), " ")
            .trim()
    }

    /** Split normalized text into chunks without cutting Burmese words or punctuation. */
    private fun splitTextIntoChunks(text: String, maxLen: Int): List<String> {
        val normalized = normalizeTtsText(text)
        if (normalized.isBlank()) return emptyList()
        if (normalized.length <= maxLen) return listOf(normalized)
        val chunks = mutableListOf<String>()
        var remaining = normalized
        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLen) { chunks += remaining.trim(); break }
            val window = remaining.substring(0, maxLen)
            val boundary = maxOf(window.lastIndexOf('။'), window.lastIndexOf('.'), window.lastIndexOf(' '))
            val cut = if (boundary >= maxLen / 3) boundary + 1 else maxLen
            val part = remaining.substring(0, cut).trim()
            if (part.isNotBlank()) chunks += part
            remaining = remaining.substring(cut).trim()
        }
        return chunks
    }

    /** Concatenate multiple audio files using FFmpeg concat */
    private suspend fun concatenateAudioFiles(context: Context, files: List<String>): String? {
        if (files.size == 1) return files[0]
        val listFile = File(context.cacheDir, "concat_${System.currentTimeMillis()}.txt")
        FileOutputStream(listFile).use { fos -> files.forEach { fos.write("file '$it'\n".toByteArray()) } }
        val out = File(context.cacheDir, "tts_full_${System.currentTimeMillis()}.m4a")
        val cmd = "-f concat -safe 0 -i ${listFile.absolutePath} -c:a aac -b:a 128k -y ${out.absolutePath}"
        val session = com.arthenica.ffmpegkit.FFmpegKit.execute(cmd)
        listFile.delete()
        return if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath else { out.delete(); null }
    }

    private suspend fun finishProcess(result: FFmpegProcessor.ProcessResult, cost: Int, coinType: String, billingReason: String) {
        if (result.success && result.outputPath != null) {
            historyDao.insert(VideoHistoryEntity(fileName = state.videoFilename ?: "video.mp4", filePath = result.outputPath, status = "completed", duration = state.videoDuration))
            state = state.copy(isProcessing = false, processStatus = "", success = "✅ ပြီးပါပြီ! (${result.durationMs / 1000}s)"); loadCoins()
        } else {
            if (cost > 0) { repo.refundCoins(cost, "$billingReason failed", if (coinType == "gold") "gold" else "silver"); loadCoins() }
            historyDao.insert(VideoHistoryEntity(fileName = state.videoFilename ?: "video.mp4", filePath = "", status = "failed", duration = state.videoDuration))
            state = state.copy(isProcessing = false, processStatus = "", error = result.error ?: "Failed")
        }; loadHistory()
    }

    private fun cleanup(vararg paths: String?) { paths.filterNotNull().forEach { try { File(it).delete() } catch (_: Exception) {} } }

    private suspend fun generateVoxCpmAudio(context: Context, accessToken: String): String? {
        val normalizedText = normalizeTtsText(state.aiText)
        if (normalizedText.isBlank()) {
            state = state.copy(error = "VoxCPM2 သို့ပို့ရန် စာသားမရှိပါ")
            return null
        }
        state = state.copy(aiText = normalizedText)
        val reference = state.voxcpmReferencePath?.let { File(it) }
        if (reference != null && (!reference.exists() || reference.length() == 0L)) {
            state = state.copy(error = "VoxCPM2 voice sample မတွေ့ပါ")
            return null
        }
        val chunks = splitTextIntoChunks(normalizedText, 650)
        val generated = mutableListOf<String>()
        try {
            chunks.forEachIndexed { index, chunk ->
                state = state.copy(processStatus = "VoxCPM generating ${index + 1}/${chunks.size}...")
                when (val result = VoxCpmClient.generate(
                    context = context,
                    text = chunk,
                    referenceAudio = reference ?: run {
                        state = state.copy(error = "VoxCPM2 voice sample ရွေးပါ")
                        return null
                    },
                    accessToken = accessToken,
                    styleControl = "Natural spoken Burmese narration, clear pronunciation and steady pacing.",
                    cfgValue = 2.0f,
                    inferenceTimesteps = 10,
                    idempotencyKey = "recap-${UUID.randomUUID()}-chunk-$index",
                )) {
                    is Result.Success -> generated += result.data.absolutePath
                    is Result.Error -> {
                                                    state = state.copy(error = "VoxCPM2: ${result.message}")

                        return null
                    }
                }
            }
            val combined = concatenateAudioFiles(context, generated)
            if (combined == null) state = state.copy(error = "VoxCPM audio ပေါင်းစည်းမရပါ")
            generated.filter { it != combined }.forEach { File(it).delete() }
            return combined
        } catch (e: Exception) {
            generated.forEach { File(it).delete() }
            state = state.copy(error = "VoxCPM2: ${e.message ?: "generation failed"}")
            return null
        }
    }

    private suspend fun generateEdgeTtsAudio(context: Context, text: String, voice: String): String? {
        val cfg = repo.getEdgeTtsConfig()
        val (key, region) = when (cfg) { is Result.Success -> cfg.data; is Result.Error -> { state = state.copy(processStatus = "", error = "Edge TTS: ${cfg.message}"); return null } }
        val r = repo.edgeTtsDirect(text, voice, key, region)
        return when (r) {
            is Result.Success -> r.data.absolutePath
            is Result.Error -> { state = state.copy(processStatus = "", error = "Edge TTS: ${r.message}"); null }
        }
    }

    private suspend fun generateSrtFromTts(context: Context, text: String, audioPath: String): String? {
        val chunks = splitTextIntoChunks(text, 200)
        val srtFile = File(context.cacheDir, "subtitles_${System.currentTimeMillis()}.srt")
        try {
            val audioDur = FFmpegProcessor.getAudioDuration(audioPath)
            if (audioDur <= 0.2) return null // unknown/zero duration → invalid SRT timestamps
            val chunkDur = audioDur / chunks.size.coerceAtLeast(1)
            srtFile.printWriter().use { pw ->
                chunks.forEachIndexed { i, chunk ->
                    val start = (i * chunkDur).coerceAtMost(audioDur - 0.1)
                    val end = ((i + 1) * chunkDur).coerceAtMost(audioDur)
                    val startStr = formatSrtTime(start); val endStr = formatSrtTime(end)
                    pw.println("${i + 1}"); pw.println("$startStr --> $endStr"); pw.println(chunk.trim()); pw.println()
                }
            }
        } catch (e: Exception) { return null }
        return if (srtFile.exists() && srtFile.length() > 0) srtFile.absolutePath else null
    }

    private fun formatSrtTime(sec: Double): String {
        val h = (sec / 3600).toInt(); val m = ((sec % 3600) / 60).toInt(); val s = (sec % 60).toInt(); val ms = ((sec - sec.toInt()) * 1000).toInt()
        return "%02d:%02d:%02d,%03d".format(h, m, s, ms)
    }

    // ═══ HISTORY ═══
    fun loadHistory() { viewModelScope.launch { historyDao.getAll().collect { state = state.copy(history = it) } } }
    fun toggleHistory() { state = state.copy(showHistory = !state.showHistory) }
    fun deleteHistoryItem(item: VideoHistoryEntity) { viewModelScope.launch { historyDao.delete(item) } }
    fun clearError() { state = state.copy(error = null) }
    fun clearSuccess() { state = state.copy(success = null) }
}
