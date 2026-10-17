package com.kode.app.kode_app.data

import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.kode.app.kode_app.model.User
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val users = FirestoreDb.db.collection(FirestoreDb.USERS)
    private val auth = FirebaseAuth.getInstance()

    suspend fun registerUser(
        name: String,
        dni: String,
        phone: String,
        gender: String,
        age: Int,
        email: String,
        password: String
    ): Long = firestoreCall(-1L) {
        val normalizedEmail = email.trim().lowercase()
        if (userExistsByEmail(normalizedEmail) || userExistsByDni(dni)) return@firestoreCall -1L

        val authUser = auth.createUserWithEmailAndPassword(normalizedEmail, password).await().user
            ?: return@firestoreCall -1L
        try {
            val id = FirestoreDb.nextId(FirestoreDb.USERS)
            val user = mapOf(
                "id" to id,
                "authUid" to authUser.uid,
                "name" to name.trim(),
                "dni" to dni.trim(),
                "phone" to phone.trim(),
                "gender" to gender.trim(),
                "age" to age,
                "email" to normalizedEmail,
                "role" to "ASISTENTE",
                "createdAt" to FirestoreDb.currentDateTime()
            )
            users.document(id.toString()).set(user).await()
            id
        } catch (e: Exception) {
            authUser.delete()
            throw e
        }
    }

    suspend fun userExistsByEmail(email: String): Boolean = firestoreCall(false) {
        !users.whereEqualTo("email", email.trim().lowercase()).limit(1).get().await().isEmpty
    }

    suspend fun userExistsByDni(dni: String): Boolean = firestoreCall(false) {
        !users.whereEqualTo("dni", dni.trim()).limit(1).get().await().isEmpty
    }

    suspend fun login(email: String, password: String): User? = firestoreCall(null) {
        val uid = auth.signInWithEmailAndPassword(email.trim().lowercase(), password).await().user?.uid
            ?: return@firestoreCall null
        val user = users.whereEqualTo("authUid", uid).limit(1).get().await()
            .documents.firstOrNull()?.toUser()
        if (user == null) auth.signOut()
        user
    }

    suspend fun getUserById(userId: Long): User? = firestoreCall(null) {
        users.document(userId.toString()).get().await()
            .takeIf { it.exists() }?.toUser()
    }

    fun observeUser(userId: Long): LiveData<User?> {
        val userRef = users.document(userId.toString())
        return object : LiveData<User?>() {
            private var registration: ListenerRegistration? = null

            override fun onActive() {
                registration = userRef.addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    if (!snapshot.exists() && snapshot.metadata.isFromCache) return@addSnapshotListener
                    value = snapshot.takeIf { it.exists() }?.toUser()
                }
            }

            override fun onInactive() {
                registration?.remove()
                registration = null
            }
        }
    }
}

internal fun DocumentSnapshot.toUser(): User {
    return User(
        id = getLong("id") ?: 0L,
        name = getString("name").orEmpty(),
        dni = getString("dni").orEmpty(),
        phone = getString("phone").orEmpty(),
        gender = getString("gender").orEmpty(),
        age = (getLong("age") ?: 0L).toInt(),
        email = getString("email").orEmpty()
    )
}
