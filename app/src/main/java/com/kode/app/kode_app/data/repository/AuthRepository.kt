package com.kode.app.kode_app.data.repository

import com.kode.app.kode_app.core.AppError
import com.kode.app.kode_app.core.AppException
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.core.appCall
import com.kode.app.kode_app.data.remote.AuthRemoteDataSource
import com.kode.app.kode_app.data.remote.SignUpOutcome
import com.kode.app.kode_app.model.Profile

/** Datos del formulario de registro (la contraseña solo viaja hacia Supabase Auth, nunca se guarda en la app). */
data class RegisterForm(val name: String, val dni: String, val phone: String, val gender: String, val age: Int, val email: String, val password: String)

class AuthRepository(private val remote: AuthRemoteDataSource = AuthRemoteDataSource()) {
    /** Espera a que el SDK termine de cargar la sesión guardada (al abrir la app). */
    suspend fun awaitReady(): AppResult<Unit> = appCall { remote.awaitInitialization() }

    suspend fun register(form: RegisterForm): AppResult<SignUpOutcome> = appCall {
        if (!remote.isDniAvailable(form.dni)) throw AppException(AppError.DNI_TAKEN)
        val outcome = remote.signUp(form.email.trim().lowercase(), form.password, form.name, form.dni, form.phone, form.gender, form.age)
        if (outcome == SignUpOutcome.ALREADY_REGISTERED) throw AppException(AppError.EMAIL_TAKEN)
        outcome
    }

    suspend fun login(email: String, password: String): AppResult<Unit> = appCall { remote.signIn(email.trim().lowercase(), password) }

    suspend fun logout(): AppResult<Unit> = appCall { remote.signOut() }

    /** Perfil del usuario con sesión: fila de `profiles` (nombre, DNI, celular, género, edad y correo). */
    suspend fun getCurrentProfile(): AppResult<Profile> = appCall {
        val userId = remote.currentUserId() ?: throw AppException(AppError.NOT_AUTHENTICATED)
        val dto = remote.getProfile(userId) ?: throw AppException(AppError.PROFILE_NOT_FOUND)
        Profile(dto.id, dto.name, dto.dni, dto.phone, dto.gender, dto.age, dto.email)
    }
}
