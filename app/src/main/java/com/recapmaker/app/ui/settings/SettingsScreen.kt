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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.recapmaker.app.ui.auth.AuthViewModel
import com.recapmaker.app.ui.common.*

@Composable
fun SettingsScreen(
    username: String,
    email: String?,
    vm: AuthViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onEmailLinked: () -> Unit = {},
) {
    val s = vm.state
    var showChangePw by remember { mutableStateOf(false) }
    var showLinkEmail by remember { mutableStateOf(false) }
    var currentEmail by remember { mutableStateOf(email ?: "") }
    // Close dialogs on success
    LaunchedEffect(s.linkEmailDone) {
        if (s.linkEmailDone) {
            currentEmail = email ?: "" // will refresh from server
            showLinkEmail = false
            vm.resetLinkEmailDone()
            onEmailLinked()
        }
    }
    LaunchedEffect(s.changePasswordDone) {
        if (s.changePasswordDone) {
            showChangePw = false
            vm.resetChangePasswordDone()
        }
    }

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
                        SettingsItem(Icons.Default.Email, "Link Email", "Password recovery အတွက်") { showLinkEmail = true }
                        HorizontalDivider(color = CardBorder)
                    }
                    SettingsItem(Icons.Default.Lock, "Change Password", "") { showChangePw = true }
                    HorizontalDivider(color = CardBorder)
                    SettingsItem(Icons.Default.Info, "App Version", "2.2.0") { }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Logout", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Link Email Dialog ──
    if (showLinkEmail) {
        var emailInput by remember { mutableStateOf("") }
        LaunchedEffect(showLinkEmail) { vm.clearError() }

        Dialog(onDismissRequest = { showLinkEmail = false; vm.clearError() }) {
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Email ချိတ်ဆက်ရန်", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Password မေ့ပါက ဤ email ဖြင့် ပြန်ယူနိုင်မည်", fontSize = 12.sp, color = TextDim)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple,
                            focusedLabelColor = Purple, unfocusedLabelColor = TextDim,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    s.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = ErrorRed, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showLinkEmail = false; vm.clearError() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardBorder),
                        ) { Text("Cancel", color = TextDim) }
                        Button(
                            onClick = { vm.linkEmail(emailInput.trim()) },
                            modifier = Modifier.weight(1f),
                            enabled = emailInput.isNotBlank() && !s.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Purple),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (s.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Link")
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
        LaunchedEffect(showChangePw) { vm.clearError() }

        Dialog(onDismissRequest = { showChangePw = false; vm.clearError() }) {
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Password ပြောင်းရန်", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = oldPw,
                        onValueChange = { oldPw = it },
                        label = { Text("လက်ရှိ Password", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple,
                            focusedLabelColor = Purple, unfocusedLabelColor = TextDim,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPw,
                        onValueChange = { newPw = it },
                        label = { Text("Password အသစ်", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple,
                            focusedLabelColor = Purple, unfocusedLabelColor = TextDim,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPw,
                        onValueChange = { confirmPw = it },
                        label = { Text("Password အသစ် အတည်ပြု", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple, unfocusedBorderColor = CardBorder, cursorColor = Purple,
                            focusedLabelColor = Purple, unfocusedLabelColor = TextDim,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    s.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = ErrorRed, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showChangePw = false; vm.clearError() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardBorder),
                        ) { Text("Cancel", color = TextDim) }
                        Button(
                            onClick = { vm.changePassword(oldPw, newPw, confirmPw) },
                            modifier = Modifier.weight(1f),
                            enabled = oldPw.isNotBlank() && newPw.isNotBlank() && !s.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Purple),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (s.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("ပြောင်းရန်")
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

