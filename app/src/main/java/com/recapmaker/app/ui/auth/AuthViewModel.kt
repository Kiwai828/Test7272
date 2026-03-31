package com.recapmaker.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recapmaker.app.data.repository.AuthRepository
import com.recapmaker.app.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false, val error: String? = null,
    val loginSuccess: Boolean = false, val emailMissing: Boolean = false,
    val resetCodeSent: Boolean = false, val passwordReset: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(private val repo: AuthRepository) : ViewModel() {
    var state by mutableStateOf(AuthUiState()); private set

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) { state = state.copy(error = "Username နှင့် password ဖြည့်ပါ"); return }
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            when (val r = repo.login(username, password)) {
                is Result.Success -> state = state.copy(isLoading = false, loginSuccess = true, emailMissing = r.data.email_missing)
                is Result.Error -> state = state.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun register(username: String, password: String, confirmPw: String, email: String?) {
        if (username.length < 3) { state = state.copy(error = "Username အနည်းဆုံး ၃ လုံး"); return }
        if (password.length < 4) { state = state.copy(error = "Password အနည်းဆုံး ၄ လုံး"); return }
        if (password != confirmPw) { state = state.copy(error = "Password များ မတူပါ"); return }
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            when (val r = repo.register(username, password, email?.ifBlank { null })) {
                is Result.Success -> state = state.copy(isLoading = false, loginSuccess = true)
                is Result.Error -> state = state.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank()) { state = state.copy(error = "Email ဖြည့်ပါ"); return }
        // Basic email format validation
        if (!email.contains("@") || !email.contains(".")) {
            state = state.copy(error = "Email format မှားနေသည်")
            return
        }
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            // FIX: was calling repo.forgotPassword(email) TWICE — once in when(), once in Error branch
            // This caused double API call: one to send email, one that never sends (race condition)
            when (val r = repo.forgotPassword(email)) {
                is Result.Success -> state = state.copy(isLoading = false, resetCodeSent = true)
                is Result.Error -> state = state.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun resetPassword(email: String, code: String, newPw: String, confirmPw: String) {
        if (code.length != 6) { state = state.copy(error = "Code ၆ လုံး ဖြည့်ပါ"); return }
        if (newPw.length < 4) { state = state.copy(error = "Password အနည်းဆုံး ၄ လုံး"); return }
        if (newPw != confirmPw) { state = state.copy(error = "Password များ မတူပါ"); return }
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            // FIX: same double-call bug — capture result once, use it
            when (val r = repo.resetPassword(email, code, newPw)) {
                is Result.Success -> state = state.copy(isLoading = false, passwordReset = true)
                is Result.Error -> state = state.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun linkEmail(email: String) {
        if (email.isBlank()) { state = state.copy(error = "Email ဖြည့်ပါ"); return }
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            when (val r = repo.linkEmail(email)) {
                is Result.Success -> state = state.copy(isLoading = false, linkEmailSuccess = true)
                is Result.Error -> state = state.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun changePassword(oldPw: String, newPw: String, confirmPw: String) {
        if (oldPw.isBlank()) { state = state.copy(error = "လက်ရှိ Password ဖြည့်ပါ"); return }
        if (newPw.length < 4) { state = state.copy(error = "Password အနည်းဆုံး ၄ လုံး"); return }
        if (newPw != confirmPw) { state = state.copy(error = "Password များ မတူပါ"); return }
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            when (val r = repo.changePassword(oldPw, newPw)) {
                is Result.Success -> state = state.copy(isLoading = false, changePwSuccess = true)
                is Result.Error -> state = state.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun clearLinkEmailSuccess() { state = state.copy(linkEmailSuccess = false) }
    fun clearChangePwSuccess() { state = state.copy(changePwSuccess = false) }
    fun clearError() { state = state.copy(error = null) }
    fun resetState() { state = AuthUiState() }
}
