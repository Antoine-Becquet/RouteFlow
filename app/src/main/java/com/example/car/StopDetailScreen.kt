package com.example.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.R
import com.example.data.AppDatabase
import com.example.data.StopEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StopDetailScreen(
    carContext: CarContext,
    private var currentStopId: Int
) : Screen(carContext) {

    private val database = AppDatabase.getDatabase(carContext)
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val voiceReader = CarVoiceReader(carContext)

    private var allStops: List<StopEntity> = emptyList()
    private var isLoaded: Boolean = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                voiceReader.release()
                screenScope.cancel()
            }
        })

        screenScope.launch {
            try {
                database.stopDao().getAllStopsFlow().collect { loadedStops ->
                    allStops = loadedStops
                    isLoaded = true
                    invalidate()
                }
            } catch (e: Exception) {
                Log.w("StopDetailScreen", "Error observing stop details: ${e.message}")
                isLoaded = true
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        if (!isLoaded) {
            return PaneTemplate.Builder(Pane.Builder().setLoading(true).build())
                .setHeaderAction(Action.BACK)
                .setTitle(carContext.getString(R.string.car_tour_title))
                .build()
        }

        val stop = allStops.firstOrNull { it.id == currentStopId }
        if (stop == null) {
            val emptyPane = Pane.Builder()
                .addRow(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_stop_not_found))
                        .build()
                )
                .build()
            return PaneTemplate.Builder(emptyPane)
                .setHeaderAction(Action.BACK)
                .setTitle(carContext.getString(R.string.car_tour_title))
                .build()
        }

        val nextPending = allStops.firstOrNull { !it.isCompleted && it.id != stop.id }

        val paneBuilder = Pane.Builder()
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_detail_address))
                .addText(stop.address)
                .build()
        )

        val scheduleParts = mutableListOf<String>()
        if (!stop.estimatedArrivalTime.isNullOrBlank()) {
            scheduleParts.add("Arrivée estimée : ${stop.estimatedArrivalTime}")
        }
        if (stop.hasTimeSlot) {
            scheduleParts.add("Créneau : ${stop.timeSlotStart} - ${stop.timeSlotEnd}")
        }
        scheduleParts.add("Durée : ${stop.durationMinutes} min")
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_detail_schedule))
                .addText(scheduleParts.joinToString(" · "))
                .build()
        )

        val statusLabel = if (stop.isCompleted) {
            carContext.getString(R.string.car_status_completed)
        } else {
            carContext.getString(R.string.car_status_pending)
        }
        val priorityLabel = CarNavigationHelper.formatPriority(carContext, stop.priority)
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_detail_status))
                .addText("$priorityLabel · $statusLabel")
                .build()
        )

        if (!stop.notes.isNullOrBlank()) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_detail_notes))
                    .addText(stop.notes)
                    .build()
            )
        }

        // Primary action: start turn-by-turn navigation on Android Auto
        val navActionBuilder = Action.Builder()
            .setTitle(carContext.getString(R.string.car_action_navigate))
            .setIcon(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_navigation))
            .setBackgroundColor(CarColor.PRIMARY)
            .setOnClickListener {
                CarNavigationHelper.launchNavigation(carContext, stop)
            }
        if (carContext.carAppApiLevel >= 2) {
            navActionBuilder.setFlags(Action.FLAG_PRIMARY)
        }
        paneBuilder.addAction(navActionBuilder.build())

        // Secondary action: mark completed or reopen
        val toggleTitle = if (stop.isCompleted) {
            carContext.getString(R.string.car_action_reopen)
        } else {
            carContext.getString(R.string.car_action_complete)
        }
        paneBuilder.addAction(
            Action.Builder()
                .setTitle(toggleTitle)
                .setIcon(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_check))
                .setOnClickListener {
                    toggleStopCompletion(stop)
                }
                .build()
        )

        // Header ActionStrip: Read aloud + jump to Next pending stop
        val stripBuilder = ActionStrip.Builder()
        stripBuilder.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_action_read_aloud))
                .setIcon(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_speaker))
                .setOnClickListener {
                    voiceReader.speak(buildSpokenSummary(stop))
                }
                .build()
        )
        if (nextPending != null) {
            stripBuilder.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_action_next_stop))
                    .setOnClickListener {
                        currentStopId = nextPending.id
                        invalidate()
                    }
                    .build()
            )
        }

        val headerTitle = "#${stop.orderIndex} · ${stop.contactName.ifBlank { stop.address }}"
        return PaneTemplate.Builder(paneBuilder.build())
            .setHeaderAction(Action.BACK)
            .setTitle(headerTitle)
            .setActionStrip(stripBuilder.build())
            .build()
    }

    private fun toggleStopCompletion(stop: StopEntity) {
        val newCompleted = !stop.isCompleted
        screenScope.launch {
            try {
                database.stopDao().updateStop(stop.copy(isCompleted = newCompleted))
                val msgRes = if (newCompleted) {
                    R.string.car_toast_stop_completed
                } else {
                    R.string.car_toast_stop_reopened
                }
                CarToast.makeText(carContext, carContext.getString(msgRes), CarToast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w("StopDetailScreen", "Failed updating stop status: ${e.message}")
            }
        }
    }

    private fun buildSpokenSummary(stop: StopEntity): String {
        val parts = mutableListOf<String>()
        val name = stop.contactName.ifBlank { "Arrêt numéro ${stop.orderIndex}" }
        parts.add("$name. Adresse : ${stop.address}.")
        if (stop.hasTimeSlot) {
            parts.add("Créneau horaire de ${stop.timeSlotStart} à ${stop.timeSlotEnd}.")
        }
        if (!stop.notes.isNullOrBlank()) {
            parts.add("Consignes : ${stop.notes}.")
        }
        return parts.joinToString(" ")
    }
}
