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

    Box(Modifier.fillMaxSize()) {
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
                    // File picker
                    OutlinedButton(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (s.videoUri != null) Emerald else Purple.copy(0.3f))) {
                        Icon(if (s.videoUri != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload, null, tint = if (s.videoUri != null) Emerald else Purple)
                        Spacer(Modifier.width(8.dp)); Text(if (s.videoUri != null) "Video ရွေးပြီးပါပြီ" else "Device မှ Video ရွေးရန်", color = if (s.videoUri != null) Emerald else TextPrimary)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = CardBorder); Text("  OR  ", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold); HorizontalDivider(Modifier.weight(1f), color = CardBorder)
                    }
                    // URL download
                    Text("Direct video URL (mp4 link) ထည့်ပါ", color = TextDim, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = s.urlInput, onValueChange = { vm.updateUrl(it) }, modifier = Modifier.weight(1f),
                            placeholder = { Text("https://...video.mp4", fontSize = 13.sp) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                            shape = RoundedCornerShape(12.dp, 0.dp, 0.dp, 12.dp))
                        Button(onClick = { vm.checkUrlInfo() }, enabled = s.urlInput.isNotBlank() && !s.isDownloading && !s.isCheckingUrl,
                            shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp), modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                            if (s.isDownloading || s.isCheckingUrl) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Download, null)
                        }
                    }
                    // Download progress
                    AnimatedVisibility(s.isDownloading) {
                        Column(Modifier.padding(top = 8.dp)) {
                            LinearProgressIndicator(progress = { s.downloadProgress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = Purple, trackColor = CardBorder)
                            Text("Downloading... ${(s.downloadProgress * 100).toInt()}%", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    // ── VIDEO PREVIEW with drag boxes ──
                    if (s.videoUri != null) {
                        Spacer(Modifier.height(8.dp))
                        VideoPreviewPlayer(uri = s.videoUri!!) { containerSize ->
                            if (s.blurEnabled) s.blurAreas.forEachIndexed { i, _ ->
                                DraggableBox(containerSize, 0.1f + i * 0.08f, 0.1f + i * 0.08f, 0.3f, 0.2f, Rose, "Blur ${i+1}", { vm.removeBlurBox(i) }) { vm.updateBlurBox(i, it) }
                            }
                            if (s.logoUri != null) DraggableBox(containerSize, 0.05f, 0.05f, 0.15f, 0.15f, Emerald, "Logo") { vm.updateLogoArea(it) }
                        }
                        if (s.videoDuration > 0) Text("Duration: ${s.videoDuration / 60}:${"%02d".format(s.videoDuration % 60)}", color = Emerald, fontSize = 11.sp)
                    }
                }

                // ═══ 2. EFFECTS ═══
                SectionCard("၂. Effect များ", Icons.Default.Tune, Purple) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EffectToggle("Video ကိုလှန်မည်", Icons.Default.SwapHoriz, s.flipEnabled) { vm.toggleFlip(it) }
                        EffectToggle("Speed 1.05x", Icons.Default.Speed, s.speedEnabled) { vm.toggleSpeed(it) }
                        EffectToggle("အသံပြောင်းမည်", Icons.Default.MusicNote, s.pitchEnabled) { vm.togglePitch(it) }
                        EffectToggle("Noise/Grain", Icons.Default.Grain, s.noiseEnabled) { vm.toggleNoise(it) }
                        EffectToggle("Blur နေရာဝှက်", Icons.Default.BlurOn, s.blurEnabled, switchColor = Rose) { vm.toggleBlur(it) }
                    }
                    AnimatedVisibility(s.blurEnabled) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text("↑ Video preview ပေါ်တွင် Blur box ဆွဲရွှေ့ပါ", color = Rose.copy(0.7f), fontSize = 11.sp)
                            OutlinedButton(onClick = { vm.addBlurBox() }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Rose.copy(0.3f))) {
                                Icon(Icons.Default.Add, null, tint = Rose, modifier = Modifier.size(14.dp)); Text(" Box ထပ်ထည့် (${s.blurAreas.size})", fontSize = 12.sp, color = Rose)
                            }
                        }
                    }
                }

                // ═══ 3. WATERMARK ═══
                SectionCard("စာတန်း Watermark", Icons.Default.TextFields, Purple) {
                    OutlinedTextField(value = s.wmText, onValueChange = { vm.setWmText(it) }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Channel အမည်", fontSize = 13.sp) }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple), shape = RoundedCornerShape(12.dp))
                    AnimatedVisibility(s.wmText.isNotBlank()) {
                        var exp by remember { mutableStateOf(false) }
                        Column(Modifier.padding(top = 6.dp)) {
                            TextButton(onClick = { exp = !exp }) { Icon(if (exp) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Purple, modifier = Modifier.size(16.dp)); Text(" အသေးစိတ်", fontSize = 12.sp, color = Purple) }
                            AnimatedVisibility(exp) {
                                Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Size: ${s.wmSize}", color = TextDim, fontSize = 12.sp)
                                        Slider(value = s.wmSize.toFloat(), onValueChange = { vm.setWmSize(it.toInt()) }, valueRange = 12f..72f, colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))
                                        Text("အရောင်", color = TextDim, fontSize = 12.sp); ColorRow(s.wmColor) { vm.setWmColor(it) }
                                        Text("နေရာ", color = TextDim, fontSize = 12.sp); PositionSelector(s.wmPosition) { vm.setWmPosition(it) }
                                        EffectToggle("ရွေ့လျား", Icons.Default.SwapHorizontalCircle, s.wmScroll) { vm.setWmScroll(it) }
                                        EffectToggle("နောက်ခံဘောင်", Icons.Default.CheckBoxOutlineBlank, s.wmBox) { vm.setWmBox(it) }
                                        AnimatedVisibility(s.wmBox) { Column { Text("Opacity: ${"%.1f".format(s.wmBoxOpacity)}", color = TextDim, fontSize = 12.sp); Slider(value = s.wmBoxOpacity, onValueChange = { vm.setWmBoxOpacity(it) }, valueRange = 0.1f..1f, colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple)) } }
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══ 4. LOGO ═══
                SectionCard("Logo", Icons.Default.BrandingWatermark, Emerald) {
                    OutlinedButton(onClick = { logoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (s.logoUri != null) Emerald else CardBorder)) {
                        Icon(if (s.logoUri != null) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate, null, tint = if (s.logoUri != null) Emerald else TextDim)
                        Spacer(Modifier.width(8.dp)); Text(if (s.logoUri != null) "Logo ရွေးပြီး — preview ပေါ်ဆွဲရွှေ့ပါ" else "Logo ရွေးရန်", color = if (s.logoUri != null) Emerald else TextDim, fontSize = 13.sp)
                    }
                    if (s.logoUri != null) TextButton(onClick = { vm.removeLogo() }) { Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(14.dp)); Text(" ဖယ်ရှား", color = ErrorRed, fontSize = 12.sp) }
                }

                // ═══ 5. AI VOICE ═══
                SectionCard("AI အသံထပ်ခြင်း", Icons.Default.RecordVoiceOver, Purple) {
                    OutlinedTextField(value = s.aiText, onValueChange = { vm.setAiText(it) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        placeholder = { Text("AI Script...", fontSize = 13.sp) }, maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple), shape = RoundedCornerShape(12.dp))
                    // Two AI buttons: Analyze (auto from video) + Translate (manual text)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Auto Analyze: extract audio → STT → translate
                        OutlinedButton(
                            onClick = { vm.analyzeScript(ctx) },
                            enabled = !s.isAnalyzing && s.videoLocalPath != null,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Emerald.copy(0.3f)),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (s.isAnalyzing && s.processStatus.isNotBlank()) CircularProgressIndicator(Modifier.size(14.dp), Emerald, strokeWidth = 2.dp)
                            else Icon(Icons.Default.GraphicEq, null, tint = Emerald, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp)); Text("Auto Analyze", fontSize = 11.sp, color = Emerald)
                        }
                        // Manual Translate: user typed text → Gemini translate
                        OutlinedButton(
                            onClick = { vm.translateScript() },
                            enabled = !s.isAnalyzing && s.aiText.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Purple.copy(0.3f)),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (s.isAnalyzing && s.processStatus.isBlank()) CircularProgressIndicator(Modifier.size(14.dp), Purple, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Translate, null, tint = Purple, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp)); Text("ဘာသာပြန်", fontSize = 11.sp, color = Purple)
                        }
                    }
                    // Analyze status
                    AnimatedVisibility(s.isAnalyzing && s.processStatus.isNotBlank()) {
                        Text(s.processStatus, color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Mic, null, tint = Purple, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Voice", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                        if (s.selectedVoice.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Surface(color = Purple.copy(0.15f), shape = RoundedCornerShape(10.dp)) { Text(s.selectedVoice, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) } } }
                    Spacer(Modifier.height(6.dp))
                    VoiceTabRow(listOf("google" to "🔷 Google PREMIUM", "microsoft" to "🟢 Microsoft FREE"), s.voiceTab) { vm.switchVoiceTab(it) }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = s.voiceSearch, onValueChange = { vm.setVoiceSearch(it) }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search...", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = TextDim, modifier = Modifier.size(16.dp)) }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple.copy(0.3f), unfocusedBorderColor = Purple.copy(0.09f), cursorColor = Purple), shape = RoundedCornerShape(11.dp))
                    Spacer(Modifier.height(6.dp))
                    val voices = vm.filteredVoices
                    if (voices.isEmpty()) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Text("Not found", color = TextDim) }
                    else LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { items(voices, key = { it.name }) { v -> VoiceCard(v.name, v.label, v.gender, s.selectedVoice == v.name, { vm.selectVoice(v.name) }) } }
                    if (s.voiceTab == "google" && s.aiText.isNotBlank()) { Spacer(Modifier.height(4.dp)); Surface(color = WarningYellow.copy(0.08f), shape = RoundedCornerShape(8.dp)) { Text("⚠ Google Voice → Gold Coins only", color = WarningYellow, fontSize = 11.sp, modifier = Modifier.padding(8.dp)) } }
                }

                // ═══ 6. PROCESS ═══
                if (s.isProcessing) {
                    Surface(color = Purple.copy(0.08f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Purple.copy(0.2f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), Purple, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(s.processStatus.ifBlank { "Processing..." }, color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
                PrimaryButton(text = if (s.isProcessing) "Processing..." else "စတင်ပြုပြင်မည် ${vm.costText}", onClick = { vm.startProcessing(ctx) },
                    loading = s.isProcessing, color = Emerald, enabled = s.videoLocalPath != null)
                Spacer(Modifier.height(24.dp))
            }
        }

        // Snackbars
        s.error?.let { msg -> Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = ErrorRed.copy(0.9f), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(14.dp)) { Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { vm.clearError() }) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp)) } } }; LaunchedEffect(msg) { kotlinx.coroutines.delay(5000); vm.clearError() } }
        s.success?.let { msg -> Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = Emerald.copy(0.9f), shape = RoundedCornerShape(12.dp)) { Text(msg, color = DarkBg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp)) }; LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); vm.clearSuccess() } }
    }

    // ═══ HISTORY DIALOG ═══
    if (s.showHistory) {
        Dialog(onDismissRequest = { vm.toggleHistory() }) {
            Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 ပြုလုပ်ပြီး Video များ", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.toggleHistory() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, tint = TextDim, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (s.history.isEmpty()) Text("မှတ်တမ်းမရှိပါ", color = TextDim, modifier = Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.history) { item ->
                            Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.fileName, color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val sc = if (item.status == "completed") Emerald else ErrorRed
                                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(sc))
                                            Spacer(Modifier.width(6.dp)); Text(item.status, color = sc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        if (item.duration > 0) Text("${item.duration}s • ${item.fileSize / 1024}KB", color = TextDim, fontSize = 10.sp)
                                    }
                                    IconButton(onClick = { vm.deleteHistoryItem(item) }) { Icon(Icons.Default.Delete, null, tint = ErrorRed.copy(0.7f), modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ═══ DOWNLOAD INFO POPUP ═══
    if (s.showDownloadPopup && s.urlInfo != null) {
        Dialog(onDismissRequest = { vm.dismissDownloadPopup() }) {
            Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Download, null, tint = Purple, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Video Download", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 17.sp)
                    Spacer(Modifier.height(12.dp))
                    Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row { Text("Type: ", color = TextDim, fontSize = 12.sp); Text(s.urlInfo!!.contentType.ifBlank { "video" }, color = TextPrimary, fontSize = 12.sp) }
                            Row { Text("Size: ", color = TextDim, fontSize = 12.sp); Text(s.urlInfo!!.fileSizeText, color = Emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            if (!s.urlInfo!!.isVideo) { Spacer(Modifier.height(4.dp)); Text("⚠ Video file မဟုတ်နိုင်ပါ", color = WarningYellow, fontSize = 11.sp) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.dismissDownloadPopup() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Cancel", color = TextDim) }
                        Button(onClick = { vm.confirmDownload(ctx) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Emerald)) { Text("Download", color = DarkBg, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorRow(selected: String, onSelect: (String) -> Unit) {
    val colors = listOf("#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#000000")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { colors.forEach { hex ->
        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(c).border(2.dp, if (selected.equals(hex, true)) Purple else Color.Transparent, RoundedCornerShape(6.dp)).clickable { onSelect(hex) })
    } }
}
