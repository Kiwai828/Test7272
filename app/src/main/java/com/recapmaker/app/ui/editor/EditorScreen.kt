package com.recapmaker.app.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.recapmaker.app.ui.common.*

@Composable
fun EditorScreen(onBack: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val s = vm.state; val ctx = LocalContext.current
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.onVideoSelected(it, ctx) } }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.onLogoSelected(it) } }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400), initialOffset = { it / 4 }), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().background(DarkBg)) {
            Surface(color = CardBg, shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
                    Text("Video Editor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Purple, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.toggleHistory() }) { Icon(Icons.Default.History, null, tint = TextDim) }
                    CoinBadge(s.gold, s.silver)
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ═══ 1. VIDEO SOURCE ═══
                SectionCard("၁. Video ရွေးချယ်ရန်", Icons.Default.VideoFile, Purple) {
                    OutlinedButton(onClick = { videoPicker.launch("video/*") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (s.videoUri != null) Emerald else Purple.copy(0.3f))) {
                        Icon(if (s.videoUri != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload, null, tint = if (s.videoUri != null) Emerald else Purple)
                        Spacer(Modifier.width(8.dp)); Text(if (s.videoUri != null) "Video ရွေးပြီး" else "Device မှ ရွေးရန်", color = if (s.videoUri != null) Emerald else TextPrimary)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f), color = CardBorder); Text("  OR  ", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold); HorizontalDivider(Modifier.weight(1f), color = CardBorder) }
                    Text("YouTube / TikTok / Facebook / URL", color = TextDim, fontSize = 11.sp); Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(s.urlInput, { vm.updateUrl(it) }, Modifier.weight(1f), placeholder = { Text("URL paste...", fontSize = 13.sp) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple), shape = RoundedCornerShape(12.dp, 0.dp, 0.dp, 12.dp))
                        Button({ vm.checkUrlInfo(ctx) }, modifier = Modifier.height(56.dp), enabled = s.urlInput.isNotBlank() && !s.isDownloading && !s.isCheckingUrl, shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { if (s.isDownloading || s.isCheckingUrl) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Download, null) }
                    }
                    AnimatedVisibility(s.isDownloading) { Column(Modifier.padding(top = 8.dp)) { LinearProgressIndicator(progress = { s.downloadProgress }, Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Purple, trackColor = CardBorder); Text("Downloading... ${(s.downloadProgress * 100).toInt()}%", color = TextDim, fontSize = 11.sp) } }
                    if (s.videoUri != null) { Spacer(Modifier.height(8.dp)); VideoPreviewPlayer(uri = s.videoUri!!) { cs -> vm.updatePreviewSize(cs.width, cs.height); if (s.blurEnabled) s.blurAreas.forEachIndexed { i, _ -> DraggableBox(cs, .1f+i*.08f, .1f+i*.08f, .3f, .2f, Rose, "Blur ${i+1}", { vm.removeBlurBox(i) }) { vm.updateBlurBox(i, it) } }; if (s.logoUri != null) DraggableBox(cs, .05f, .05f, .15f, .15f, Emerald, "Logo") { vm.updateLogoArea(it) } }; if (s.videoDuration > 0) Text("Duration: ${s.videoDuration/60}:${"%02d".format(s.videoDuration%60)} • ${s.videoWidth}x${s.videoHeight}", color = Emerald, fontSize = 11.sp) }
                }

                // ═══ 2. EFFECTS ═══
                SectionCard("၂. Effect များ", Icons.Default.Tune, Purple) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EffectToggle("Video ကိုလှန်မည်", Icons.Default.SwapHoriz, s.flipEnabled) { vm.toggleFlip(it) }
                        EffectToggle("Speed 1.05x", Icons.Default.Speed, s.speedEnabled) { vm.toggleSpeed(it) }
                        EffectToggle("အသံပြောင်းမည်", Icons.Default.MusicNote, s.pitchEnabled) { vm.togglePitch(it) }
                        EffectToggle("Blur နေရာဝှက်", Icons.Default.BlurOn, s.blurEnabled, switchColor = Rose) { vm.toggleBlur(it) }
                    }
                    AnimatedVisibility(s.blurEnabled) { Column(Modifier.padding(top = 8.dp)) { Text("↑ Preview ပေါ်တွင် Blur box ဆွဲရွှေ့ပါ", color = Rose.copy(.7f), fontSize = 11.sp); OutlinedButton({ vm.addBlurBox() }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Rose.copy(.3f))) { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = Rose); Text(" Box (${s.blurAreas.size})", fontSize = 12.sp, color = Rose) } } }
                }

                // ═══ 3. WATERMARK + ADVANCED ═══
                SectionCard("စာတန်း Watermark", Icons.Default.TextFields, Purple) {
                    OutlinedTextField(s.wmText, { vm.setWmText(it) }, Modifier.fillMaxWidth(), placeholder = { Text("Channel အမည်", fontSize = 13.sp) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple), shape = RoundedCornerShape(12.dp))
                    AnimatedVisibility(s.wmText.isNotBlank()) {
                        var expanded by remember { mutableStateOf(false) }
                        Column(Modifier.padding(top = 6.dp)) {
                            TextButton({ expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp), tint = Purple); Text(" အသေးစိတ်", fontSize = 12.sp, color = Purple) }
                            AnimatedVisibility(expanded) {
                                Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Size: ${s.wmSize}", color = TextDim, fontSize = 12.sp)
                                        Slider(s.wmSize.toFloat(), { vm.setWmSize(it.toInt()) }, valueRange = 12f..72f, colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))
                                        Text("အရောင်", color = TextDim, fontSize = 12.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("#FFFFFF","#FF0000","#00FF00","#0000FF","#FFFF00","#FF00FF","#00FFFF","#000000").forEach { hex -> val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }; Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(c).border(2.dp, if (s.wmColor.equals(hex, true)) Purple else Color.Transparent, RoundedCornerShape(6.dp)).clickable { vm.setWmColor(hex) }) } }
                                        Text("နေရာ", color = TextDim, fontSize = 12.sp)
                                        PositionSelector(s.wmPosition) { vm.setWmPosition(it) }
                                        EffectToggle("ရွေ့လျား Scroll", Icons.Default.SwapHorizontalCircle, s.wmScroll) { vm.setWmScroll(it) }
                                        EffectToggle("နောက်ခံ Box", Icons.Default.CheckBoxOutlineBlank, s.wmBox) { vm.setWmBox(it) }
                                        AnimatedVisibility(s.wmBox) { Column { Text("Box Opacity: ${"%.1f".format(s.wmBoxOpacity)}", color = TextDim, fontSize = 12.sp); Slider(s.wmBoxOpacity, { vm.setWmBoxOpacity(it) }, valueRange = 0.1f..1f, colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple)) } }
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══ 4. LOGO ═══
                SectionCard("Logo", Icons.Default.BrandingWatermark, Emerald) {
                    OutlinedButton({ logoPicker.launch("image/*") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (s.logoUri != null) Emerald else CardBorder)) {
                        Icon(if (s.logoUri != null) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate, null, tint = if (s.logoUri != null) Emerald else TextDim)
                        Spacer(Modifier.width(8.dp)); Text(if (s.logoUri != null) "Logo ရွေးပြီး — preview ဆွဲရွှေ့ပါ" else "Logo ရွေးရန်", color = if (s.logoUri != null) Emerald else TextDim, fontSize = 13.sp)
                    }
                    if (s.logoUri != null) TextButton({ vm.removeLogo() }) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp), tint = ErrorRed); Text(" ဖယ်ရှား", color = ErrorRed, fontSize = 12.sp) }
                }

                // ═══ 5. AI VOICE ═══
                SectionCard("AI အသံထပ်ခြင်း", Icons.Default.RecordVoiceOver, Purple) {
                    OutlinedTextField(s.aiText, { vm.setAiText(it) }, Modifier.fillMaxWidth().heightIn(min = 80.dp), placeholder = { Text("AI Script...", fontSize = 13.sp) }, maxLines = 6, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ vm.analyzeScript(ctx) }, enabled = !s.isAnalyzing && s.videoLocalPath != null, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Emerald.copy(.3f)), modifier = Modifier.weight(1f)) { if (s.isAnalyzing && s.processStatus.isNotBlank()) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Emerald, strokeWidth = 2.dp) else Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(14.dp), tint = Emerald); Spacer(Modifier.width(4.dp)); Text("Auto Analyze", fontSize = 11.sp, color = Emerald) }
                        OutlinedButton({ vm.translateScript() }, enabled = !s.isAnalyzing && s.aiText.isNotBlank(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Purple.copy(.3f)), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Translate, null, modifier = Modifier.size(14.dp), tint = Purple); Spacer(Modifier.width(4.dp)); Text("ဘာသာပြန်", fontSize = 11.sp, color = Purple) }
                    }
                    AnimatedVisibility(s.isAnalyzing && s.processStatus.isNotBlank()) { Text(s.processStatus, color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp), tint = Purple); Spacer(Modifier.width(6.dp)); Text("Voice", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary); if (s.selectedVoice.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Surface(color = Purple.copy(.15f), shape = RoundedCornerShape(10.dp)) { Text(s.selectedVoice, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) } } }
                    Spacer(Modifier.height(6.dp))
                    VoiceTabRow(listOf("google" to "🔷 Google PREMIUM", "microsoft" to "🟢 Microsoft FREE"), s.voiceTab) { vm.switchVoiceTab(it) }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(s.voiceSearch, { vm.setVoiceSearch(it) }, Modifier.fillMaxWidth(), placeholder = { Text("Search...", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp), tint = TextDim) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple.copy(.3f), unfocusedBorderColor = Purple.copy(.09f), cursorColor = Purple), shape = RoundedCornerShape(11.dp))
                    Spacer(Modifier.height(6.dp))
                    val voices = vm.filteredVoices
                    if (voices.isEmpty()) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Text("Not found", color = TextDim) }
                    else LazyVerticalGrid(columns = GridCells.Fixed(2), Modifier.fillMaxWidth().heightIn(max = 260.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { items(voices, key = { it.name }) { v -> VoiceCard(v.name, v.label, v.gender, s.selectedVoice == v.name, { vm.selectVoice(v.name) }) } }
                    if (s.voiceTab == "google" && s.aiText.isNotBlank()) { Spacer(Modifier.height(4.dp)); Surface(color = WarningYellow.copy(.08f), shape = RoundedCornerShape(8.dp)) { Text("⚠ Google Voice → Gold Coins", color = WarningYellow, fontSize = 11.sp, modifier = Modifier.padding(8.dp)) } }
                    if (s.voiceTab == "microsoft" && s.edgeTtsAvailable) { Spacer(Modifier.height(4.dp)); Surface(color = Cyan.copy(.08f), shape = RoundedCornerShape(8.dp)) { Text("🔷 Edge TTS Local Mode", color = Cyan, fontSize = 11.sp, modifier = Modifier.padding(8.dp)) } }
                }

                // ═══ 5-A. VIDEO EFFECTS ═══
                val ve = s.videoEffects
                SectionCard("Video Effects", Icons.Default.Tune, Purple) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectToggle("Grayscale", Icons.Default.BlurOn, ve.grayscale, Rose) { vm.setVideoEffectGrayscale(it) }
                        EffectToggle("Sepia", Icons.Default.BlurOn, ve.sepia, Gold) { vm.setVideoEffectSepia(it) }
                        EffectToggle("Vignette", Icons.Default.BlurOn, ve.vignette, Purple) { vm.setVideoEffectVignette(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Brightness", color = TextDim, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                        Slider(ve.brightness, { vm.setVideoEffectBrightness(it) }, valueRange = 0.5f..1.5f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))
                        Text("${"%.1f".format(ve.brightness)}", color = TextMid, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Contrast", color = TextDim, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                        Slider(ve.contrast, { vm.setVideoEffectContrast(it) }, valueRange = 0.5f..2.0f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))
                        Text("${"%.1f".format(ve.contrast)}", color = TextMid, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                    }
                }

                // ═══ 5-B. BACKGROUND MUSIC ═══
                val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.setBgMusicUri(it) } }
                SectionCard("Background Music", Icons.Default.MusicNote, Emerald) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton({ musicPicker.launch("audio/*") }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Emerald.copy(.3f))) {
                            Icon(if (s.bgMusicUri != null) Icons.Default.CheckCircle else Icons.Default.AudioFile, null, tint = if (s.bgMusicUri != null) Emerald else TextDim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text(if (s.bgMusicUri != null) "Music added" else "Add music", fontSize = 12.sp, color = if (s.bgMusicUri != null) Emerald else TextDim)
                        }
                        if (s.bgMusicUri != null) { Spacer(Modifier.width(8.dp)); IconButton(onClick = { vm.setBgMusicUri(null) }) { Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(18.dp)) } }
                    }
                    if (s.bgMusicUri != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Volume", color = TextDim, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                            Slider(s.bgMusicVolume, { vm.setBgMusicVolume(it) }, valueRange = 0f..1f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Emerald, activeTrackColor = Emerald))
                            Text("${(s.bgMusicVolume * 100).toInt()}%", color = TextMid, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                        }
                        EffectToggle("Auto-Duck (lower when speaking)", Icons.Default.VolumeDown, s.autoDuck, Emerald) { vm.setAutoDuck(it) }
                    }
                }

                // ═══ 5-C. AUDIO EFFECTS ═══
                val ae = s.audioEffects
                SectionCard("Audio Effects", Icons.Default.GraphicEq, Purple) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectToggle("Echo", Icons.Default.GraphicEq, ae.echo, Cyan) { vm.setAudioEffectEcho(it) }
                        EffectToggle("Reverb", Icons.Default.GraphicEq, ae.reverb, Rose) { vm.setAudioEffectReverb(it) }
                        EffectToggle("Bass Boost", Icons.Default.GraphicEq, ae.bassBoost, Gold) { vm.setAudioEffectBassBoost(it) }
                    }
                    AnimatedVisibility(ae.echo) {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Delay", color = TextDim, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                                Slider(ae.echoDelay, { vm.setEchoDelay(it) }, valueRange = 10f..200f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
                                Text("${ae.echoDelay.toInt()}ms", color = TextMid, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Decay", color = TextDim, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                                Slider(ae.echoDecay, { vm.setEchoDecay(it) }, valueRange = 0.1f..0.9f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
                                Text("${"%.1f".format(ae.echoDecay)}", color = TextMid, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                            }
                        }
                    }
                    AnimatedVisibility(ae.reverb) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Amount", color = TextDim, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                            Slider(ae.reverbAmount, { vm.setReverbAmount(it) }, valueRange = 0.1f..0.8f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Rose, activeTrackColor = Rose))
                            Text("${"%.1f".format(ae.reverbAmount)}", color = TextMid, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                        }
                    }
                    AnimatedVisibility(ae.bassBoost) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Gain", color = TextDim, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                            Slider(ae.bassAmount, { vm.setBassAmount(it) }, valueRange = 1f..10f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
                            Text("${ae.bassAmount.toInt()}dB", color = TextMid, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                        }
                    }
                }

                // ═══ 5-D. MULTI-CLIP ═══
                SectionCard("Multi-Clip Joiner", Icons.Default.VideoLibrary, Emerald) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton({ val picker = androidx.activity.compose.ActivityResultRegistryPickVisualMedia(); picker.launch("video/*") { it?.let { vm.addExtraClip(it.toString()) } } }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Emerald.copy(.3f))) {
                            Icon(Icons.Default.Add, null, tint = Emerald, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Add clip (${s.extraClips.size})", fontSize = 12.sp, color = Emerald)
                        }
                    }
                    if (s.extraClips.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Clips will be joined with fade transitions", color = TextDim, fontSize = 11.sp)
                        s.extraClips.forEachIndexed { i, path ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("Clip ${i + 1}: ${File(path).name}", color = TextMid, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { vm.removeExtraClip(path) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = ErrorRed, modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                }

                // ═══ 5-E. SUBTITLES ═══
                SectionCard("SRT Subtitles", Icons.Default.Subtitles, Purple) {
                    EffectToggle("Generate SRT from TTS", Icons.Default.Subtitles, s.subtitleEnabled, Purple) { vm.setSubtitleEnabled(it) }
                    AnimatedVisibility(s.subtitleEnabled) {
                        OutlinedTextField(s.subtitleText, { vm.setSubtitleText(it) }, Modifier.fillMaxWidth(), placeholder = { Text("SRT content or auto-generate from TTS", fontSize = 12.sp) }, maxLines = 6, shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple))
                    }
                }

                // ═══ 6. PROCESS ═══
                if (s.isProcessing) { Surface(color = Purple.copy(.08f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Purple.copy(.2f))) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Purple, strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text(s.processStatus.ifBlank { "Processing..." }, color = TextPrimary, fontSize = 13.sp) } } }
                PrimaryButton(text = if (s.isProcessing) "Processing..." else "စတင်ပြုပြင်မည် ${vm.costText}", onClick = { vm.startProcessing(ctx) }, loading = s.isProcessing, color = Emerald, enabled = s.videoLocalPath != null)
                Spacer(Modifier.height(24.dp))
            }
        }

        // Snackbars
        s.error?.let { m -> Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = ErrorRed.copy(.9f), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(14.dp)) { Text(m, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)); IconButton({ vm.clearError() }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = Color.White) } } }; LaunchedEffect(m) { kotlinx.coroutines.delay(6000); vm.clearError() } }
        s.success?.let { m -> Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = Emerald.copy(.9f), shape = RoundedCornerShape(12.dp)) { Text(m, color = DarkBg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp)) }; LaunchedEffect(m) { kotlinx.coroutines.delay(5000); vm.clearSuccess() } }
    }

    // ═══ RESOLUTION POPUP ═══
    if (s.showResolutionPopup && s.videoInfo != null) {
        Dialog(onDismissRequest = { vm.dismissResolutionPopup() }) {
            Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoFile, null, modifier = Modifier.size(28.dp), tint = Purple)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.videoInfo!!.title.take(60), fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp, maxLines = 2)
                            if (s.videoInfo!!.duration > 0) Text("${s.videoInfo!!.duration/60}:${"%02d".format(s.videoInfo!!.duration%60)}", color = TextDim, fontSize = 12.sp)
                        }
                        IconButton(onClick = { vm.dismissResolutionPopup() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = TextDim) }
                    }
                    Spacer(Modifier.height(12.dp)); Text("Resolution ရွေးပါ", color = TextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(s.videoInfo!!.formats) { f ->
                            Surface(onClick = { vm.downloadWithFormat(ctx, f) }, color = if (f.formatId == "best") Purple.copy(.15f) else SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (f.formatId == "best") Purple.copy(.3f) else CardBorder)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (f.formatId == "best") Icons.Default.AutoAwesome else Icons.Default.HighQuality, null, modifier = Modifier.size(20.dp), tint = if (f.formatId == "best") Purple else Emerald)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(f.resolution, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                                        if (f.note.isNotBlank()) Text(f.note, color = TextDim, fontSize = 11.sp)
                                    }
                                    if (f.fileSize > 0) Text("${"%.1f".format(f.fileSize/(1024.0*1024.0))}MB", color = TextDim, fontSize = 11.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp), tint = TextDim)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ═══ HISTORY ═══
    if (s.showHistory) {
        Dialog(onDismissRequest = { vm.toggleHistory() }) {
            Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 ပြုလုပ်ပြီး Video များ", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.toggleHistory() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = TextDim) }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (s.history.isEmpty()) Text("မှတ်တမ်းမရှိပါ", color = TextDim, modifier = Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.history) { item ->
                            Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.fileName, color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                                        val sc = if (item.status == "completed") Emerald else ErrorRed
                                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(sc)); Spacer(Modifier.width(6.dp)); Text(item.status, color = sc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                                    }
                                    IconButton(onClick = { vm.deleteHistoryItem(item) }) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = ErrorRed.copy(.7f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
