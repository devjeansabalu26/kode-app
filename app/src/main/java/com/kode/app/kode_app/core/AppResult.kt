package com.kode.app.kode_app.core

import android.util.Log
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Resultado de una operación remota: o salió bien (Success) o falló con un error tipado (Failure). */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Errores que la app sabe distinguir. `userMessage` es el texto amigable que ve el usuario. */
enum class AppError(val userMessage: String) {
    NO_INTERNET("Sin conexión a internet. Revisa tu red e inténtalo de nuevo"),
    NOT_AUTHENTICATED("Inicia sesión para continuar"),
    INVALID_CREDENTIALS("Correo o contraseña incorrectos"),
    EMAIL_NOT_CONFIRMED("Confirma tu correo desde el mensaje que te enviamos y vuelve a iniciar sesión"),
    EMAIL_TAKEN("Este correo ya está registrado"),
    DNI_TAKEN("Este DNI ya está registrado"),
    PROFILE_NOT_FOUND("No encontramos los datos de tu perfil"),
    EVENT_NOT_FOUND("El evento no existe o ya no está disponible"),
    EVENT_FULL("El evento alcanzó su capacidad máxima"),
    ALREADY_REGISTERED("Ya estás inscrito en este evento"),
    IS_CREATOR("Eres el creador de este evento"),
    CAPACITY_BELOW_REGISTERED("La capacidad no puede ser menor a los inscritos actuales"),
    FORBIDDEN("No tienes permiso para realizar esta acción"),
    SERVER("El servidor tuvo un problema. Inténtalo de nuevo en unos minutos"),
    UNKNOWN("Ocurrió un error inesperado. Inténtalo de nuevo")
}

/** Para lanzar un error tipado desde dentro de un `appCall { }`. */
class AppException(val error: AppError) : Exception(error.name)

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

/** Ejecuta una llamada remota y convierte cualquier excepción en un `AppResult.Failure`. Nunca se traga la cancelación. */
suspend fun <T> appCall(block: suspend () -> T): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppException) {
        AppResult.Failure(e.error)
    } catch (e: Exception) {
        Log.w("KodeApp", "Operación remota fallida: ${e.javaClass.simpleName}: ${e.message}")
        AppResult.Failure(e.toAppError())
    }
}

fun Throwable.toAppError(): AppError {
    val text = (message ?: "").lowercase()
    return when (this) {
        is HttpRequestException, is HttpRequestTimeoutException -> AppError.NO_INTERNET
        is AuthRestException -> when {
            "invalid login credentials" in text || "invalid_credentials" in text -> AppError.INVALID_CREDENTIALS
            "email not confirmed" in text || "email_not_confirmed" in text -> AppError.EMAIL_NOT_CONFIRMED
            "already registered" in text || "already exists" in text || "user_already_exists" in text -> AppError.EMAIL_TAKEN
            statusCode == 401 || statusCode == 403 -> AppError.NOT_AUTHENTICATED
            statusCode >= 500 -> AppError.SERVER
            else -> AppError.UNKNOWN
        }
        is PostgrestRestException -> when {
            "event_full" in text -> AppError.EVENT_FULL
            "event_not_found" in text -> AppError.EVENT_NOT_FOUND
            "is_creator" in text -> AppError.IS_CREATOR
            "capacity_below_registered" in text -> AppError.CAPACITY_BELOW_REGISTERED
            "not_authenticated" in text -> AppError.NOT_AUTHENTICATED
            "registrations_event_id_user_id" in text -> AppError.ALREADY_REGISTERED
            "dni" in text && "duplicate" in text -> AppError.DNI_TAKEN
            code == "42501" || statusCode == 401 || statusCode == 403 -> AppError.FORBIDDEN
            statusCode >= 500 -> AppError.SERVER
            else -> AppError.UNKNOWN
        }
        is RestException -> if (statusCode >= 500) AppError.SERVER else AppError.UNKNOWN
        is IOException -> AppError.NO_INTERNET
        else -> AppError.UNKNOWN
    }
}
