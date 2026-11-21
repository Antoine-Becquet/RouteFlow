package com.example.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.R
import com.example.data.AppDatabase
import com.example.data.OptimizationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarToolsScreen(carContext: CarContext) : Screen(carContext) {
    private val database = AppDatabase.getDatabase(carContext)
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var isOptimizing = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_tools_title))
            .setHeaderAction(Action.BACK)

        if (isOptimizing) {
            return builder.setLoading(true).build()
        }

        val itemList = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_tool_map_title))
                    .addText(carContext.getString(R.string.car_tool_map_subtitle))
                    .setImage(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_map))
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(TourMapScreen(carContext))
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_tool_optimize_title))
                    .addText(carContext.getString(R.string.car_tool_optimize_subtitle))
                    .setImage(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_optimize))
                    .setOnClickListener {
                        reoptimizeTour()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_tool_favorites_title))
                    .addText(carContext.getString(R.string.car_tool_favorites_subtitle))
                    .setImage(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_favorites))
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(FavoritesCarScreen(carContext))
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_tool_search_title))
                    .addText(carContext.getString(R.string.car_tool_search_subtitle))
                    .setImage(CarNavigationHelper.createCarIcon(carContext, R.drawable.ic_car_search))
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(SearchStopCarScreen(carContext))
                    }
                    .build()
            )
            .build()

        return builder.setSingleList(itemList).build()
    }

    private fun reoptimizeTour() {
        if (isOptimizing) return
        isOptimizing = true
        invalidate()

        screenScope.launch {
            try {
                val allStops = withContext(Dispatchers.IO) { database.stopDao().getAllStops() }
                val pending = allStops.filter { !it.isCompleted }
                val completed = allStops.filter { it.isCompleted }

                if (pending.isEmpty()) {
                    CarToast.makeText(
                        carContext,
                        carContext.getString(R.string.car_toast_optimize_empty),
                        CarToast.LENGTH_SHORT
                    ).show()
                    isOptimizing = false
                    invalidate()
                    return@launch
                }

                val (hubLat, hubLng) = CarNavigationHelper.getHubCoordinates(carContext)
                val result = OptimizationService.optimizeTour(
                    stops = pending,
                    hubX = hubLat,
                    hubY = hubLng,
                    startHour = "08:00",
                    trafficFactor = 1.0f
                )

                val merged = (result.optimizedStops + completed).mapIndexed { index, stop ->
                    stop.copy(orderIndex = index + 1)
                }

                withContext(Dispatchers.IO) {
                    database.stopDao().replaceStops(merged)
                }

                CarToast.makeText(
                    carContext,
                    carContext.getString(R.string.car_toast_optimized),
                    CarToast.LENGTH_SHORT
                ).show()
                isOptimizing = false
                screenManager.popToRoot()
            } catch (e: Exception) {
                Log.w("CarToolsScreen", "Car re-optimization failed: ${e.message}")
                isOptimizing = false
                invalidate()
            }
        }
    }
}
