package com.kode.app.kode_app.model

import java.io.Serializable

data class Event(
    val id: Int,
    val creatorId: Long?,
    val title: String,
    val description: String,
    val date: String,
    val endTime: String? = null,
    val location: String,
    val category: String,
    val capacity: Int,
    val eventType: String = "Presencial",
    val isPrivate: Boolean = false,
    val requireDni: Boolean = false,
    val isUntransferable: Boolean = false,
    val registeredCount: Int = 0,
    val attendedCount: Int = 0,
    val accessCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUrl: String? = null
) : Serializable {

    fun hasLocation(): Boolean = latitude != null && longitude != null

    fun bannerUrl(): String =
        if (!imageUrl.isNullOrBlank()) imageUrl else "https://picsum.photos/seed/kode$id/800/400"
}
