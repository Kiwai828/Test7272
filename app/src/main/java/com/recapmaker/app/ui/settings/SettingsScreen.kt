package com.recapmaker.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.recapmaker.app.ui.common.*

@Composable
fun SettingsScreen(
    username: String,
    email: String?,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s = vm.state
    var showChangePw by remember { mutableStateOf(false) }
    var showLinkEmail by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
            Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Account", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    InfoRow("Username", username)
                    InfoRow("Email", email ?: "Not linked")
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (email == null) {
                        SettingsItem(Icons.Default.Email, "Link Email", "For password recovery") { vm.clearMessages(); showLinkEmail = true }
                        HorizontalDivider(color = CardBorder)
                    }
                    SettingsItem(Icons.Default.Lock, "Change Password", "") { vm.clearMessages(); showChangePw = true }
                    HorizontalDivider(color = CardBorder)
                    SettingsItem(Icons.Default.History, "Video History", "Locally stored") { }
                    HorizontalDivider(color = CardBorder)
                    SettingsItem(Icons.Default.Info, "App Version", "2.0.0") { }
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLogout, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Logout", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLinkEmail) {
        LinkEmailDialog(
            vm = vm,
            onDismiss = { showLinkEmail = false; vm.clearMessages() },
        )
    }

    if (showChangePw) {
        ChangePasswordDialog(
            vm = vm,
            onDismiss = { showChangePw = false; vm.clearMessages() },
        )
    }
}

@Composable
private fun LinkEmailDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val s = vm.state
    var emailInput by remember { mutableStateOf("") }

    LaunchedEffect(s.linkEmailSuccess) { if (s.linkEmailSuccess) onDismiss() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, null, tint = Purple, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Link Email", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = TextDim)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Password ပြန်ယူနိုင်ရန် email ချိတ်ဆက်ပါ", color = TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    emailInput, { emailInput = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("example@email.com", fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                ErrorBanner(s.error)
                Spacer(Modifier.height(4.dp))
                PrimaryButton(
                    "Link Email",
                    onClick = { vm.linkEmail(emailInput.trim()) },
                    loading = s.isLoading,
                    enabled = emailInput.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput.trim()).matches(),
                )
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val s = vm.state
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }

    LaunchedEffect(s.changePwSuccess) { if (s.changePwSuccess) onDismiss() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Purple, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Change Password", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = TextDim)
                    }
                }
                Spacer(Modifier.height(16.dp))
                PasswordField(oldPw, { oldPw = it }, "လက်ရှိ Password", imeAction = ImeAction.Next)
                Spacer(Modifier.height(10.dp))
                PasswordField(newPw, { newPw = it }, "Password အသစ်", imeAction = ImeAction.Next)
                Spacer(Modifier.height(10.dp))
                PasswordField(confirmPw, { confirmPw = it }, "Password အသစ် အတည်ပြု")
                Spacer(Modifier.height(8.dp))
                ErrorBanner(s.error)
                Spacer(Modifier.height(4.dp))
                PrimaryButton(
                    "Password ပြောင်းမည်",
                    onClick = { vm.changePassword(oldPw, newPw, confirmPw) },
                    loading = s.isLoading,
                    enabled = oldPw.isNotBlank() && newPw.isNotBlank() && confirmPw.isNotBlank(),
                )
            }
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
