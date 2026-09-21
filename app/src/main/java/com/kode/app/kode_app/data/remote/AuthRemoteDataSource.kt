package com.kode.app.kode_app.data.remote

import com.kode.app.kode_app.data.remote.dto.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Resultado del registro en Supabase Auth. */
enum class SignUpOutcome {
    /** Cuenta creada y sesión iniciada (el proyecto NO exige confirmar el correo). */
    LOGGED_IN,

    /** Cuenta creada, pero hay que confirmar el correo antes de iniciar sesión. */
    CONFIRMATION_REQUIRED,

    /** Ese correo ya tenía una cuenta. */
    ALREADY_REGISTERED
}

/** Habla directamente con Supabase Auth y con la tabla `profiles`. */
class AuthRemoteDataSource(private val client: SupabaseClient = SupabaseProvider.client) {
    suspend fun awaitInitialization() {
        client.auth.awaitInitialization()
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    fun currentEmail(): String? = client.auth.currentUserOrNull()?.email

    fun currentName(): String? = client.auth.currentUserOrNull()?.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull

    /**
     * El perfil (nombre, DNI, ...) viaja como metadatos del alta; un trigger en la BD crea la fila de `profiles`.
     * El correo NO se envía como dato de perfil: el trigger lo toma de Auth (NEW.email), así la app no puede falsearlo.
     */
    suspend fun signUp(email: String, password: String, name: String, dni: String, phone: String, gender: String, age: Int): SignUpOutcome {
        val user = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("name", name)
                put("dni", dni)
                put("phone", phone)
                put("gender", gender)
                put("age", age)
            }
        }
        return when {
            user?.identities.isNullOrEmpty() -> SignUpOutcome.ALREADY_REGISTERED
            client.auth.currentSessionOrNull() != null -> SignUpOutcome.LOGGED_IN
            else -> SignUpOutcome.CONFIRMATION_REQUIRED
        }
    }

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    /** RPC pública: dice si un DNI está libre sin exponer la tabla `profiles`. */
    suspend fun isDniAvailable(dni: String): Boolean {
        return client.postgrest.rpc("is_dni_available", buildJsonObject { put("p_dni", dni) }).decodeAs<Boolean>()
    }

    suspend fun getProfile(userId: String): ProfileDto? {
        return client.postgrest.from("profiles").select { filter { eq("id", userId) } }.decodeSingleOrNull<ProfileDto>()
    }
}
