package com.recapmaker.app.ui.settings

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recapmaker.app.ui.common.*

@Composable
fun SettingsScreen(username: String, email: String?, onBack: () -> Unit, onLogout: () -> Unit) {
    var showChangePw by remember { mutableStateOf(false) }
    var showLinkEmail by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
            Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Account info
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Account", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    InfoRow("Username", username)
                    InfoRow("Email", email ?: "Not linked")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Actions
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (email == null) {
                        SettingsItem(Icons.Default.Email, "Link Email", "For password recovery") { showLinkEmail = true }
                        HorizontalDivider(color = CardBorder)
                    }
                    SettingsItem(Icons.Default.Lock, "Change Password", "") { showChangePw = true }
                    HorizontalDivider(color = CardBorder)
                    SettingsItem(Icons.Default.History, "Video History", "Locally stored") { /* TODO: show history */ }
                    HorizontalDivider(color = CardBorder)
                    SettingsItem(Icons.Default.Info, "App Version", "2.0.0") { }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Logout
            Button(
                onClick = onLogout, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Logout", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = TextDim, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextDim)
    }
}
