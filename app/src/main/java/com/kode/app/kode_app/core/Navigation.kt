package com.kode.app.kode_app.core

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.kode.app.kode_app.R

fun Fragment.navigateTo(
    destinationId: Int,
    args: Bundle? = null,
    replaceCurrent: Boolean = false,
    popUpToId: Int? = null
) {
    val navController = findNavController()
    val options = when {
        popUpToId != null -> NavOptions.Builder().setPopUpTo(popUpToId, false).build()
        replaceCurrent -> navController.currentDestination?.let {
            NavOptions.Builder().setPopUpTo(it.id, true).build()
        }
        else -> null
    }
    navController.navigate(destinationId, args, options)
}

fun Fragment.goToTab(tabId: Int) {
    val navController = findNavController()
    val options = NavOptions.Builder()
        .setPopUpTo(navController.graph.startDestinationId, false)
        .setLaunchSingleTop(true)
        .build()
    navController.navigate(tabId, null, options)
}

fun Fragment.goBack() {
    if (!findNavController().navigateUp()) goToTab(R.id.nav_home)
}
