package com.example.car

import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.R
import com.example.data.AppDatabase
import com.example.data.SavedAddressEntity
import com.example.data.StopEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class FavoritesCarScreen(carContext: CarContext) : Screen(carContext) {
    private val database = AppDatabase.getDatabase(carContext)
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var favorites: List<SavedAddressEntity> = emptyList()
    private var isLoaded = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })

        screenScope.launch {
            try {
                database.savedAddressDao().getAllSavedAddressesFlow().collect { items ->
                    favorites = items
                    isLoaded = true
                    invalidate()
                }
            } catch (e: Exception) {
                Log.w("FavoritesCarScreen", "Error observing favorites: ${e.message}")
                isLoaded = true
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_favorites_title))
            .setHeaderAction(Action.BACK)

        if (!isLoaded) {
            return builder.setLoading(true).build()
        }

        val maxItems = CarNavigationHelper.getListLimit(carContext)
        val listBuilder = ItemList.Builder()
            .setNoItemsMessage(carContext.getString(R.string.car_favorites_empty))

        favorites.take(maxItems).forEach { fav ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(fav.label.ifBlank { fav.address })
                    .addText(fav.address)
                    .setOnClickListener {
                        addFavoriteToTour(fav)
                    }
                    .build()
            )
        }

        return builder.setSingleList(listBuilder.build()).build()
    }

    private fun addFavoriteToTour(fav: SavedAddressEntity) {
        screenScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val existing = database.stopDao().getAllStops()
                    val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: 0) + 1
                    database.stopDao().insertStop(
                        StopEntity(
                            address = fav.address,
                            contactName = fav.label,
                            hasTimeSlot = false,
                            timeSlotStart = "08:00",
                            timeSlotEnd = "18:00",
                            latitude = fav.latitude,
                            longitude = fav.longitude,
                            orderIndex = nextOrder,
                            priority = "Normal"
                        )
                    )
                }
                CarToast.makeText(
                    carContext,
                    carContext.getString(R.string.car_toast_added_to_tour),
                    CarToast.LENGTH_SHORT
                ).show()
                screenManager.popToRoot()
            } catch (e: Exception) {
                Log.w("FavoritesCarScreen", "Error adding favorite to tour: ${e.message}")
            }
        }
    }
}

private data class CarAddressSuggestion(
    val displayName: String,
    val shortTitle: String,
    val lat: Float,
    val lng: Float
)

class SearchStopCarScreen(carContext: CarContext) : Screen(carContext) {
    private val database = AppDatabase.getDatabase(carContext)
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var searchJob: Job? = null
    private var currentQuery: String = ""
    private var isSearching: Boolean = false
    private var results: List<CarAddressSuggestion> = emptyList()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                screenScope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val searchCallback = object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                scheduleSearch(searchText, immediate = false)
            }

            override fun onSearchSubmitted(searchText: String) {
                scheduleSearch(searchText, immediate = true)
            }
        }

        val builder = SearchTemplate.Builder(searchCallback)
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.car_search_hint))
            .setShowKeyboardByDefault(false)

        if (isSearching) {
            return builder.setLoading(true).build()
        }

        val maxItems = CarNavigationHelper.getListLimit(carContext)
        val emptyMsg = if (currentQuery.trim().length < 3) {
            carContext.getString(R.string.car_search_prompt)
        } else {
            carContext.getString(R.string.car_search_no_results)
        }

        val listBuilder = ItemList.Builder()
            .setNoItemsMessage(emptyMsg)

        results.take(maxItems).forEach { suggestion ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(suggestion.shortTitle)
                    .addText(suggestion.displayName)
                    .setOnClickListener {
                        addSuggestionToTour(suggestion)
                    }
                    .build()
            )
        }

        return builder.setItemList(listBuilder.build()).build()
    }

    private fun scheduleSearch(query: String, immediate: Boolean) {
        currentQuery = query
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.length < 3) {
            isSearching = false
            results = emptyList()
            invalidate()
            return
        }

        searchJob = screenScope.launch {
            if (!immediate) {
                delay(400)
            }
            isSearching = true
            invalidate()

            val fetched = withContext(Dispatchers.IO) {
                fetchAddressSuggestions(trimmed)
            }
            results = fetched
            isSearching = false
            invalidate()
        }
    }

    private fun addSuggestionToTour(suggestion: CarAddressSuggestion) {
        screenScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val existing = database.stopDao().getAllStops()
                    val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: 0) + 1
                    database.stopDao().insertStop(
                        StopEntity(
                            address = suggestion.displayName,
                            contactName = suggestion.shortTitle,
                            hasTimeSlot = false,
                            timeSlotStart = "08:00",
                            timeSlotEnd = "18:00",
                            latitude = suggestion.lat,
                            longitude = suggestion.lng,
                            orderIndex = nextOrder,
                            priority = "Normal"
                        )
                    )
                }
                CarToast.makeText(
                    carContext,
                    carContext.getString(R.string.car_toast_added_to_tour),
                    CarToast.LENGTH_SHORT
                ).show()
                screenManager.popToRoot()
            } catch (e: Exception) {
                Log.w("SearchStopCarScreen", "Error adding searched stop: ${e.message}")
            }
        }
    }

    companion object {
        private val searchClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        private fun fetchAddressSuggestions(query: String): List<CarAddressSuggestion> {
            return try {
                val encoded = Uri.encode(query)
                val url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&addressdetails=1&q=$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RouteFlowProAndroidAuto/1.0")
                    .build()

                searchClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return emptyList()
                    val body = response.body?.string() ?: return emptyList()
                    val jsonArray = JSONArray(body)
                    val list = mutableListOf<CarAddressSuggestion>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val displayName = obj.optString("display_name", "")
                        val lat = obj.optString("lat", "").toFloatOrNull() ?: continue
                        val lng = obj.optString("lon", "").toFloatOrNull() ?: continue
                        if (displayName.isBlank()) continue
                        val shortTitle = displayName.split(",").take(2).joinToString(",").trim()
                        list.add(
                            CarAddressSuggestion(
                                displayName = displayName,
                                shortTitle = shortTitle.ifBlank { displayName },
                                lat = lat,
                                lng = lng
                            )
                        )
                    }
                    list
                }
            } catch (e: Exception) {
                Log.w("SearchStopCarScreen", "Address search failed: ${e.message}")
                emptyList()
            }
        }
    }
}
