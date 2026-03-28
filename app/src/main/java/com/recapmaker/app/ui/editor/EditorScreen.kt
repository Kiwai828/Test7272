package com.recapmaker.app.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.recapmaker.app.data.model.*
import com.recapmaker.app.ui.common.*

@Composable
fun EditorScreen(onBack: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val s = vm.state
    val context = LocalContext.current
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.onVideoSelected(it, context) } }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.onLogoSelected(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(DarkBg)) {
            // ── Top Bar ──
            Surface(color = CardBg, shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
                    Text("Video Editor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Purple, modifier = Modifier.weight(1f))
                    CoinBadge(s.gold, s.silver)
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ═══ 1. VIDEO SOURCE ═══
                SectionCard("၁. မူရင်း Video File", Icons.Default.VideoFile, Purple) {
                    OutlinedButton(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (s.videoUri != null) Emerald else Purple.copy(0.3f))) {
                        Icon(if (s.videoUri != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload, null, tint = if (s.videoUri != null) Emerald else Purple)
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.videoUri != null) "Video ရွေးပြီးပါပြီ" else "Video File တင်ရန်", color = if (s.videoUri != null) Emerald else TextPrimary)
                    }

                    // OR
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = CardBorder); Text("  OR  ", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold); HorizontalDivider(Modifier.weight(1f), color = CardBorder)
                    }

                    // URL input
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = s.urlInput, onValueChange = { vm.updateUrl(it) }, modifier = Modifier.weight(1f),
                            placeholder = { Text("YouTube, TikTok, FB Link", fontSize = 13.sp) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                            shape = RoundedCornerShape(12.dp, 0.dp, 0.dp, 12.dp))
                        Button(onClick = { vm.downloadFromUrl() }, enabled = s.urlInput.isNotBlank() && !s.isUploading,
                            shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp), modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                            if (s.isUploading) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Link, null)
                        }
                    }

                    // Video preview
                    if (s.videoUri != null) {
                        Spacer(Modifier.height(8.dp))
                        VideoPreview(s.videoUri!!)
                        if (s.videoDuration > 0) {
                            Text("Duration: ${s.videoDuration / 60}:${"%02d".format(s.videoDuration % 60)}", color = Emerald, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    } else if (s.videoFilename != null) {
                        Spacer(Modifier.height(4.dp))
                        Surface(color = Emerald.copy(0.08f), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(12.dp)) {
                                Icon(Icons.Default.Movie, null, tint = Emerald, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(s.videoFilename ?: "", color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }

                // ═══ 2. BYPASS EFFECTS ═══
                SectionCard("၂. ပြုပြင်မည့် Effect များ", Icons.Default.Tune, Purple) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EffectToggle("Video ကိုလှန်မည်", Icons.Default.SwapHoriz, s.flipEnabled) { vm.toggleFlip(it) }
                        EffectToggle("Speed မြန်မည် (1.05x)", Icons.Default.Speed, s.speedEnabled) { vm.toggleSpeed(it) }
                        EffectToggle("အသံပြောင်းမည် (Copyright)", Icons.Default.MusicNote, s.pitchEnabled) { vm.togglePitch(it) }
                        EffectToggle("Noise/Grain ထည့်မည်", Icons.Default.Grain, s.noiseEnabled) { vm.toggleNoise(it) }
                        EffectToggle("နေရာဝှက် (Blur)", Icons.Default.BlurOn, s.blurEnabled, { vm.toggleBlur(it) }, switchColor = Rose)
                    }
                    AnimatedVisibility(s.blurEnabled) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text("Blur Box အရေအတွက်", color = TextDim, fontSize = 12.sp)
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (1..4).forEach { c -> FilterChip(selected = s.blurBoxCount == c, onClick = { vm.setBlurCount(c) }, label = { Text("$c") },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Rose.copy(0.15f), selectedLabelColor = Rose)) }
                            }
                        }
                    }
                }

                // ═══ 3. TEXT WATERMARK ═══
                SectionCard("စာတန်း Watermark", Icons.Default.TextFields, Purple) {
                    OutlinedTextField(value = s.wmText, onValueChange = { vm.setWmText(it) }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("ဥပမာ: Channel အမည်", fontSize = 13.sp) }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                        shape = RoundedCornerShape(12.dp))
                    AnimatedVisibility(s.wmText.isNotBlank()) {
                        var expanded by remember { mutableStateOf(false) }
                        Column(Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { expanded = !expanded }) {
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Purple, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Watermark အသေးစိတ်", fontSize = 12.sp, color = Purple)
                            }
                            AnimatedVisibility(expanded) {
                                Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("စာလုံး Size: ${s.wmSize}", color = TextDim, fontSize = 12.sp)
                                        Slider(value = s.wmSize.toFloat(), onValueChange = { vm.setWmSize(it.toInt()) }, valueRange = 12f..72f,
                                            colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))
                                        Text("စာလုံးအရောင်", color = TextDim, fontSize = 12.sp)
                                        ColorRow(s.wmColor) { vm.setWmColor(it) }
                                        Text("နေရာ", color = TextDim, fontSize = 12.sp)
                                        PositionSelector(s.wmPosition) { vm.setWmPosition(it) }
                                        EffectToggle("စာတန်းကို ရွေ့လျားစေမည်", Icons.Default.SwapHorizontalCircle, s.wmScroll) { vm.setWmScroll(it) }
                                        EffectToggle("နောက်ခံဘောင် ထည့်မည်", Icons.Default.CheckBoxOutlineBlank, s.wmBox) { vm.setWmBox(it) }
                                        AnimatedVisibility(s.wmBox) {
                                            Column {
                                                Text("ဘောင် Opacity: ${"%.1f".format(s.wmBoxOpacity)}", color = TextDim, fontSize = 12.sp)
                                                Slider(value = s.wmBoxOpacity, onValueChange = { vm.setWmBoxOpacity(it) }, valueRange = 0.1f..1f,
                                                    colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══ 4. LOGO ═══
                SectionCard("Logo ထည့်ရန်", Icons.Default.BrandingWatermark, Emerald) {
                    OutlinedButton(onClick = { logoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (s.logoUri != null) Emerald else CardBorder)) {
                        Icon(if (s.logoUri != null) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate, null, tint = if (s.logoUri != null) Emerald else TextDim)
                        Spacer(Modifier.width(8.dp)); Text(if (s.logoUri != null) "Logo ရွေးပြီးပါပြီ" else "Logo ရွေးချယ်ရန်", color = if (s.logoUri != null) Emerald else TextDim)
                    }
                    if (s.logoUri != null) {
                        TextButton(onClick = { vm.removeLogo() }) { Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("ဖယ်ရှားမည်", color = ErrorRed, fontSize = 12.sp) }
                    }
                }

                // ═══ 5. AI VOICE ═══
                SectionCard("AI အသံထပ်ခြင်း", Icons.Default.RecordVoiceOver, Purple) {
                    OutlinedTextField(value = s.aiText, onValueChange = { vm.setAiText(it) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        placeholder = { Text("AI Script ရိုက်ထည့်ပါ...", fontSize = 13.sp) }, maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                        shape = RoundedCornerShape(12.dp))
                    if (s.aiText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { vm.analyzeScript() }, enabled = !s.isAnalyzing, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Purple.copy(0.3f))) {
                            if (s.isAnalyzing) CircularProgressIndicator(Modifier.size(16.dp), Purple, strokeWidth = 2.dp)
                            else Icon(Icons.Default.AutoAwesome, null, tint = Purple, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text("AI ဖြင့် ပြန်ရေးမည်", fontSize = 12.sp, color = Purple)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // Voice selector header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, null, tint = Purple, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text("AI Voice ရွေးချယ်ရန်", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                        if (s.selectedVoice.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = Purple.copy(0.15f), shape = RoundedCornerShape(12.dp)) {
                                Text(s.selectedVoice, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Tabs
                    VoiceTabRow(listOf("google" to "🔷 Google PREMIUM", "microsoft" to "🟢 Microsoft FREE"), s.voiceTab) { vm.switchVoiceTab(it) }
                    Spacer(Modifier.height(8.dp))

                    // Search
                    OutlinedTextField(value = s.voiceSearch, onValueChange = { vm.setVoiceSearch(it) }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search voices...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextDim, modifier = Modifier.size(18.dp)) }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple.copy(0.3f), unfocusedBorderColor = Purple.copy(0.09f),
                            focusedContainerColor = SurfaceDark.copy(0.75f), unfocusedContainerColor = SurfaceDark.copy(0.75f), cursorColor = Purple),
                        shape = RoundedCornerShape(11.dp))
                    Spacer(Modifier.height(8.dp))

                    // Grid
                    val voices = vm.filteredVoices
                    if (voices.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Text("Not found", color = TextDim, fontSize = 13.sp) }
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(voices, key = { it.name }) { v ->
                                VoiceCard(v.name, v.label, v.gender, s.selectedVoice == v.name, { vm.selectVoice(v.name) })
                            }
                        }
                    }
                    // Premium notice
                    if (s.voiceTab == "google" && s.aiText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(color = WarningYellow.copy(0.08f), shape = RoundedCornerShape(8.dp)) {
                            Text("⚠ Google AI Voice → Gold Coins သာ", color = WarningYellow, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }

                // ═══ 6. PROCESS ═══
                PrimaryButton(
                    text = if (s.isProcessing) "Processing..." else "စတင်ပြုပြင်မည် ${vm.costText}",
                    onClick = { vm.startProcessing(context) }, loading = s.isProcessing, color = Emerald,
                    enabled = s.videoUri != null || s.videoFilename != null,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // Snackbars
        s.error?.let { msg ->
            Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = ErrorRed.copy(0.9f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.clearError() }) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
            LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); vm.clearError() }
        }
        s.success?.let { msg ->
            Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), color = Emerald.copy(0.9f), shape = RoundedCornerShape(12.dp)) {
                Text(msg, color = DarkBg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp))
            }
            LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); vm.clearSuccess() }
        }
        LoadingOverlay(s.isUploading, "Downloading...")
    }
}

// ── Video Preview (ExoPlayer) ──
@Composable
private fun VideoPreview(uri: Uri) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Surface(color = Color.Black, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
    }
}

// ── Color Row ──
@Composable
private fun ColorRow(selected: String, onSelect: (String) -> Unit) {
    val colors = listOf("#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#000000")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { hex ->
            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(c)
                .border(2.dp, if (selected.equals(hex, true)) Purple else Color.Transparent, RoundedCornerShape(6.dp))
                .clickable { onSelect(hex) })
        }
    }
}
