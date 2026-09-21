package com.kode.app.kode_app.core

import com.kode.app.kode_app.data.remote.AuthRemoteDataSource

/**
 * Lectura rápida (sin red) de la sesión de Supabase Auth.
 * La sesión y los tokens los guarda y renueva el SDK de Supabase; esta clase NO guarda nada en SharedPreferences.
 */
class SessionManager(private val auth: AuthRemoteDataSource = AuthRemoteDataSource()) {
    fun isLoggedIn(): Boolean = auth.currentUserId() != null

    /** UUID del usuario con sesión, o null si no hay sesión. */
    fun getUserId(): String? = auth.currentUserId()

    fun getName(): String = auth.currentName().orEmpty()

    fun getEmail(): String = auth.currentEmail().orEmpty()
}
