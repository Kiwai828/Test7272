package com.recapmaker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.recapmaker.app.data.local.TokenManager
import com.recapmaker.app.ui.auth.*
import com.recapmaker.app.ui.common.RecapTheme
import com.recapmaker.app.ui.dashboard.*
import com.recapmaker.app.ui.editor.EditorScreen
import com.recapmaker.app.ui.editor.EditorViewModel
import com.recapmaker.app.ui.history.HistoryScreen
import com.recapmaker.app.ui.history.HistoryViewModel
import com.recapmaker.app.ui.settings.SettingsScreen
import com.recapmaker.app.ui.subtitle.SubtitleScreen
import com.recapmaker.app.ui.subtitle.SubtitleViewModel
import com.recapmaker.app.ui.subtitle.SubtitleEditorScreen
import com.recapmaker.app.ui.subtitle.SubtitleEditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val hasToken = runBlocking { tokenManager.getToken() != null }

        setContent {
            RecapTheme {
                val nav = rememberNavController()

                NavHost(nav, startDestination = if (hasToken) "dashboard" else "login") {

                    composable("login") {
                        val vm: AuthViewModel = hiltViewModel()
                        LoginScreen(vm,
                            onRegister = { vm.resetState(); nav.navigate("register") },
                            onForgot = { vm.resetState(); nav.navigate("forgot_password") },
                            onSuccess = { nav.navigate("dashboard") { popUpTo("login") { inclusive = true } } },
                        )
                    }

                    composable("register") {
                        val vm: AuthViewModel = hiltViewModel()
                        RegisterScreen(vm,
                            onLogin = { vm.resetState(); nav.popBackStack() },
                            onSuccess = { nav.navigate("dashboard") { popUpTo("login") { inclusive = true } } },
                        )
                    }

                    composable("forgot_password") {
                        val vm: AuthViewModel = hiltViewModel()
                        ForgotPasswordScreen(vm, onBack = { nav.popBackStack() })
                    }

                    composable("dashboard") {
                        val vm: DashboardViewModel = hiltViewModel()
                        DashboardScreen(vm,
                            onEditor = { nav.navigate("editor") },
                            onSubtitle = { nav.navigate("subtitle") },
                            onHistory = { nav.navigate("history") },
                            onSettings = { nav.navigate("settings") },
                            onLogout = {
                                runBlocking { tokenManager.clear() }
                                nav.navigate("login") { popUpTo(0) { inclusive = true } }
                            },
                        )
                    }

                    composable("history") {
                        val vm: HistoryViewModel = hiltViewModel()
                        HistoryScreen(onBack = { nav.popBackStack() }, vm = vm)
                    }

                    composable("editor") {
                        val vm: EditorViewModel = hiltViewModel()
                        EditorScreen(onBack = { nav.popBackStack() }, onEditSubtitles = { nav.navigate("subtitle-editor") }, vm = vm)
                    }

                    composable("subtitle") {
                        val vm: SubtitleViewModel = hiltViewModel()
                        SubtitleScreen(onBack = { nav.popBackStack() }, onEditSubtitles = { nav.navigate("subtitle-editor") }, vm = vm)
                    }

                    composable("subtitle-editor") {
                        val vm: SubtitleEditorViewModel = hiltViewModel()
                        SubtitleEditorScreen(onBack = { nav.popBackStack() }, vm = vm)
                    }

                    composable("settings") {
                        val dashVm: DashboardViewModel = hiltViewModel()
                        val authVm: AuthViewModel = hiltViewModel()
                        SettingsScreen(
                            username = dashVm.state.username,
                            email = dashVm.state.email,
                            vm = authVm,
                            onBack = { nav.popBackStack() },
                            onLogout = {
                                runBlocking { tokenManager.clear() }
                                nav.navigate("login") { popUpTo(0) { inclusive = true } }
                            },
                        )
                    }
                }
            }
        }
    }
}
