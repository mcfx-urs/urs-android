package ch.mcfx.urs.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

// minSdk is already 34, well above the API 29 this permission was
// introduced at, so no version gate is needed here unlike the
// foreground/background location checks in LocationHistorySettingsScreen.
fun hasActivityRecognitionPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
