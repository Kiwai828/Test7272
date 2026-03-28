package com.recapmaker.app.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recapmaker.app.ui.common.*

@Composable
fun DashboardScreen(vm: DashboardViewModel, onEditor: () -> Unit, onSubtitle: () -> Unit, onSettings: () -> Unit, onLogout: () -> Unit) {
    val s = vm.state

    Column(Modifier.fillMaxSize().background(DarkBg).verticalScroll(rememberScrollState())) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recap Maker", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Purple)
                Text("Hello, ${s.username}", fontSize = 13.sp, color = TextDim)
            }
            CoinBadge(s.gold, s.silver)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = TextDim) }
        }

        // Coin cards
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoinCard("Gold", s.gold, Gold, Modifier.weight(1f))
            CoinCard("Silver", s.silver, SilverColor, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        // Daily checkin
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily Check-in", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("+${s.checkinSilver} Silver coins", fontSize = 13.sp, color = SilverColor)
                }
                Button(
                    onClick = { vm.dailyCheckin() },
                    enabled = !s.checkedInToday && !s.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = if (s.checkedInToday) CardBorder else Emerald),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (s.checkedInToday) "Done" else "Claim", color = if (s.checkedInToday) TextDim else DarkBg)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Main actions
        Text("Tools", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard("Video Editor", "Flip, blur, TTS, logo", Icons.Default.VideoSettings, Purple, Modifier.weight(1f)) { onEditor() }
            ActionCard("Subtitles", "Auto-generate & burn", Icons.Default.Subtitles, Emerald, Modifier.weight(1f)) { onSubtitle() }
        }

        Spacer(Modifier.height(12.dp))

        // Pricing info
        if (s.pricingTiers.isNotEmpty()) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Pricing", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    s.pricingTiers.forEach { tier ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("${tier.max_seconds}s", color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("${tier.cost} coins", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Error snackbar
        if (s.error != null) {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(s.error)
            LaunchedEffect(s.error) { kotlinx.coroutines.delay(3000); vm.clearError() }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun CoinCard(label: String, amount: Int, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("●", color = color, fontSize = 20.sp)
            Text("$amount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 12.sp, color = TextDim)
        }
    }
}

@Composable
private fun ActionCard(title: String, desc: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
            Text(desc, fontSize = 12.sp, color = TextDim)
        }
    }
}
