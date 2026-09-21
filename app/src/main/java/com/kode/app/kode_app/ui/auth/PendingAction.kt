package com.kode.app.kode_app.ui.auth

import androidx.fragment.app.Fragment
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.data.repository.RegistrationRepository
import com.kode.app.kode_app.ui.events.EventFormFragment
import com.kode.app.kode_app.ui.events.MyEventsFragment
import com.kode.app.kode_app.ui.events.TicketFragment
import com.kode.app.kode_app.ui.home.HomeFragment
import com.kode.app.kode_app.ui.navigateTo
import com.kode.app.kode_app.ui.profile.ProfileFragment
import com.kode.app.kode_app.ui.showError

/** Argumentos con los que se abre Login/Register para saber a dónde ir cuando el usuario ya tenga sesión. */
const val ARG_DESTINATION = "destination"
const val ARG_EVENT_ID = "event_id"
const val ARG_EVENT_TITLE = "event_title"

/** Lleva al usuario a lo que quería hacer antes de que le pidieran iniciar sesión. Se usa desde Login y Register. */
suspend fun Fragment.continuePendingAction(registrations: RegistrationRepository = RegistrationRepository()) {
    val destination = arguments?.getString(ARG_DESTINATION)
    when (destination) {
        "REGISTER_EVENT" -> {
            val eventId = arguments?.getLong(ARG_EVENT_ID, -1L) ?: -1L
            val eventTitle = arguments?.getString(ARG_EVENT_TITLE) ?: ""
            when (val result = registrations.registerUserInEvent(eventId)) {
                is AppResult.Success -> navigateTo(TicketFragment.newInstance(eventId, eventTitle, result.value))
                is AppResult.Failure -> {
                    showError(result.error)
                    navigateTo(HomeFragment())
                }
            }
        }
        "CREATE_EVENT" -> navigateTo(EventFormFragment())
        "MY_EVENTS" -> navigateTo(MyEventsFragment())
        "PROFILE" -> navigateTo(ProfileFragment())
        else -> navigateTo(HomeFragment())
    }
}
