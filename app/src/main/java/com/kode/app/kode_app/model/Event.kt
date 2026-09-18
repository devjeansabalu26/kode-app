package com.kode.app.kode_app.model

data class Event(
    val id: Int,
    val creatorId: Long?,
    val title: String,
    val description: String,
    val date: String,
    val location: String,
    val category: String,
    val capacity: Int,
    val registeredCount: Int = 0,
    val attendedCount: Int = 0
)