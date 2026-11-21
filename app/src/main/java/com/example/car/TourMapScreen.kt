package com.example.car

import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
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

class TourMapScreen(carContext: CarContext) : Screen(carContext) {
    private val database = AppDatabase.getDatabase(carContext)
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var stops: List<StopEntity> = emptyList()
    private var isLoaded = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })

        screenScope.launch {
            try {
                database.stopDao().getAllStopsFlow().collect { loaded ->
                    stops = loaded
                    isLoaded = true
                    invalidate()
                }
            } catch (e: Exception) {
                Log.w("TourMapScreen", "Error observing stops for map: ${e.message}")
                isLoaded = true
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val pendingStops = stops.filter { !it.isCompleted }
        val (hubLat, hubLng) = CarNavigationHelper.getHubCoordinates(carContext)

        val builder = PlaceListMapTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_map_title))
            .setHeaderAction(Action.BACK)

        if (carContext.carAppApiLevel >= 5) {
            builder.setOnContentRefreshListener {
                invalidate()
            }
        }

        val firstStop = pendingStops.firstOrNull()
        if (firstStop != null) {
            builder.setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.car_action_next))
                            .setIcon(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_navigation))
                            .setOnClickListener {
                                CarNavigationHelper.launchNavigation(carContext, firstStop)
                            }
                            .build()
                    )
                    .build()
            )
        }

        if (!isLoaded) {
            return builder.setLoading(true).build()
        }

        val maxItems = CarNavigationHelper.getListLimit(
            carContext,
            ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST
        )

        val listBuilder = ItemList.Builder()
            .setNoItemsMessage(carContext.getString(R.string.car_map_empty))

        var refLat = hubLat.toDouble()
        var refLng = hubLng.toDouble()

        pendingStops.take(maxItems).forEach { stop ->
            val distanceKm = CarNavigationHelper.calculateDistanceKm(
                refLat,
                refLng,
                stop.latitude.toDouble(),
                stop.longitude.toDouble()
            )
            refLat = stop.latitude.toDouble()
            refLng = stop.longitude.toDouble()

            val carDistance = if (distanceKm < 1.0) {
                Distance.create((distanceKm * 1000.0).coerceAtLeast(10.0), Distance.UNIT_METERS)
            } else {
                Distance.create(distanceKm, Distance.UNIT_KILOMETERS_P1)
            }

            val distanceLine = SpannableString("  · ${stop.address}")
            distanceLine.setSpan(
                DistanceSpan.create(carDistance),
                0,
                1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            val marker = PlaceMarker.Builder()
                .setLabel(stop.orderIndex.toString().take(3))
                .setColor(CarNavigationHelper.priorityMarkerColor(stop.priority))
                .build()

            val place = Place.Builder(
                CarLocation.create(stop.latitude.toDouble(), stop.longitude.toDouble())
            )
                .setMarker(marker)
                .build()

            val rowBuilder = Row.Builder()
                .setTitle("#${stop.orderIndex} · ${stop.contactName.ifBlank { stop.address }}")
                .addText(distanceLine)
                .setMetadata(Metadata.Builder().setPlace(place).build())
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(StopDetailScreen(carContext, stop.id))
                }

            val meta = CarNavigationHelper.formatStopSubtitle(carContext, stop)
            if (meta.isNotBlank()) {
                rowBuilder.addText(meta)
            }

            listBuilder.addItem(rowBuilder.build())
        }

        builder.setAnchor(
            Place.Builder(CarLocation.create(hubLat.toDouble(), hubLng.toDouble())).build()
        )
        builder.setItemList(listBuilder.build())

        return builder.build()
    }
}
