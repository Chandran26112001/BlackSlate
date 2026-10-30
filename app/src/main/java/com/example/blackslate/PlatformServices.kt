package com.example.blackslate

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Geocoder
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import androidx.annotation.RequiresApi
import java.util.Locale

class TouchFeedback(context: Context, private val enabled: () -> Boolean) {
    private val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator else context.getSystemService(Vibrator::class.java)
    private var last = 0L
    fun pulse(strong: Boolean = false) {
        if (!enabled() || vibrator?.hasVibrator() != true) return
        val now = SystemClock.elapsedRealtime()
        if (!strong && now - last < 40) return
        last = now
        if (Build.VERSION.SDK_INT >= 26) {
            val effect = (if (Build.VERSION.SDK_INT >= 30) supportedEffect(strong) else null)
                ?: VibrationEffect.createOneShot(if (strong) 28 else 16, if (strong) 180 else 110)
            if (Build.VERSION.SDK_INT >= 33) vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            else vibrator.vibrate(effect, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        } else @Suppress("DEPRECATION") vibrator.vibrate(if (strong) 28 else 16)
    }
    @RequiresApi(30)
    private fun supportedEffect(strong: Boolean): VibrationEffect? {
        val id = if (strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
        return if (vibrator?.areEffectsSupported(id)?.firstOrNull() == Vibrator.VIBRATION_EFFECT_SUPPORT_YES)
            VibrationEffect.createPredefined(id) else null
    }
}
/** Picks a phone entry, not a whole contact, so the returned URI exposes name and number without READ_CONTACTS. */
class PickContactPhone : ActivityResultContract<Unit?, Uri?>() {
    override fun createIntent(context: Context, input: Unit?) =
        Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
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

/** Uses the device geocoder only after the owner enables saving transaction locations. */
@Suppress("DEPRECATION")
fun areaNameForLocation(context: Context, location: String?): String? {
    val point = locationCoordinates(location) ?: return null
    if (!Geocoder.isPresent()) return null
    return runCatching {
        val address = Geocoder(context, Locale.getDefault()).getFromLocation(point.first, point.second, 1)?.firstOrNull()
        listOfNotNull(address?.subLocality, address?.locality, address?.subAdminArea, address?.adminArea)
            .firstOrNull { it.isNotBlank() }
    }.getOrNull()
}

fun shareSettlementCard(context: Context, person: Person, balances: Map<Currency, Long>, transactions: List<Transaction>, appearance: Appearance): Boolean {
    val due = balances.filterValues { it > 0L }
    if (due.isEmpty()) return false
    val amount = due.entries.joinToString(" · ") { (currency, cents) -> money(cents, currency) }
    val text = "Hi ${person.name}, $amount is pending. Please settle when convenient."
    val attachment = SettlementCardRenderer.render(context, person, due, transactions, appearance)?.let { file ->
        runCatching { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file) }.getOrNull()
    }
    val whatsapp = Intent(Intent.ACTION_SEND).apply {
        type = if (attachment == null) "text/plain" else "image/png"
        putExtra(Intent.EXTRA_TEXT, text)
        attachment?.let { uri ->
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Black Slate settlement", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setPackage("com.whatsapp")
    }
    return runCatching { context.startActivity(whatsapp); true }.getOrDefault(false)
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

