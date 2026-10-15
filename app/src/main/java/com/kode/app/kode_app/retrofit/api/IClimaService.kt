package com.kode.app.kode_app.retrofit.api

import com.kode.app.kode_app.retrofit.response.ClimaResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface IClimaService {
    @GET("forecast")
    fun obtenerPronostico(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") fecha: String,
        @Query("end_date") fechaFin: String,
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        @Query("timezone") timezone: String = "auto"
    ): Call<ClimaResponse>
}
