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
 * sem o cabeçalho — é o caso de endpoints públicos. Cabeçalhos explícitos são
 * preservados para autenticação Basic e uso do refresh token.
 *
 * @param accessToken token de acesso atual, ou `null` quando não autenticado.
 */
class AuthInterceptor(private val accessToken: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = accessToken()
        val original = chain.request()
        val explicitAuthorization = original.headers.values(HEADER_AUTHORIZATION).firstOrNull()
        val request = when {
            explicitAuthorization != null ->
                original.newBuilder().header(HEADER_AUTHORIZATION, explicitAuthorization).build()
            token.isNullOrBlank() -> original
            else ->
                original.newBuilder()
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