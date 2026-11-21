package com.example.data.remote

import okhttp3.Interceptor
import okhttp3.Response

fun interface TokenProvider {
    fun getToken(): String?
}

class AuthInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", "PingDuluFieldOps-Android/1.0")

        if (original.header("Accept").isNullOrBlank()) {
            builder.header("Accept", "application/json")
        }

        val token = tokenProvider.getToken()?.trim()
        if (!token.isNullOrEmpty() && original.header("Authorization").isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }
}
