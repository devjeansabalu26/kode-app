package com.kode.app.kode_app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.kode.app.kode_app.model.Event
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.random.Random

class EventRepository {
    private val events = FirestoreDb.db.collection(FirestoreDb.EVENTS)
    private val registrations = FirestoreDb.db.collection(FirestoreDb.REGISTRATIONS)
    private val users = UserRepository()
    private val published: Query get() = events.whereEqualTo("status", STATUS_PUBLISHED)
    private val publicEvents: Query get() = published.whereEqualTo("isPrivate", false)

    suspend fun getEvents(): List<Event> = fetch(published)

    suspend fun getPublicEvents(): List<Event> = fetch(publicEvents)

    suspend fun getEventById(eventId: Int): Event? = firestoreCall(null) {
        events.document(eventId.toString()).get().await()
            .takeIf { it.exists() && it.getString("status") == STATUS_PUBLISHED }
            ?.toEvent()
    }

    suspend fun getEventByAccessCode(code: String): Event? = firestoreCall(null) {
        published.whereEqualTo("accessCode", code.trim()).limit(1).get().await()
            .documents.firstOrNull()?.toEvent()
    }

    suspend fun getEventsByCategory(category: String): List<Event> =
        fetch(publicEvents.whereEqualTo("category", category))

    suspend fun getEventsCreatedByUser(userId: Long): List<Event> =
        fetch(published.whereEqualTo("creatorId", userId))

    suspend fun getEventsRegisteredByUser(userId: Long): List<Event> = firestoreCall(emptyList()) {
        val eventIds = registrations.whereEqualTo("userId", userId).get().await()
            .documents.mapNotNull { it.getLong("eventId") }
        FirestoreDb.getByIds(FirestoreDb.EVENTS, eventIds)
            .filter { it.getString("status") == STATUS_PUBLISHED }
            .map { it.toEvent() }
            .sortedBy { it.date }
    }

    suspend fun getCategories(): List<String> {
        return getPublicEvents()
            .map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    suspend fun eventExists(eventId: Int): Boolean = firestoreCall(false) {
        events.document(eventId.toString()).get().await().exists()
    }

    suspend fun createEvent(
        creatorId: Long,
        title: String,
        description: String,
        date: String,
        location: String,
        category: String,
        capacity: Int,
        endTime: String = "",
        eventType: String = "Presencial",
        isPrivate: Boolean = false,
        requireDni: Boolean = false,
        isUntransferable: Boolean = false,
        accessCode: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        imageUrl: String? = null
    ): Long = firestoreCall(-1L) {
        if (creatorId <= 0 || users.getUserById(creatorId) == null) return@firestoreCall -1L

        val finalAccessCode = if (isPrivate) {
            generateUniqueAccessCode(title)
        } else {
            ""
        }

        val id = FirestoreDb.nextId(FirestoreDb.EVENTS)
        val event = mapOf(
            "id" to id,
            "creatorId" to creatorId,
            "title" to title.trim(),
            "description" to description.trim(),
            "date" to date,
            "endTime" to endTime,
            "location" to location.trim(),
            "category" to category.trim(),
            "capacity" to capacity,
            "eventType" to eventType,
            "isPrivate" to isPrivate,
            "requireDni" to requireDni,
            "isUntransferable" to isUntransferable,
            "accessCode" to finalAccessCode,
            "latitude" to latitude,
            "longitude" to longitude,
            "imageUrl" to imageUrl?.trim()?.ifEmpty { null },
            "status" to STATUS_PUBLISHED,
            "registeredCount" to 0,
            "attendedCount" to 0,
            "createdAt" to FirestoreDb.currentDateTime()
        )
        events.document(id.toString()).set(event).await()
        id
    }

    private suspend fun generateUniqueAccessCode(title: String): String {

        val cleanTitle = title.trim().replace(Regex("[^a-zA-Z0-9]"), "").uppercase(Locale.getDefault())
        val prefix = when {
            cleanTitle.length >= 3 -> cleanTitle.substring(0, 3)
            cleanTitle.isNotEmpty() -> cleanTitle.padEnd(3, 'X')
            else -> "KOD"
        }

        var candidateCode: String
        do {
            val randomNumber = Random.nextInt(100, 1000)
            candidateCode = "$prefix$randomNumber"
        } while (!events.whereEqualTo("accessCode", candidateCode).limit(1).get().await().isEmpty)

        return candidateCode
    }

    private suspend fun fetch(query: Query): List<Event> = firestoreCall(emptyList()) {
        query.get().await().documents.map { it.toEvent() }.sortedBy { it.date }
    }

    companion object {
        private const val STATUS_PUBLISHED = "PUBLISHED"
    }
}

internal fun DocumentSnapshot.toEvent(): Event {
    return Event(
        id = (getLong("id") ?: 0L).toInt(),
        creatorId = getLong("creatorId"),
        title = getString("title").orEmpty(),
        description = getString("description").orEmpty(),
        date = getString("date").orEmpty(),
        endTime = getString("endTime"),
        location = getString("location").orEmpty(),
        category = getString("category").orEmpty(),
        capacity = (getLong("capacity") ?: 0L).toInt(),
        eventType = getString("eventType") ?: "Presencial",
        isPrivate = getBoolean("isPrivate") ?: false,
        requireDni = getBoolean("requireDni") ?: false,
        isUntransferable = getBoolean("isUntransferable") ?: false,
        registeredCount = (getLong("registeredCount") ?: 0L).toInt(),
        attendedCount = (getLong("attendedCount") ?: 0L).toInt(),
        accessCode = getString("accessCode"),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        imageUrl = getString("imageUrl")
    )
}
