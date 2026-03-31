package com.recapmaker.app.ui.settings

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

data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val linkEmailSuccess: Boolean = false,
    val changePwSuccess: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: AuthRepository) : ViewModel() {
    var state by mutableStateOf(SettingsUiState()); private set

    fun linkEmail(email: String) {
        if (email.isBlank()) { state = state.copy(error = "Email ဖြည့်ပါ"); return }
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            when (val r = repo.linkEmail(email)) {
                is Result.Success -> state = state.copy(isLoading = false, linkEmailSuccess = true, success = "Email ချိတ်ဆက်ပြီးပါပြီ!")
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
                is Result.Success -> state = state.copy(isLoading = false, changePwSuccess = true, success = "Password ပြောင်းပြီးပါပြီ!")
                is Result.Error -> state = state.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun clearMessages() { state = state.copy(error = null, success = null) }
}
