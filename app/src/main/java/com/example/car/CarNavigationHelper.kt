package com.example.car

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.example.R
import com.example.data.StopEntity
import java.util.Locale
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object CarNavigationHelper {
    private const val TAG = "CarNavigationHelper"
    private const val PREFS_NAME = "routeflow_prefs"

    fun launchNavigation(carContext: CarContext, stop: StopEntity) {
        launchNavigation(carContext, stop.latitude, stop.longitude, stop.address)
    }

    fun launchNavigation(carContext: CarContext, lat: Float, lng: Float, address: String) {
        try {
            val encodedQuery = Uri.encode(address.ifBlank { "$lat,$lng" })
            val uri = Uri.parse("geo:$lat,$lng?q=$encodedQuery")
            val intent = Intent(CarContext.ACTION_NAVIGATE, uri)
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start car navigation: ${e.message}")
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.car_toast_nav_error),
                CarToast.LENGTH_SHORT
            ).show()
        }
    }

    fun getListLimit(carContext: CarContext, contentLimitType: Int = ConstraintManager.CONTENT_LIMIT_TYPE_LIST): Int {
        return try {
            if (carContext.carAppApiLevel >= 2) {
                carContext.getCarService(ConstraintManager::class.java)
                    .getContentLimit(contentLimitType)
                    .coerceAtLeast(1)
            } else {
                6
            }
        } catch (e: Exception) {
            6
        }
    }

    fun createCarIcon(carContext: CarContext, @DrawableRes resId: Int): CarIcon {
        return CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
    }

    fun getHubCoordinates(context: Context): Pair<Float, Float> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lat = prefs.getFloat("hub_lat", 48.8566f)
        val lng = prefs.getFloat("hub_lng", 2.3522f)
        return lat to lng
    }

    fun formatPriority(carContext: CarContext, priority: String): String {
        return when (priority) {
            "High" -> carContext.getString(R.string.car_priority_high)
            "Low" -> carContext.getString(R.string.car_priority_low)
            else -> carContext.getString(R.string.car_priority_normal)
        }
    }

    fun priorityMarkerColor(priority: String): CarColor {
        return when (priority) {
            "High" -> CarColor.RED
            "Low" -> CarColor.GREEN
            else -> CarColor.BLUE
        }
    }

    fun formatStopSubtitle(carContext: CarContext, stop: StopEntity): String {
        val parts = mutableListOf<String>()
        if (!stop.estimatedArrivalTime.isNullOrBlank()) {
            parts.add("ETA ${stop.estimatedArrivalTime}")
        }
        if (stop.hasTimeSlot) {
            parts.add("${stop.timeSlotStart}-${stop.timeSlotEnd}")
        }
        if (stop.priority == "High") {
            parts.add(carContext.getString(R.string.car_priority_high))
        }
        return parts.joinToString(" · ")
    }

    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c * 1.25
    }
}

/**
 * Speaks stop notes and details over the vehicle speakers using navigation audio focus
 * so car media ducks automatically during playback.
 */
class CarVoiceReader(private val carContext: CarContext) {
    private val audioManager = carContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { /* transient ducking */ }

    init {
        tts = TextToSpeech(carContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    engine.setAudioAttributes(audioAttributes)
                    val langResult = engine.setLanguage(Locale.FRENCH)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.getDefault())
                    }
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            abandonAudioFocus()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            abandonAudioFocus()
                        }
                    })
                    isReady = true
                    pendingText?.let { queued ->
                        pendingText = null
                        speakNow(queued)
                    }
                }
            } else {
                isReady = false
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (isReady) {
            speakNow(text)
        } else if (tts != null) {
            pendingText = text
        } else {
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.car_toast_tts_unavailable),
                CarToast.LENGTH_SHORT
            ).show()
        }
    }

    private fun speakNow(text: String) {
        requestAudioFocus()
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    fun release() {
        pendingText = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        abandonAudioFocus()
    }
}
