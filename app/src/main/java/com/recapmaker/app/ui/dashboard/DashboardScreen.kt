package com.recapmaker.app.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.recapmaker.app.data.model.CoinPackage
import com.recapmaker.app.ui.common.*

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onEditor: () -> Unit, onSubtitle: () -> Unit,
    onSettings: () -> Unit, onLogout: () -> Unit,
) {
    val s = vm.state
    val context = LocalContext.current
    var showPackages by remember { mutableStateOf(false) }
    var showCheckin by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DarkBg).verticalScroll(rememberScrollState())) {
        // ── Top Bar ──
        Surface(color = CardBg, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recap Maker", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Purple)
                    Text("Hello, ${s.username}", fontSize = 13.sp, color = TextDim)
                }
                CoinBadge(s.gold, s.silver, onClick = { showPackages = true })
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = TextDim) }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Coin Cards ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoinCard("🥇 Gold", s.gold, Gold, Modifier.weight(1f)) { showPackages = true }
                CoinCard("🥈 Silver", s.silver, SilverColor, Modifier.weight(1f)) { showCheckin = true }
            }

            // ── Daily Check-in ──
            Surface(
                color = CardBg, shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (s.checkedInToday) CardBorder else Emerald.copy(0.3f)),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily Check-in", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("+${s.checkinSilver} Silver coins", fontSize = 13.sp, color = SilverColor)
                    }
                    Button(
                        onClick = { vm.dailyCheckin() },
                        enabled = !s.checkedInToday && !s.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (s.checkedInToday) CardBorder else Emerald,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (s.isLoading) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                        else Text(
                            if (s.checkedInToday) "✅ Done" else "🥈 Claim",
                            color = if (s.checkedInToday) TextDim else DarkBg,
                        )
                    }
                }
            }

            // ── Tools ──
            Text("Tools", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Video Editor", "Flip, blur, TTS, logo, watermark", Icons.Default.VideoSettings, Purple, Modifier.weight(1f)) { onEditor() }
                ActionCard("Subtitles", "Auto-generate & burn subtitles", Icons.Default.Subtitles, Emerald, Modifier.weight(1f)) { onSubtitle() }
            }

            // ── Quick Actions ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallActionCard("🥇 Gold ဝယ်ရန်", Gold, Modifier.weight(1f)) { showPackages = true }
                SmallActionCard("🥈 Check-in", SilverColor, Modifier.weight(1f)) { showCheckin = true }
            }

            // ── Pricing Info ──
            if (s.pricingTiers.isNotEmpty()) {
                SectionCard("Pricing", Icons.Default.Payments, Gold) {
                    s.pricingTiers.forEach { tier ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("≤ ${tier.max_seconds}s", color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                if (tier.cost == 0) "FREE" else "${tier.cost} coins",
                                color = if (tier.cost == 0) Emerald else Gold,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // ── Error ──
            if (s.error != null) {
                ErrorBanner(s.error)
                LaunchedEffect(s.error) { kotlinx.coroutines.delay(3000); vm.clearError() }
            }

            if (s.checkinSuccess) {
                SuccessBanner("🥈 Check-in Success! +${s.checkinSilver} Silver coins")
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    // ═════════════════════════════
    // Coin Packages Popup
    // ═════════════════════════════
    if (showPackages) {
        CoinPackagesDialog(
            packages = s.packages,
            paymentMessage = s.paymentMessage,
            contactUsername = s.contactUsername,
            onDismiss = { showPackages = false },
        )
    }

    // ═════════════════════════════
    // Check-in Dialog
    // ═════════════════════════════
    if (showCheckin) {
        CheckinDialog(
            checkedIn = s.checkedInToday,
            checkinSilver = s.checkinSilver,
            isLoading = s.isLoading,
            onCheckin = { vm.dailyCheckin() },
            onDismiss = { showCheckin = false },
        )
    }
}

// ── Coin Card ──

@Composable
private fun CoinCard(label: String, amount: Int, color: Color, modifier: Modifier, onClick: () -> Unit = {}) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = CardBg, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(0.15f)),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp, color = TextDim)
            Spacer(Modifier.height(4.dp))
            Text("$amount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ── Action Card ──

@Composable
private fun ActionCard(title: String, desc: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = CardBg, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
            Text(desc, fontSize = 11.sp, color = TextDim, maxLines = 2)
        }
    }
}

// ── Small Action Card ──

@Composable
private fun SmallActionCard(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = color.copy(0.06f), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(0.2f)),
    ) {
        Text(
            label, modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            textAlign = TextAlign.Center, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, color = color,
        )
    }
}

// ═════════════════════════════════════
// Coin Packages Dialog (Website's pkgModal)
// ═════════════════════════════════════

@Composable
fun CoinPackagesDialog(
    packages: List<CoinPackage>,
    paymentMessage: String,
    contactUsername: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp), color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🥇 Gold Coins ဝယ်ရန်", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, tint = TextDim, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Package list
                if (packages.isEmpty()) {
                    Text("Package များမရှိပါ။", color = TextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(20.dp))
                } else {
                    packages.forEach { pkg ->
                        Surface(
                            color = SurfaceDark, shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Gold.copy(0.15f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(pkg.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                    Text("🥇 ${pkg.coins} Gold Coins", color = Gold, fontSize = 12.sp)
                                    if (pkg.no_ads_days > 0) {
                                        Text("🚫 No Ads ${pkg.no_ads_days} ရက်", color = Emerald, fontSize = 11.sp)
                                    }
                                }
                                Surface(
                                    color = Gold.copy(0.15f), shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        "${pkg.price} Ks",
                                        color = Gold, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Payment instructions
                Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("ငွေပေးချေရန် ညွှန်ကြားချက်", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            paymentMessage.ifBlank { "Admin ကိုဆက်သွယ်ပါ" },
                            color = TextPrimary, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val tgUser = contactUsername.removePrefix("@")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$tgUser"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("📱 Admin ကိုဆက်သွယ်ရန်", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════
// Check-in Dialog
// ═════════════════════════════════════

@Composable
fun CheckinDialog(
    checkedIn: Boolean, checkinSilver: Int,
    isLoading: Boolean, onCheckin: () -> Unit, onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🥈", fontSize = 50.sp)
                Spacer(Modifier.height(12.dp))
                Text("Daily Check-in", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "တရက်တကြိမ် Check-in နှိပ်ပြီး Silver Coins $checkinSilver ခု ရယူပါ။",
                    color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))

                if (checkedIn) {
                    Surface(color = Emerald.copy(0.12f), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "✅ ယနေ့ Check-in ပြီးပါပြီ",
                            color = Emerald, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                } else {
                    Button(
                        onClick = { onCheckin(); onDismiss() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                        else Text("🥈 Check-in နှိပ်ပါ ($checkinSilver Silver)", color = DarkBg, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
