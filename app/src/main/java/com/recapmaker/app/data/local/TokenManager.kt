package com.recapmaker.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "recap_prefs")

@Singleton
class TokenManager @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_VOXCPM2_TOKEN = stringPreferencesKey("voxcpm2_access_token")
    }
    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    val isLoggedIn: Flow<Boolean> = tokenFlow.map { !it.isNullOrEmpty() }
    suspend fun getToken(): String? = tokenFlow.first()
    suspend fun getUsername(): String? = context.dataStore.data.first()[KEY_USERNAME]
    suspend fun getVoxCpm2Token(): String? = normalizeVoiceToken(context.dataStore.data.first()[KEY_VOXCPM2_TOKEN])
    suspend fun saveToken(token: String, username: String = "") {
        context.dataStore.edit { it[KEY_TOKEN] = token; if (username.isNotEmpty()) it[KEY_USERNAME] = username }
    }
    suspend fun saveVoxCpm2Token(token: String) {
        val normalized = normalizeVoiceToken(token) ?: return
        context.dataStore.edit { it[KEY_VOXCPM2_TOKEN] = normalized }
    }
    suspend fun clearVoxCpm2Token() { context.dataStore.edit { it.remove(KEY_VOXCPM2_TOKEN) } }

    private fun normalizeVoiceToken(raw: String?): String? {
        var token = raw?.trim().orEmpty()
        if (token.startsWith("Authorization:", ignoreCase = true)) {
            token = token.substringAfter(':').trim()
        }
        if (token.startsWith("Bearer ", ignoreCase = true)) {
            token = token.substring(7).trim()
        }
        token = token.replace(Regex("\\s+"), "")
        return token.takeIf { it.isNotEmpty() }
    }
    suspend fun clear() { context.dataStore.edit { it.clear() } }
}
