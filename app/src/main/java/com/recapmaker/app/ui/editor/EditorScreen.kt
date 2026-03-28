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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recapmaker.app.data.model.*
import com.recapmaker.app.ui.common.*

@Composable
fun EditorScreen(onBack: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val s = vm.state
    val context = LocalContext.current

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.onVideoSelected(it, context) }
    }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.onLogoSelected(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(DarkBg)) {
            // ── Top Bar ──
            Surface(color = CardBg, shadowElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                    Text("Video Editor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Purple, modifier = Modifier.weight(1f))
                    CoinBadge(s.gold, s.silver)
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ══════════════════════════════════
                // SECTION 1: Video Source
                // ══════════════════════════════════
                SectionCard("၁. မူရင်း Video File", Icons.Default.VideoFile, Purple) {
                    // Upload button
                    OutlinedButton(
                        onClick = { videoPicker.launch("video/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (s.videoFilename != null) Emerald else Purple.copy(0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (s.videoFilename != null) Emerald.copy(0.06f) else Color.Transparent),
                    ) {
                        Icon(
                            if (s.videoFilename != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                            null, tint = if (s.videoFilename != null) Emerald else Purple,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (s.videoFilename != null) "Video ရွေးပြီးပါပြီ" else "Video File တင်ရန်",
                            color = if (s.videoFilename != null) Emerald else TextPrimary,
                        )
                    }

                    // OR divider
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HorizontalDivider(Modifier.weight(1f), color = CardBorder)
                        Text("  OR  ", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        HorizontalDivider(Modifier.weight(1f), color = CardBorder)
                    }

                    // URL Download
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = s.urlInput, onValueChange = { vm.updateUrl(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("YouTube, TikTok, Facebook Link", fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple,
                            ),
                            shape = RoundedCornerShape(12.dp, 0.dp, 0.dp, 12.dp),
                        )
                        Button(
                            onClick = { vm.downloadFromUrl() },
                            enabled = s.urlInput.isNotBlank() && !s.isDownloading,
                            shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp),
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple),
                        ) {
                            if (s.isDownloading) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Link, null)
                        }
                    }

                    // Video info
                    if (s.videoFilename != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(color = Emerald.copy(0.08f), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Movie, null, tint = Emerald, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(s.videoFilename ?: "", color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                                    if (s.videoDuration > 0) {
                                        Text("Duration: ${formatDuration(s.videoDuration)}", color = Emerald, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ══════════════════════════════════
                // SECTION 2: Bypass Effects
                // ══════════════════════════════════
                SectionCard("၂. ပြုပြင်မည့် Effect များ", Icons.Default.Tune, Purple) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EffectToggle("Video ကိုလှန်မည်", Icons.Default.SwapHoriz, s.flipEnabled, { vm.toggleFlip(it) })
                        EffectToggle("Speed မြန်မည် (1.05x)", Icons.Default.Speed, s.speedEnabled, { vm.toggleSpeed(it) })
                        EffectToggle("အသံပြောင်းမည် (Copyright)", Icons.Default.MusicNote, s.pitchEnabled, { vm.togglePitch(it) })
                        EffectToggle("Noise/Grain ထည့်မည်", Icons.Default.Grain, s.noiseEnabled, { vm.toggleNoise(it) })
                        EffectToggle("နေရာဝှက် (Blur)", Icons.Default.BlurOn, s.blurEnabled, { vm.toggleBlur(it) }, switchColor = Rose)
                    }

                    // Blur box count selector
                    AnimatedVisibility(s.blurEnabled) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text("Blur Box အရေအတွက်", color = TextDim, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (1..4).forEach { count ->
                                    FilterChip(
                                        selected = s.blurBoxCount == count,
                                        onClick = { vm.setBlurBoxCount(count) },
                                        label = { Text("$count") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Rose.copy(0.15f),
                                            selectedLabelColor = Rose,
                                        ),
                                    )
                                }
                            }
                            Text(
                                "⚠ Blur box တွေကို Server ဘက်မှာ Video preview ပေါ်တွင် ရွေးချယ်နိုင်ပါသည်",
                                color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                // ══════════════════════════════════
                // SECTION 3: Text Watermark
                // ══════════════════════════════════
                SectionCard("စာတန်း Watermark", Icons.Default.TextFields, Purple) {
                    OutlinedTextField(
                        value = s.watermarkText, onValueChange = { vm.updateWatermarkText(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("ဥပမာ: သင်၏ Channel အမည်", fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    // Expand for advanced watermark settings
                    AnimatedVisibility(s.watermarkText.isNotBlank()) {
                        var expanded by remember { mutableStateOf(false) }
                        Column(Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, tint = Purple, modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Watermark အသေးစိတ်ပြင်ဆင်ရန်", fontSize = 12.sp, color = Purple)
                            }

                            AnimatedVisibility(expanded) {
                                Surface(
                                    color = SurfaceDark, shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // Font size slider
                                        Text("စာလုံး Size: ${s.watermarkSize}", color = TextDim, fontSize = 12.sp)
                                        Slider(
                                            value = s.watermarkSize.toFloat(),
                                            onValueChange = { vm.updateWatermarkSize(it.toInt()) },
                                            valueRange = 12f..72f,
                                            colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple),
                                        )

                                        // Font color
                                        Text("စာလုံးအရောင်", color = TextDim, fontSize = 12.sp)
                                        ColorSelector(s.watermarkColor) { vm.updateWatermarkColor(it) }

                                        // Position
                                        Text("နေရာ", color = TextDim, fontSize = 12.sp)
                                        PositionSelector(s.watermarkPosition) { vm.updateWatermarkPosition(it) }

                                        // Scroll toggle
                                        EffectToggle(
                                            "စာတန်းကို ရွေ့လျားစေမည်",
                                            Icons.Default.SwapHorizontalCircle,
                                            s.watermarkScroll, { vm.updateWatermarkScroll(it) },
                                        )

                                        // Box background toggle
                                        EffectToggle(
                                            "စာတန်းနောက်ခံဘောင် ထည့်မည်",
                                            Icons.Default.CheckBoxOutlineBlank,
                                            s.watermarkBox, { vm.updateWatermarkBox(it) },
                                        )

                                        AnimatedVisibility(s.watermarkBox) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("ဘောင် Opacity: ${"%.1f".format(s.watermarkBoxOpacity)}", color = TextDim, fontSize = 12.sp)
                                                Slider(
                                                    value = s.watermarkBoxOpacity,
                                                    onValueChange = { vm.updateWatermarkBoxOpacity(it) },
                                                    valueRange = 0.1f..1.0f,
                                                    colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ══════════════════════════════════
                // SECTION 4: Logo
                // ══════════════════════════════════
                SectionCard("Logo ထည့်ရန်", Icons.Default.BrandingWatermark, Emerald) {
                    OutlinedButton(
                        onClick = { logoPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (s.logoUri != null) Emerald else CardBorder),
                    ) {
                        Icon(
                            if (s.logoUri != null) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate,
                            null, tint = if (s.logoUri != null) Emerald else TextDim,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (s.logoUri != null) "Logo ရွေးပြီးပါပြီ" else "Logo ရွေးချယ်ရန်",
                            color = if (s.logoUri != null) Emerald else TextDim,
                        )
                    }
                    if (s.logoUri != null) {
                        TextButton(onClick = { vm.removeLogo() }) {
                            Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Logo ဖယ်ရှားမည်", color = ErrorRed, fontSize = 12.sp)
                        }
                    }
                }

                // ══════════════════════════════════
                // SECTION 5: AI Voice (TTS)
                // ══════════════════════════════════
                SectionCard("AI အသံထပ်ခြင်း", Icons.Default.RecordVoiceOver, Purple) {
                    // Script text area
                    OutlinedTextField(
                        value = s.aiText, onValueChange = { vm.updateAiText(it) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        placeholder = { Text("AI Script ရိုက်ထည့်ပါ...", fontSize = 13.sp) },
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    // Analyze button
                    if (s.aiText.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { vm.analyzeScript() },
                            enabled = !s.isAnalyzing,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Purple.copy(0.3f)),
                        ) {
                            if (s.isAnalyzing) CircularProgressIndicator(Modifier.size(16.dp), Purple, strokeWidth = 2.dp)
                            else Icon(Icons.Default.AutoAwesome, null, tint = Purple, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Script ကို AI ဖြင့် ပြန်ရေးမည်", fontSize = 12.sp, color = Purple)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Voice Selector ──
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, null, tint = Purple, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AI Voice ရွေးချယ်ရန်", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                        if (s.selectedVoice.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = Purple.copy(0.15f), shape = RoundedCornerShape(12.dp)) {
                                Text(s.selectedVoice, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Provider tabs
                    TabRow(
                        tabs = listOf("google" to "🔷 Google  PREMIUM", "microsoft" to "🟢 Microsoft  FREE"),
                        selectedTab = s.voiceTab, onTabSelected = { vm.switchVoiceTab(it) },
                    )

                    Spacer(Modifier.height(8.dp))

                    // Voice search
                    OutlinedTextField(
                        value = s.voiceSearch, onValueChange = { vm.updateVoiceSearch(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search voices...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextDim, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple.copy(0.3f),
                            unfocusedBorderColor = Purple.copy(0.09f),
                            focusedContainerColor = SurfaceDark.copy(0.75f),
                            unfocusedContainerColor = SurfaceDark.copy(0.75f),
                            cursorColor = Purple,
                        ),
                        shape = RoundedCornerShape(11.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    // Voice grid (2 columns, scrollable with max height)
                    val filteredVoices = vm.filteredVoices
                    if (filteredVoices.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("Not found", color = TextDim, fontSize = 13.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            items(filteredVoices, key = { it.name }) { voice ->
                                VoiceCard(
                                    name = voice.name,
                                    label = voice.label,
                                    gender = voice.gender,
                                    isSelected = s.selectedVoice == voice.name,
                                    onClick = { vm.selectVoice(voice.name) },
                                )
                            }
                        }
                    }

                    // Premium notice
                    if (s.voiceTab == "google" && s.aiText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(color = WarningYellow.copy(0.08f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                "⚠ Google AI Voice သုံးရန် Gold Coins သာ သုံးနိုင်ပါသည်",
                                color = WarningYellow, fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }

                // ══════════════════════════════════
                // SECTION 6: Process Button
                // ══════════════════════════════════
                val costText = vm.costDisplayText
                PrimaryButton(
                    text = if (s.isProcessing) "Processing..." else "စတင်ပြုပြင်မည် $costText",
                    onClick = { vm.startProcessing(context) },
                    loading = s.isProcessing,
                    color = Emerald,
                    enabled = s.videoFilename != null,
                )

                Spacer(Modifier.height(24.dp))
            }
        }

        // Error snackbar
        if (s.error != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                color = ErrorRed.copy(0.9f), shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.error ?: "", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.clearError() }) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            LaunchedEffect(s.error) {
                kotlinx.coroutines.delay(4000)
                vm.clearError()
            }
        }

        // Success snackbar
        if (s.successMessage != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                color = Emerald.copy(0.9f), shape = RoundedCornerShape(12.dp),
            ) {
                Text(s.successMessage ?: "", color = DarkBg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp))
            }
            LaunchedEffect(s.successMessage) {
                kotlinx.coroutines.delay(3000)
                vm.clearSuccess()
            }
        }

        // Loading overlay
        LoadingOverlay(s.isDownloading, "Downloading from URL...")
    }
}

// ── Color Selector ──

@Composable
private fun ColorSelector(selected: String, onSelect: (String) -> Unit) {
    val colors = listOf("#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#000000")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { hex ->
            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
            Box(
                Modifier.size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .border(
                        2.dp,
                        if (selected.equals(hex, true)) Purple else Color.Transparent,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(hex) },
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
