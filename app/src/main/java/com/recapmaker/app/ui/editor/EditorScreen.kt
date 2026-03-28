package com.recapmaker.app.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recapmaker.app.ui.common.*

@Composable
fun EditorScreen(onBack: () -> Unit) {
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var flipEnabled by remember { mutableStateOf(false) }
    var speedEnabled by remember { mutableStateOf(false) }
    var noiseEnabled by remember { mutableStateOf(false) }
    var pitchEnabled by remember { mutableStateOf(false) }
    var blurEnabled by remember { mutableStateOf(false) }
    var ttsText by remember { mutableStateOf("") }
    var selectedVoice by remember { mutableStateOf("ThihaNeural") }
    var isProcessing by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> videoUri = uri }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
            Text("Video Editor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Upload
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { videoPicker.launch("video/*") },
                color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (videoUri != null) Emerald else CardBorder),
            ) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (videoUri != null) Icons.Default.CheckCircle else Icons.Default.VideoCall, null,
                        tint = if (videoUri != null) Emerald else TextDim, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (videoUri != null) "Video selected" else "Tap to select video", color = if (videoUri != null) Emerald else TextDim)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Effects toggles
            Text("Bypass Effects", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            ToggleRow("Flip Video", flipEnabled) { flipEnabled = it }
            ToggleRow("Speed Change", speedEnabled) { speedEnabled = it }
            ToggleRow("Add Noise", noiseEnabled) { noiseEnabled = it }
            ToggleRow("Pitch Shift", pitchEnabled) { pitchEnabled = it }
            ToggleRow("Blur Areas", blurEnabled) { blurEnabled = it }

            Spacer(Modifier.height(16.dp))

            // TTS
            Text("AI Voice (TTS)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            AppTextField(ttsText, { ttsText = it }, "Script text (optional)", imeAction = androidx.compose.ui.text.input.ImeAction.Done)

            Spacer(Modifier.height(8.dp))

            // Voice selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ThihaNeural", "NilarNeural", "Puck", "Kore").forEach { voice ->
                    FilterChip(
                        selected = selectedVoice == voice,
                        onClick = { selectedVoice = voice },
                        label = { Text(voice, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Purple, selectedLabelColor = TextPrimary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Process button
            PrimaryButton(
                text = if (isProcessing) "Processing..." else "Process Video",
                onClick = {
                    if (videoUri != null) {
                        isProcessing = true
                        // TODO: FFmpegX processing + coin deduction
                    }
                },
                loading = isProcessing,
                color = Emerald,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Purple, checkedThumbColor = TextPrimary),
        )
    }
}
