package com.example.blackslate

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

class TouchFeedback(context: Context, private val enabled: () -> Boolean) {
    private val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator else context.getSystemService(Vibrator::class.java)
    private var last = 0L
    fun pulse(strong: Boolean = false) {
        if (!enabled() || vibrator?.hasVibrator() != true) return
        val now = SystemClock.elapsedRealtime()
        if (!strong && now - last < 40) return
        last = now
        if (Build.VERSION.SDK_INT >= 26) {
            val effectId = if (strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
            val supported = Build.VERSION.SDK_INT >= 30 && vibrator.areEffectsSupported(effectId).firstOrNull() == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
            val effect = if (supported) VibrationEffect.createPredefined(effectId)
                else VibrationEffect.createOneShot(if (strong) 28 else 16, if (strong) 180 else 110)
            if (Build.VERSION.SDK_INT >= 33) vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            else vibrator.vibrate(effect, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        } else @Suppress("DEPRECATION") vibrator.vibrate(if (strong) 28 else 16)
    }
}
fun readContact(context: Context, uri: Uri): Person? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
        if (it.moveToFirst()) newPerson(it.getString(0).orEmpty(), it.getString(1).orEmpty()) else null
    }
}.getOrNull()

fun openMap(context: Context, location: String): Boolean {
    val coordinates = locationCoordinates(location) ?: return false
    val point = "${coordinates.first},${coordinates.second}"
    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point(Transaction)")).setPackage("com.google.android.apps.maps")
    return runCatching { context.startActivity(mapIntent); true }.getOrElse {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$point"))); true
        }.getOrDefault(false)
    }
}
@SuppressLint("MissingPermission")
fun captureLocation(context: Context, callback: (String?) -> Unit): () -> Unit {
    val handler = Handler(Looper.getMainLooper())
    var finished = false
    val cancellation = CancellationSignal()
    fun finish(value: String?) {
        if (finished) return
        finished = true
        cancellation.cancel()
        callback(value)
    }
    val timeout = Runnable { finish(null) }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!granted) { finish(null); return {} }
    val manager = context.getSystemService(LocationManager::class.java)
    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull {
        runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
    }
    if (provider == null) { finish(null); return {} }
    handler.postDelayed(timeout, 6000)
    runCatching {
        LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(context)) { location ->
            handler.removeCallbacks(timeout)
            val recent = location?.takeIf { System.currentTimeMillis() - it.time < 120_000 }
            finish(recent?.let { "${it.latitude},${it.longitude}" })
        }
    }.onFailure { handler.removeCallbacks(timeout); finish(null) }
    return { finished = true; handler.removeCallbacks(timeout); cancellation.cancel() }
}

