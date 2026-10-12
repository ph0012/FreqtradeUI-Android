package com.freqtradeui.android.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP da API do Freqtrade, já apontado para a porta local do túnel.
 *
 * O [baseUrl] é sempre derivado do encaminhamento local (`127.0.0.1:<porta>`),
 * nunca do IP público da VPS: o tráfego precisa continuar dentro do túnel SSH.
 *
 * Timeouts curtos e explícitos: uma queda do túnel precisa virar erro visível,
 * não uma tela travada esperando indefinidamente.
 *
 * @param baseUrl URL base terminando em `/` (ex.: `http://127.0.0.1:18080/api/v1/`).
 * @param accessToken fornecedor do token JWT atual; `null` quando não autenticado.
 */
class FreqtradeApiClient(
    private val baseUrl: String,
    private val accessToken: () -> String? = { null },
) {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(accessToken))
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build(),
        )
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    /** Serviço REST tipado. */
    fun service(): FreqtradeApiService = retrofit.create(FreqtradeApiService::class.java)

    private companion object {
        /**
         * Timeout único, em segundos.
         *
         * Curto o bastante para a UI reagir a um túnel derrubado e longo o
         * bastante para a VPS responder em rede móvel.
         */
        const val TIMEOUT_SECONDS = 15L
    }
}