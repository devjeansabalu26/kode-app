package com.kode.app.kode_app.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FirestoreDb {
    const val USERS = "users"
    const val EVENTS = "events"
    const val REGISTRATIONS = "registrations"
    private const val COUNTERS = "counters"

    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun nextId(collection: String): Long {
        val counterRef = db.collection(COUNTERS).document(collection)
        return db.runTransaction { transaction ->
            val next = (transaction.get(counterRef).getLong("value") ?: 0L) + 1
            transaction.set(counterRef, mapOf("value" to next))
            next
        }.await()
    }

    suspend fun getByIds(collection: String, ids: List<Long>): List<DocumentSnapshot> {
        return ids.distinct().chunked(30).flatMap { chunk ->
            db.collection(collection)
                .whereIn(FieldPath.documentId(), chunk.map { it.toString() })
                .get().await().documents
        }
    }

    fun currentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}

internal inline fun <T> firestoreCall(default: T, block: () -> T): T {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Firestore", "Error en Firestore", e)
        default
    }
}
