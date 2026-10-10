package com.kode.app.kode_app.model

data class AttendeeItem(
    val user: User,
    val ticketCode: String,
    var status: String
)