package com.recapmaker.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recapmaker.app.ui.common.*

@Composable
fun LoginScreen(vm: AuthViewModel, onRegister: () -> Unit, onForgot: () -> Unit, onSuccess: () -> Unit) {
    val s = vm.state
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val fm = LocalFocusManager.current
    LaunchedEffect(s.loginSuccess) { if (s.loginSuccess) onSuccess() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500), initialOffset = { it / 3 }), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎬", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text("Recap Maker", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Video Editor & Subtitle Generator", fontSize = 13.sp, color = TextDim)
            Spacer(Modifier.height(32.dp))
            AppTextField(username, { username = it }, "Username or Email")
            Spacer(Modifier.height(12.dp))
            PasswordField(password, { password = it }, onImeAction = { fm.clearFocus(); vm.login(username.trim(), password) })
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("Forgot Password?", color = Purple, fontSize = 13.sp, modifier = Modifier.clickable { onForgot() }.padding(vertical = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            ErrorBanner(s.error)
            PrimaryButton("Login", { fm.clearFocus(); vm.login(username.trim(), password) }, s.isLoading)
            Spacer(Modifier.height(20.dp))
            Row {
                Text("Account မရှိသေးဘူးလား? ", color = TextDim, fontSize = 14.sp)
                Text("Register", color = Purple, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onRegister() })
            }
        }
    }
}
}
