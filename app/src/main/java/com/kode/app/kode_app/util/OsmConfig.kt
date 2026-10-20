package com.kode.app.kode_app.util

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

fun Context.configurarOsm() {
    val config = Configuration.getInstance()
    config.userAgentValue = packageName
    val base = File(cacheDir, "osmdroid")
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles")
}
