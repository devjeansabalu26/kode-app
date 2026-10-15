package com.kode.app.kode_app.data

import android.content.Context
import com.kode.app.kode_app.db.KodeRoomDatabase
import com.kode.app.kode_app.db.entity.EventEntity
import com.kode.app.kode_app.model.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class EventRepository(context: Context) {
    private val eventDao = KodeRoomDatabase.getDatabase(context).eventDao()
    private val users = UserRepository(context)

    fun getEvents(): List<Event> = eventDao.listarTodos()


    fun getPublicEvents(): List<Event> = eventDao.listarPublicos()

    fun getEventById(eventId: Int): Event? = eventDao.obtenerPorId(eventId)


    fun getEventByAccessCode(code: String): Event? = eventDao.obtenerPorCodigo(code.trim())


    fun getEventsByCategory(category: String): List<Event> = eventDao.listarPorCategoria(category)

    fun getEventsCreatedByUser(userId: Long): List<Event> = eventDao.listarCreadosPor(userId)

    fun getEventsRegisteredByUser(userId: Long): List<Event> = eventDao.listarInscritosPor(userId)

    fun getCategories(): List<String> = eventDao.listarCategorias()

    fun eventExists(eventId: Int): Boolean = eventDao.existe(eventId)

    fun createEvent(
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
    ): Long {
        if (creatorId <= 0 || users.getUserById(creatorId) == null) return -1L

        // Generar código único even priv
        val finalAccessCode = if (isPrivate) {
            generateUniqueAccessCode(title)
        } else {
            ""
        }

        val event = EventEntity(
            creatorId = creatorId,
            title = title.trim(),
            description = description.trim(),
            date = date,
            endTime = endTime,
            location = location.trim(),
            category = category.trim(),
            capacity = capacity,
            eventType = eventType,
            isPrivate = isPrivate,
            requireDni = requireDni,
            isUntransferable = isUntransferable,
            accessCode = finalAccessCode,
            latitude = latitude,
            longitude = longitude,
            imageUrl = imageUrl?.trim()?.ifEmpty { null },
            status = "PUBLISHED",
            createdAt = currentDateTime()
        )
        return eventDao.insertar(event)
    }

    /**
     * Genera un código automático único basado en las 3 primeras letras del título + 3 dígitos aleatorios ojo acaaaaaaaa.
     * Ejemplo: "Cibercafe inaguracion" -> "CIB123"
     * Verifica que el código no exista previamente en la base de datos */
    private fun generateUniqueAccessCode(title: String): String {

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
        } while (eventDao.existeCodigo(candidateCode))

        return candidateCode
    }

    private fun currentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
