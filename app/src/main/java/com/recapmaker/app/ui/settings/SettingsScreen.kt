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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    // Auto-close dialogs on success
    LaunchedEffect(s.emailLinked) { if (s.emailLinked) showLinkEmail = false }
    LaunchedEffect(s.passwordChanged) { if (s.passwordChanged) showChangePw = false }

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

            // Success / error banners
            s.success?.let { msg ->
                Surface(color = Emerald.copy(.12f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Emerald.copy(.3f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Emerald, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Emerald, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearMessages() }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, tint = Emerald, modifier = Modifier.size(14.dp)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            s.error?.let { msg ->
                Surface(color = ErrorRed.copy(.12f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ErrorRed.copy(.3f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearMessages() }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, tint = ErrorRed, modifier = Modifier.size(14.dp)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Actions
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column {
                    // FIX: Link Email is always shown if email is null — and now actually calls the API
                    if (email == null && !s.emailLinked) {
                        SettingsItem(Icons.Default.Email, "Link Email", "Password recovery အတွက်") { showLinkEmail = true }
                        HorizontalDivider(color = CardBorder)
                    }
                    SettingsItem(Icons.Default.Lock, "Change Password", "") { showChangePw = true }
                    HorizontalDivider(color = CardBorder)
                    SettingsItem(Icons.Default.History, "Video History", "Locally stored") { }
                    HorizontalDivider(color = CardBorder)
                    SettingsItem(Icons.Default.Info, "App Version", "3.9.0") { }
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

    // ── Link Email Dialog ──
    if (showLinkEmail) {
        var emailInput by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showLinkEmail = false; vm.clearMessages() }) {
            Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Email ချိတ်ဆက်ရန်", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Password ပြန်ယူရန် email ဖြည့်ပါ", color = TextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        placeholder = { Text("example@email.com", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showLinkEmail = false; vm.clearMessages() },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardBorder),
                        ) { Text("Cancel", color = TextDim) }
                        Button(
                            onClick = { vm.linkEmail(emailInput.trim()) },
                            enabled = emailInput.isNotBlank() && !s.isLoading,
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple),
                        ) {
                            if (s.isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                            else Text("ချိတ်ဆက်မည်")
                        }
                    }
                }
            }
        }
    }

    // ── Change Password Dialog ──
    if (showChangePw) {
        var oldPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        var confirmPw by remember { mutableStateOf("") }
        var showOld by remember { mutableStateOf(false) }
        var showNew by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { showChangePw = false; vm.clearMessages() }) {
            Surface(shape = RoundedCornerShape(20.dp), color = CardBg, border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Password ပြောင်းရန်", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    // Old password
                    OutlinedTextField(
                        value = oldPw, onValueChange = { oldPw = it },
                        label = { Text("လက်ရှိ Password") }, singleLine = true,
                        visualTransformation = if (showOld) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showOld = !showOld }) { Icon(if (showOld) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextDim, modifier = Modifier.size(18.dp)) } },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    // New password
                    OutlinedTextField(
                        value = newPw, onValueChange = { newPw = it },
                        label = { Text("Password အသစ်") }, singleLine = true,
                        visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showNew = !showNew }) { Icon(if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextDim, modifier = Modifier.size(18.dp)) } },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    // Confirm password
                    OutlinedTextField(
                        value = confirmPw, onValueChange = { confirmPw = it },
                        label = { Text("Password အတည်ပြုရန်") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmPw.isNotEmpty() && confirmPw != newPw,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    if (confirmPw.isNotEmpty() && confirmPw != newPw) {
                        Text("Password မတူပါ", color = ErrorRed, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showChangePw = false; vm.clearMessages() },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardBorder),
                        ) { Text("Cancel", color = TextDim) }
                        Button(
                            onClick = { vm.changePassword(oldPw, newPw, confirmPw) },
                            enabled = oldPw.isNotBlank() && newPw.isNotBlank() && !s.isLoading,
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                        ) {
                            if (s.isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                            else Text("ပြောင်းမည်")
                        }
                    }
                }
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
