package com.kode.app.kode_app.data.repository

import com.kode.app.kode_app.core.AppError
import com.kode.app.kode_app.core.AppException
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.core.appCall
import com.kode.app.kode_app.data.remote.EventRemoteDataSource
import com.kode.app.kode_app.data.remote.RegistrationRemoteDataSource
import com.kode.app.kode_app.data.remote.dto.EventDto
import com.kode.app.kode_app.data.remote.dto.EventWriteDto
import com.kode.app.kode_app.model.Event
import java.time.Instant
import java.time.OffsetDateTime

class EventRepository(
    private val remote: EventRemoteDataSource = EventRemoteDataSource(),
    private val registrations: RegistrationRemoteDataSource = RegistrationRemoteDataSource()
) {
    suspend fun getEvents(): AppResult<List<Event>> = appCall { remote.list().map { it.toEvent() } }

    suspend fun getEventById(eventId: Long): AppResult<Event> = appCall {
        remote.byId(eventId)?.toEvent() ?: throw AppException(AppError.EVENT_NOT_FOUND)
    }

    suspend fun getEventsByCategory(category: String): AppResult<List<Event>> = appCall { remote.byCategory(category).map { it.toEvent() } }

    suspend fun getEventsCreatedByUser(userId: String): AppResult<List<Event>> = appCall { remote.createdBy(userId).map { it.toEvent() } }

    /** Primero se obtienen los ids de mis inscripciones (RLS: solo las mías) y luego esos eventos. */
    suspend fun getEventsRegisteredByUser(userId: String): AppResult<List<Event>> = appCall {
        remote.byIds(registrations.eventIdsFor(userId)).map { it.toEvent() }
    }

    suspend fun getCategories(): AppResult<List<String>> = appCall { remote.categories() }

    suspend fun createEvent(title: String, description: String, date: Instant, location: String, category: String, capacity: Int): AppResult<Unit> = appCall {
        remote.create(EventWriteDto(title.trim(), description.trim(), date.toString(), location.trim(), category.trim(), capacity))
    }

    /** Solo el creador puede editar (RLS) y no se permite bajar la capacidad por debajo de los inscritos. */
    suspend fun updateEvent(eventId: Long, title: String, description: String, date: Instant, location: String, category: String, capacity: Int): AppResult<Unit> = appCall {
        val current = remote.byId(eventId) ?: throw AppException(AppError.EVENT_NOT_FOUND)
        if (capacity < current.registeredCount) throw AppException(AppError.CAPACITY_BELOW_REGISTERED)
        val updated = remote.update(eventId, EventWriteDto(title.trim(), description.trim(), date.toString(), location.trim(), category.trim(), capacity))
        if (updated == 0) throw AppException(AppError.FORBIDDEN)
    }
}

private fun EventDto.toEvent(): Event {
    return Event(id, creatorId, title, description.orEmpty(), OffsetDateTime.parse(eventDate).toInstant(), location, category, capacity, registeredCount, attendedCount)
}
