package com.kode.app.kode_app.data.remote

import com.kode.app.kode_app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Único cliente de Supabase de toda la app.
 * Solo usa la URL y la Publishable Key (públicas). La seguridad real la ponen Auth + RLS en la base de datos.
 * Los valores vienen de local.properties → BuildConfig (ver app/build.gradle.kts).
 */
object SupabaseProvider {
    val client: SupabaseClient by lazy {
        check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Falta SUPABASE_URL o SUPABASE_PUBLISHABLE_KEY en local.properties"
        }
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            install(Auth)
            install(Postgrest)
        }
    }
}
