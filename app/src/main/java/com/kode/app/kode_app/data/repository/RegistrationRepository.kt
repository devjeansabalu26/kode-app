package com.kode.app.kode_app.data.repository

import com.kode.app.kode_app.core.AppError
import com.kode.app.kode_app.core.AppException
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.core.appCall
import com.kode.app.kode_app.data.remote.AuthRemoteDataSource
import com.kode.app.kode_app.data.remote.RegistrationRemoteDataSource

class RegistrationRepository(
    private val remote: RegistrationRemoteDataSource = RegistrationRemoteDataSource(),
    private val auth: AuthRemoteDataSource = AuthRemoteDataSource()
) {
    /**
     * Inscribe al usuario con sesión y devuelve su código QR (si ya estaba inscrito, devuelve el mismo).
     * Las reglas (evento existe, no eres el creador, aforo) las valida la BD en una sola transacción.
     */
    suspend fun registerUserInEvent(eventId: Long): AppResult<String> = appCall {
        if (eventId <= 0) throw AppException(AppError.EVENT_NOT_FOUND)
        if (auth.currentUserId() == null) throw AppException(AppError.NOT_AUTHENTICATED)
        remote.register(eventId)
    }

    suspend fun getRegistrationQr(eventId: Long): AppResult<String?> = appCall {
        val userId = auth.currentUserId() ?: throw AppException(AppError.NOT_AUTHENTICATED)
        remote.qrFor(eventId, userId)
    }

    suspend fun isUserRegisteredInEvent(eventId: Long): AppResult<Boolean> = appCall {
        val userId = auth.currentUserId() ?: throw AppException(AppError.NOT_AUTHENTICATED)
        remote.qrFor(eventId, userId) != null
    }
}
