package com.recapmaker.app.data.api

import com.recapmaker.app.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(private val tokenManager: TokenManager) : Interceptor {
    private val publicPaths = listOf("api/register", "api/login", "api/forgot-password", "api/reset-password", "api/config", "api/health")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (publicPaths.any { request.url.encodedPath.contains(it) }) return chain.proceed(request)
        val token = tokenManager.latestToken
        return if (!token.isNullOrEmpty()) {
            chain.proceed(request.newBuilder().addHeader("Authorization", "Bearer $token").build())
        } else chain.proceed(request)
    }
}
