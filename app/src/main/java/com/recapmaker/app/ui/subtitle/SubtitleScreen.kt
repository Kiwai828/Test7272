package com.recapmaker.app.ui.subtitle

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recapmaker.app.ui.common.*

@Composable
fun SubtitleScreen(onBack: () -> Unit, vm: SubtitleViewModel = hiltViewModel()) {
    val s = vm.state
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.onVideoSelected(it, context) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(DarkBg)) {
            // Top bar
            Surface(color = CardBg, shadowElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                    Text("Subtitle Generator", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald, modifier = Modifier.weight(1f))
                    CoinBadge(s.gold, s.silver)
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Video Source ──
                SectionCard("Video File", Icons.Default.VideoFile, Emerald) {
                    OutlinedButton(
                        onClick = { picker.launch("video/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (s.videoFilename != null) Emerald else CardBorder),
                    ) {
                        Icon(
                            if (s.videoFilename != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                            null, tint = if (s.videoFilename != null) Emerald else TextDim,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.videoFilename != null) "Video ရွေးပြီးပါပြီ" else "Video File တင်ရန်",
                            color = if (s.videoFilename != null) Emerald else TextDim)
                    }

                    // URL Download
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = CardBorder)
                        Text("  OR  ", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        HorizontalDivider(Modifier.weight(1f), color = CardBorder)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = s.urlInput, onValueChange = { vm.updateUrl(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("YouTube, TikTok, Facebook Link", fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald, unfocusedBorderColor = CardBorder, cursorColor = Emerald,
                            ),
                            shape = RoundedCornerShape(12.dp, 0.dp, 0.dp, 12.dp),
                        )
                        Button(
                            onClick = { vm.downloadFromUrl() },
                            enabled = s.urlInput.isNotBlank() && !s.isDownloading,
                            shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp),
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                        ) {
                            if (s.isDownloading) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Link, null, tint = DarkBg)
                        }
                    }

                    if (s.videoFilename != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(color = Emerald.copy(0.08f), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Movie, null, tint = Emerald, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(s.videoFilename ?: "", color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }

                // ── Subtitle Style ──
                SectionCard("Subtitle Style", Icons.Default.FormatSize, Purple) {
                    // Font size
                    Text("Font Size: ${s.fontSize.toInt()}", color = TextDim, fontSize = 13.sp)
                    Slider(
                        value = s.fontSize, onValueChange = { vm.updateFontSize(it) },
                        valueRange = 10f..32f,
                        colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple),
                    )

                    // Background box
                    EffectToggle("Background Box", Icons.Default.CheckBoxOutlineBlank, s.boxEnabled, { vm.toggleBox(it) })

                    Spacer(Modifier.height(8.dp))

                    // Position
                    Text("Position", color = TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("top_center" to "Top", "middle" to "Middle", "bottom_center" to "Bottom").forEach { (value, label) ->
                            FilterChip(
                                selected = s.position == value, onClick = { vm.updatePosition(value) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Purple, selectedLabelColor = TextPrimary,
                                ),
                            )
                        }
                    }

                    // Font color
                    Spacer(Modifier.height(8.dp))
                    Text("Font Color", color = TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    SubtitleColorSelector(s.fontColor) { vm.updateFontColor(it) }
                }

                // ── Bypass Effects (same as editor) ──
                SectionCard("Effect များ", Icons.Default.Tune, Purple) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EffectToggle("Video ကိုလှန်မည်", Icons.Default.SwapHoriz, s.flipEnabled, { vm.toggleFlip(it) })
                        EffectToggle("Speed မြန်မည်", Icons.Default.Speed, s.speedEnabled, { vm.toggleSpeed(it) })
                        EffectToggle("Noise/Grain ထည့်မည်", Icons.Default.Grain, s.noiseEnabled, { vm.toggleNoise(it) })
                        EffectToggle("နေရာဝှက် (Blur)", Icons.Default.BlurOn, s.blurEnabled, { vm.toggleBlur(it) }, switchColor = Rose)
                    }
                }

                // ── Process Button ──
                PrimaryButton(
                    text = if (s.isProcessing) "Generating..." else "Generate Subtitles",
                    onClick = { vm.startSubtitleProcessing(context) },
                    loading = s.isProcessing, color = Emerald,
                    enabled = s.videoFilename != null,
                )

                Spacer(Modifier.height(24.dp))
            }
        }

        // Error
        if (s.error != null) {
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                color = ErrorRed.copy(0.9f), shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.error ?: "", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.clearError() }) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            LaunchedEffect(s.error) { kotlinx.coroutines.delay(4000); vm.clearError() }
        }

        LoadingOverlay(s.isDownloading, "Downloading...")
    }
}

@Composable
private fun SubtitleColorSelector(selected: String, onSelect: (String) -> Unit) {
    val colors = listOf("#FFFFFF", "#FFFF00", "#00FF00", "#00FFFF", "#FF0000", "#FF69B4")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { hex ->
            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
            Box(
                Modifier.size(30.dp)
                    .background(color, RoundedCornerShape(8.dp))
                    .border(2.dp, if (selected.equals(hex, true)) Purple else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(hex) },
            )
        }
    }
}
