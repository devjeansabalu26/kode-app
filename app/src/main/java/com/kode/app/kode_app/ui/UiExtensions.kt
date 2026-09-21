package com.kode.app.kode_app.ui

import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.core.AppError

/** Mensaje flotante breve. No hace nada si el fragment ya no está adjunto (p. ej. la respuesta llegó tarde). */
fun Fragment.showMessage(message: String) {
    context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
}

fun Fragment.showError(error: AppError) = showMessage(error.userMessage)

/**
 * Cambia la pantalla dentro de `mainContainer`.
 * Usa commitAllowingStateLoss porque muchas navegaciones ocurren cuando termina una llamada de red
 * y la app podría estar en segundo plano; así no se cae con IllegalStateException.
 */
fun Fragment.navigateTo(fragment: Fragment, addToBackStack: Boolean = false) {
    if (!isAdded) return
    parentFragmentManager.beginTransaction().replace(R.id.mainContainer, fragment).apply { if (addToBackStack) addToBackStack(null) }.commitAllowingStateLoss()
}
