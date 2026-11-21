package com.example.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
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

class TourListScreen(carContext: CarContext) : Screen(carContext) {
    private val database = AppDatabase.getDatabase(carContext)
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var stops: List<StopEntity> = emptyList()
    private var isLoaded: Boolean = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })

        screenScope.launch {
            try {
                database.stopDao().getAllStopsFlow().collect { loadedStops ->
                    stops = loadedStops
                    isLoaded = true
                    invalidate()
                }
            } catch (e: Exception) {
                Log.w("TourListScreen", "Error observing stops: ${e.message}")
                isLoaded = true
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val pendingStops = stops.filter { !it.isCompleted }
        val completedStops = stops.filter { it.isCompleted }
        val nextStop = pendingStops.firstOrNull()

        val actionStripBuilder = ActionStrip.Builder()
        if (nextStop != null) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_action_next))
                    .setIcon(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_navigation))
                    .setOnClickListener {
                        CarNavigationHelper.launchNavigation(carContext, nextStop)
                    }
                    .build()
            )
        }
        actionStripBuilder.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_action_tools))
                .setIcon(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_tools))
                .setOnClickListener {
                    screenManager.push(CarToolsScreen(carContext))
                }
                .build()
        )

        val title = if (stops.isEmpty()) {
            carContext.getString(R.string.car_tour_title)
        } else {
            "${carContext.getString(R.string.car_tour_title)} (${completedStops.size}/${stops.size})"
        }

        val templateBuilder = ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStripBuilder.build())

        if (!isLoaded) {
            return templateBuilder.setLoading(true).build()
        }

        if (stops.isEmpty()) {
            val emptyList = ItemList.Builder()
                .setNoItemsMessage(carContext.getString(R.string.car_empty_tour))
                .build()
            return templateBuilder.setSingleList(emptyList).build()
        }

        val maxItems = CarNavigationHelper.getListLimit(carContext)
        var remainingBudget = maxItems

        if (pendingStops.isNotEmpty() && remainingBudget > 0) {
            val pendingListBuilder = ItemList.Builder()
            pendingStops.take(remainingBudget).forEach { stop ->
                pendingListBuilder.addItem(buildStopRow(stop, isCompleted = false))
                remainingBudget--
            }
            templateBuilder.addSectionedList(
                SectionedItemList.create(
                    pendingListBuilder.build(),
                    carContext.getString(R.string.car_section_pending, pendingStops.size)
                )
            )
        }

        if (completedStops.isNotEmpty() && remainingBudget > 0) {
            val completedListBuilder = ItemList.Builder()
            completedStops.take(remainingBudget).forEach { stop ->
                completedListBuilder.addItem(buildStopRow(stop, isCompleted = true))
                remainingBudget--
            }
            templateBuilder.addSectionedList(
                SectionedItemList.create(
                    completedListBuilder.build(),
                    carContext.getString(R.string.car_section_completed, completedStops.size)
                )
            )
        }

        return templateBuilder.build()
    }

    private fun buildStopRow(stop: StopEntity, isCompleted: Boolean): Row {
        val label = stop.contactName.ifBlank { stop.address }
        val titleText = "#${stop.orderIndex} · $label"

        val rowBuilder = Row.Builder()
            .setTitle(titleText)
            .setBrowsable(true)
            .setOnClickListener {
                screenManager.push(StopDetailScreen(carContext, stop.id))
            }

        if (isCompleted) {
            rowBuilder.addText(
                "${carContext.getString(R.string.car_status_completed)} · ${stop.address}"
            )
        } else {
            rowBuilder.addText(stop.address)
            val meta = CarNavigationHelper.formatStopSubtitle(carContext, stop)
            if (meta.isNotBlank()) {
                rowBuilder.addText(meta)
            }
        }

        return rowBuilder.build()
    }
}
