package com.freqtradeui.android.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injeta o token JWT no cabeçalho `Authorization` das requisições.
 *
 * O token é lido a cada requisição por um lambda: assim um token renovado passa
 * a valer imediatamente, sem recriar o cliente HTTP.
 *
 * O token nunca é registrado em log. Quando não há token, a requisição segue
 * sem o cabeçalho — é o caso de `/ping`, `/version` e do próprio login.
 *
 * @param accessToken token de acesso atual, ou `null` quando não autenticado.
 */
class AuthInterceptor(private val accessToken: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = accessToken()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header(HEADER_AUTHORIZATION, "$SCHEME_BEARER $token")
                .build()
        }
        return chain.proceed(request)
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val SCHEME_BEARER = "Bearer"
    }
}