package com.recapmaker.app.data.repository

import com.recapmaker.app.data.api.RecapApi
import com.recapmaker.app.data.local.TokenManager
import com.recapmaker.app.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

fun extractDetail(json: String): String = try {
    """"detail"\s*:\s*"([^"]+)"""".toRegex().find(json)?.groupValues?.get(1) ?: json
} catch (_: Exception) { json }

@Singleton
class AuthRepository @Inject constructor(private val api: RecapApi, private val tokenManager: TokenManager) {

    suspend fun login(username: String, password: String): Result<AuthResponse> = try {
        val r = api.login(LoginRequest(username, password))
        if (r.isSuccessful) { r.body()!!.also { if (it.token != null) tokenManager.saveToken(it.token, it.username ?: "") }.let { Result.Success(it) } }
        else Result.Error(extractDetail(r.errorBody()?.string() ?: "Login failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun register(username: String, password: String, email: String?): Result<AuthResponse> = try {
        val r = api.register(RegisterRequest(username, password, email))
        if (r.isSuccessful) { r.body()!!.also { if (it.token != null) tokenManager.saveToken(it.token, username) }.let { Result.Success(it) } }
        else Result.Error(extractDetail(r.errorBody()?.string() ?: "Register failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun forgotPassword(email: String): Result<MessageResponse> = try {
        val r = api.forgotPassword(ForgotPasswordRequest(email))
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "Failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun resetPassword(email: String, code: String, pw: String): Result<MessageResponse> = try {
        val r = api.resetPassword(ResetPasswordRequest(email, code, pw))
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "Failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun linkEmail(email: String): Result<MessageResponse> = try {
        val r = api.linkEmail(LinkEmailRequest(email))
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "Failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun changePassword(oldPw: String, newPw: String): Result<MessageResponse> = try {
        val r = api.changePassword(ChangePasswordRequest(oldPw, newPw))
        if (r.isSuccessful) Result.Success(r.body()!!) else Result.Error(extractDetail(r.errorBody()?.string() ?: "Failed"))
    } catch (e: Exception) { Result.Error(e.message ?: "Network error") }

    suspend fun logout() { tokenManager.clear() }
}
