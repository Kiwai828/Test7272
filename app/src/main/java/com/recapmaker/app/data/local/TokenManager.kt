package com.recapmaker.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "recap_prefs")

@Singleton
class TokenManager @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_USERNAME = stringPreferencesKey("username")
    }
    @Volatile private var cachedToken: String? = null
    private val isCacheLoaded = AtomicBoolean(false)
    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    val isLoggedIn: Flow<Boolean> = tokenFlow.map { !it.isNullOrEmpty() }
    suspend fun getToken(): String? = tokenFlow.first()
    suspend fun initCache() {
        if (isCacheLoaded.compareAndSet(false, true)) {
            cachedToken = tokenFlow.first()
        }
    }
    fun getCachedToken(): String? = cachedToken
    suspend fun getUsername(): String? = context.dataStore.data.first()[KEY_USERNAME]
    suspend fun saveToken(token: String, username: String = "") {
        context.dataStore.edit { it[KEY_TOKEN] = token; if (username.isNotEmpty()) it[KEY_USERNAME] = username }
        cachedToken = token
    }
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
        cachedToken = null
        isCacheLoaded.set(false)
    }
}
