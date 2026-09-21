package com.kode.app.kode_app.model

/** Datos del usuario (tabla `profiles`, incluido el correo). La contraseña NO está aquí: es exclusiva de Supabase Auth. */
data class Profile(val id: String, val name: String, val dni: String, val phone: String, val gender: String, val age: Int, val email: String)
