package com.kode.app.kode_app.retrofit

import com.kode.app.kode_app.retrofit.api.IClimaService
import com.kode.app.kode_app.util.Constantes
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object ClienteClimaRetrofit {

    private var okHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildRetrofit() = Retrofit.Builder()
        .baseUrl(Constantes.URL_API_CLIMA)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val retrofitClimaService: IClimaService by lazy {
        buildRetrofit().create(IClimaService::class.java)
    }

    fun describirClima(code: Int?): String = when (code) {
        0 -> "☀️ Despejado"
        1, 2 -> "🌤️ Parcialmente nublado"
        3 -> "☁️ Nublado"
        45, 48 -> "🌫️ Neblina"
        51, 53, 55, 56, 57 -> "🌦️ Llovizna"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️ Lluvia"
        71, 73, 75, 77, 85, 86 -> "❄️ Nieve"
        95, 96, 99 -> "⛈️ Tormenta"
        else -> "🌡️ Sin datos"
    }
}
