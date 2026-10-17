package com.kode.app.kode_app.core

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

class SessionManager(context: Context) {
    private val preferences = context.getSharedPreferences("kode_session", Context.MODE_PRIVATE)

    fun createSession(userId: Long, name: String, email: String) {
        preferences.edit().putBoolean("logged_in", true).putLong("user_id", userId).putString("name", name).putString("email", email).apply()
    }

    fun isLoggedIn(): Boolean {
        val loggedIn = preferences.getBoolean("logged_in", false)
        val userId = preferences.getLong("user_id", -1L)
        return loggedIn && userId > 0 && FirebaseAuth.getInstance().currentUser != null
    }

    fun getUserId(): Long {
        return preferences.getLong("user_id", -1L)
    }

    fun getName(): String {
        return preferences.getString(
            "name",
            ""
        ) ?: ""
    }

    fun getEmail(): String {
        return preferences.getString("email", "") ?: ""
    }

    fun logout() {
        FirebaseAuth.getInstance().signOut()
        preferences.edit().clear().apply()
    }
}