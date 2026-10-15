package com.kode.app.kode_app.retrofit.response

import com.google.gson.annotations.SerializedName

data class ClimaResponse(
    val daily: ClimaDiario?
)

data class ClimaDiario(
    val time: List<String>,
    @SerializedName("weather_code")
    val weatherCode: List<Int?>,
    @SerializedName("temperature_2m_max")
    val temperaturaMax: List<Double?>,
    @SerializedName("temperature_2m_min")
    val temperaturaMin: List<Double?>,
    @SerializedName("precipitation_probability_max")
    val probabilidadLluvia: List<Int?>
)
