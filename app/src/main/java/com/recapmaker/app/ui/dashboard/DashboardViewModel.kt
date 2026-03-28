package com.recapmaker.app.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recapmaker.app.data.local.TokenManager
import com.recapmaker.app.data.model.CoinPackage
import com.recapmaker.app.data.model.PricingTier
import com.recapmaker.app.data.repository.MainRepository
import com.recapmaker.app.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val username: String = "", val email: String? = null,
    val gold: Int = 0, val silver: Int = 0,
    val checkedInToday: Boolean = false, val checkinSilver: Int = 15,
    val pricingTiers: List<PricingTier> = emptyList(),
    val packages: List<CoinPackage> = emptyList(),
    val paymentMessage: String = "",
    val isLoading: Boolean = false, val error: String? = null,
    val checkinSuccess: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: MainRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {
    var state by mutableStateOf(DashboardState()); private set

    init { loadUserInfo() }

    fun loadUserInfo() {
        viewModelScope.launch {
            state = state.copy(isLoading = true)
            when (val r = repo.getUserInfo()) {
                is Result.Success -> state = state.copy(
                    isLoading = false, username = r.data.username, email = r.data.email,
                    gold = r.data.gold, silver = r.data.silver,
                    checkedInToday = r.data.checked_in_today, checkinSilver = r.data.checkin_silver,
                    pricingTiers = r.data.pricing_tiers, packages = r.data.packages,
                    paymentMessage = r.data.payment_message,
                )
                is Result.Error -> state = state.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun dailyCheckin() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, checkinSuccess = false)
            when (val r = repo.dailyCheckin()) {
                is Result.Success -> state = state.copy(
                    isLoading = false, gold = r.data.gold, silver = r.data.silver,
                    checkedInToday = true, checkinSuccess = true,
                )
                is Result.Error -> state = state.copy(isLoading = false, error = r.message)
            }
        }
    }

    suspend fun logout() { tokenManager.clear() }
    fun clearError() { state = state.copy(error = null) }
}
