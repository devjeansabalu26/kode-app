package com.kode.app.kode_app.retrofit

import com.kode.app.kode_app.retrofit.api.ICloudinaryService
import com.kode.app.kode_app.util.Constantes
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object ClienteCloudinaryRetrofit {

    private var okHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    private fun buildRetrofit() = Retrofit.Builder()
        .baseUrl(Constantes.URL_API_CLOUDINARY)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val retrofitCloudinaryService: ICloudinaryService by lazy {
        buildRetrofit().create(ICloudinaryService::class.java)
    }
}
