package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.OptimizationService
import com.example.data.StopEntity
import com.example.data.StopRepository
import com.example.data.SavedAddressDao
import com.example.data.SavedAddressEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.random.Random

data class SmartTourUiState(
    val isLoading: Boolean = false,
    val aiExplanation: String? = null,
    val trafficFactor: Float = 1.0f, // 1.0 = Fluid, 1.5 = Dense, 2.0 = Heavy
    val selectedStop: StopEntity? = null,
    val isAddingStop: Boolean = false,
    val alerts: List<String> = emptyList(),
    val isSimulating: Boolean = false,
    val vehicleStopIndex: Int = 0, // vehicle is at/moving towards this index
    val vehicleProgress: Float = 0.0f // 0.0 to 1.0 between stops
)

data class AddressSuggestion(
    val label: String,
    val latitude: Float,
    val longitude: Float
)

class SmartTourViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StopRepository
    val stopsFlow: StateFlow<List<StopEntity>>

    private val savedAddressDao: SavedAddressDao
    val savedAddressesFlow: StateFlow<List<SavedAddressEntity>>

    private val _themeMode = MutableStateFlow<Boolean?>(null) // null=system, true=dark, false=light
    val themeMode: StateFlow<Boolean?> = _themeMode.asStateFlow()

    private val _uiState = MutableStateFlow(SmartTourUiState())
    val uiState: StateFlow<SmartTourUiState> = _uiState.asStateFlow()

    private var simulationJob: Job? = null

    // Departure central depot coordinate (hub) - persistent via SharedPreferences
    private val prefs = application.getSharedPreferences("routeflow_prefs", Context.MODE_PRIVATE)

    var hubX by mutableStateOf(prefs.getFloat("hub_lat", 48.8566f))
        private set
    var hubY by mutableStateOf(prefs.getFloat("hub_lng", 2.3522f))
        private set
    var hubAddress by mutableStateOf(prefs.getString("hub_address", "Dépôt Central (Paris)") ?: "Dépôt Central (Paris)")
        private set

    // StateFlow containing the exact list of road coordinates for drawing a beautiful OsmDroid Polyline path
    private val _streetRoutePoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val streetRoutePoints: StateFlow<List<Pair<Double, Double>>> = _streetRoutePoints.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = StopRepository(database.stopDao())
        savedAddressDao = database.savedAddressDao()
        
        stopsFlow = repository.allStopsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        savedAddressesFlow = savedAddressDao.getAllSavedAddressesFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Core listener to automatically calculate the real OSRM street route path whenever stops update!
        viewModelScope.launch {
            repository.allStopsFlow.collect { stops ->
                calculateStreetRoute(stops)
            }
        }

        // Ensure initial route points are drawn on launch
        viewModelScope.launch {
            val stops = repository.getAllStops()
            if (stops.isNotEmpty()) {
                calculateStreetRoute(stops)
            }
        }
    }

    fun setThemeMode(dark: Boolean?) {
        _themeMode.value = dark
    }

    private suspend fun seedInitialSavedAddresses() {
        val defaultSaved = listOf(
            SavedAddressEntity(
                label = "Dépôt Principal Seine",
                address = "400 Rue de Rivoli, Paris",
                latitude = 48.8576f,
                longitude = 2.3482f
            ),
            SavedAddressEntity(
                label = "Bureau Technique Est",
                address = "102 Rue des Pyrénées, Paris",
                latitude = 48.8592f,
                longitude = 2.4042f
            ),
            SavedAddressEntity(
                label = "Cabinet République",
                address = "45 Avenue de la République, Paris",
                latitude = 48.8653f,
                longitude = 2.3789f
            ),
            SavedAddressEntity(
                label = "Centre Médical Rive Gauche",
                address = "8 Boulevard Saint-Germain, Paris",
                latitude = 48.8496f,
                longitude = 2.3529f
            )
        )
        defaultSaved.forEach {
            savedAddressDao.insertAddress(it)
        }
    }

    private suspend fun seedInitialData() {
        val defaultStops = listOf(
            StopEntity(
                address = "12 Rue de Rivoli, Paris",
                contactName = "Mme. Julie Martin",
                hasTimeSlot = true,
                timeSlotStart = "08:30",
                timeSlotEnd = "10:00",
                latitude = 48.8552f,
                longitude = 2.3611f,
                orderIndex = 1,
                notes = "Laisser le colis au gardien si absent.",
                durationMinutes = 15
            ),
            StopEntity(
                address = "45 Avenue de la République",
                contactName = "M. Thomas Dubois",
                hasTimeSlot = true,
                timeSlotStart = "10:00",
                timeSlotEnd = "11:30",
                latitude = 48.8653f,
                longitude = 2.3789f,
                orderIndex = 2,
                notes = "Code d'entrée: B2541. 3ème étage.",
                durationMinutes = 25
            ),
            StopEntity(
                address = "8 Boulevard Saint-Germain",
                contactName = "Clinique Saint-Roch",
                hasTimeSlot = false,
                timeSlotStart = "08:00",
                timeSlotEnd = "18:00",
                latitude = 48.8496f,
                longitude = 2.3529f,
                orderIndex = 3,
                notes = "Livraison matériel médical en réserve.",
                durationMinutes = 30
            ),
            StopEntity(
                address = "102 Rue des Pyrénées",
                contactName = "Sophie Durand (Optic)",
                hasTimeSlot = true,
                timeSlotStart = "14:00",
                timeSlotEnd = "15:30",
                latitude = 48.8592f,
                longitude = 2.4042f,
                orderIndex = 4,
                notes = "Heure de rendez-vous clé impérative.",
                durationMinutes = 20
            )
        )
        repository.insertStops(defaultStops)
        
        // Initial auto-optimization
        val dbStops = repository.getAllStops()
        val result = OptimizationService.optimizeLocally(dbStops, hubX, hubY, "08:00", 1.0f)
        repository.replaceStops(result.optimizedStops)
        _uiState.value = _uiState.value.copy(
            aiExplanation = "Tournée initiale optimisée pour limiter les kilomètres. Le départ à 08:00 assure la livraison de Mme. Martin en premier.",
            alerts = listOf("Météo favorable pour la matinée.", "Trafic de banlieue dense signalé.")
        )
    }

    fun optimizeRoute() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val currentStops = repository.getAllStops()
            val trafficFactor = _uiState.value.trafficFactor

            val result = OptimizationService.optimizeTour(
                stops = currentStops,
                hubX = hubX,
                hubY = hubY,
                startHour = "08:00",
                trafficFactor = trafficFactor
            )

            // Save in database
            repository.replaceStops(result.optimizedStops)

            // Scan constraints warnings to generate alerts in French
            val newAlerts = mutableListOf<String>()
            val lateStops = result.optimizedStops.filter { s ->
                if (s.hasTimeSlot && s.estimatedArrivalTime != null) {
                    val arr = OptimizationService.parseTimeToMinutes(s.estimatedArrivalTime)
                    val end = OptimizationService.parseTimeToMinutes(s.timeSlotEnd)
                    arr > end
                } else false
            }

            if (lateStops.isNotEmpty()) {
                lateStops.forEach { s ->
                    newAlerts.add("Retard prévu à ${s.address} (${s.estimatedArrivalTime} > limite ${s.timeSlotEnd})")
                }
            } else {
                newAlerts.add("Optimisation terminée. Toutes les contraintes horaires sont respectées.")
            }

            if (trafficFactor >= 1.5f) {
                newAlerts.add("Alerte Trafic: Circulation ralentie sur les axes majeurs.")
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                aiExplanation = result.explanation,
                alerts = newAlerts,
                vehicleStopIndex = 0,
                vehicleProgress = 0.0f
            )
        }
    }

    private val _addressSuggestions = MutableStateFlow<List<AddressSuggestion>>(emptyList())
    val addressSuggestions: StateFlow<List<AddressSuggestion>> = _addressSuggestions.asStateFlow()

    fun searchAddress(query: String) {
        viewModelScope.launch {
            if (query.trim().length < 3) {
                _addressSuggestions.value = emptyList()
                return@launch
            }
            try {
                val results = searchAddressSuggestions(query)
                _addressSuggestions.value = results
            } catch (e: Exception) {
                _addressSuggestions.value = emptyList()
            }
        }
    }

    fun clearSuggestions() {
        _addressSuggestions.value = emptyList()
    }

    private suspend fun searchAddressSuggestions(query: String): List<AddressSuggestion> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://api-adresse.data.gouv.fr/search/?q=$encodedQuery&limit=5"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RouteFlowPro/1.0 (Android; Contact: antoinebecquet93@gmail.com)")
                    .build()
                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val json = JSONObject(body)
                    val features = json.getJSONArray("features")
                    val results = mutableListOf<AddressSuggestion>()
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val properties = feature.getJSONObject("properties")
                        val label = properties.getString("label")
                        
                        val geometry = feature.getJSONObject("geometry")
                        val coordinates = geometry.getJSONArray("coordinates")
                        val lon = coordinates.getDouble(0).toFloat()
                        val lat = coordinates.getDouble(1).toFloat()
                        
                        results.add(AddressSuggestion(label = label, latitude = lat, longitude = lon))
                    }
                    results
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    fun updateHub(address: String, lat: Float, lng: Float) {
        hubX = lat
        hubY = lng
        hubAddress = address
        prefs.edit()
            .putFloat("hub_lat", lat)
            .putFloat("hub_lng", lng)
            .putString("hub_address", address)
            .apply()

        viewModelScope.launch {
            val currentStops = repository.getAllStops()
            calculateStreetRoute(currentStops)
            optimizeRoute()
        }
    }

    private fun calculateStreetRoute(stops: List<StopEntity>) {
        viewModelScope.launch {
            if (stops.isEmpty()) {
                _streetRoutePoints.value = emptyList()
                return@launch
            }
            val pts = withContext(Dispatchers.IO) {
                try {
                    val coordinatesBuilder = StringBuilder()
                    // Start at custom depot
                    coordinatesBuilder.append("${hubY},${hubX}")
                    
                    // Add all stops in sequence
                    stops.forEach { stop ->
                        coordinatesBuilder.append(";${stop.longitude},${stop.latitude}")
                    }
                    
                    // End at custom depot
                    coordinatesBuilder.append(";${hubY},${hubX}")
                    
                    val url = "https://router.project-osrm.org/route/v1/driving/$coordinatesBuilder?overview=full&geometries=geojson"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "RouteFlowPro/1.0 (Android; Contact: antoinebecquet93@gmail.com)")
                        .build()
                    val client = OkHttpClient()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext null
                        val body = response.body?.string() ?: return@withContext null
                        val json = JSONObject(body)
                        val routes = json.getJSONArray("routes")
                        if (routes.length() == 0) return@withContext null
                        
                        val route = routes.getJSONObject(0)
                        val geometry = route.getJSONObject("geometry")
                        val coords = geometry.getJSONArray("coordinates")
                        
                        val resultPoints = mutableListOf<Pair<Double, Double>>()
                        for (i in 0 until coords.length()) {
                            val coord = coords.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            resultPoints.add(Pair(lat, lon))
                        }
                        resultPoints
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            if (pts != null) {
                _streetRoutePoints.value = pts
            } else {
                // Fallback: straight lines connecting central hub -> stops -> central hub
                val fallback = mutableListOf<Pair<Double, Double>>()
                fallback.add(Pair(hubX.toDouble(), hubY.toDouble()))
                stops.forEach { s ->
                    fallback.add(Pair(s.latitude.toDouble(), s.longitude.toDouble()))
                }
                fallback.add(Pair(hubX.toDouble(), hubY.toDouble()))
                _streetRoutePoints.value = fallback
            }
        }
    }

    suspend fun getCoordinatesForAddress(address: String): Pair<Float, Float> {
        val addr = address.lowercase()
        when {
            addr.contains("rivoli") -> return Pair(48.8552f, 2.3611f)
            addr.contains("république") || addr.contains("republique") -> return Pair(48.8653f, 2.3789f)
            addr.contains("saint-germain") -> return Pair(48.8496f, 2.3529f)
            addr.contains("pyrénées") || addr.contains("pyrenees") -> return Pair(48.8592f, 2.4042f)
            addr.contains("lafayette") -> return Pair(48.8732f, 2.3361f)
            addr.contains("haussmann") -> return Pair(48.8741f, 2.3271f)
            addr.contains("bureau") || addr.contains("dépôt") || addr.contains("depot") -> return Pair(hubX, hubY)
        }

        // Attempt live geocoding over the network
        val liveCoords = withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(address, "UTF-8")
                val url = "https://api-adresse.data.gouv.fr/search/?q=$encodedQuery&limit=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RouteFlowPro/1.0 (Android; Contact: antoinebecquet93@gmail.com)")
                    .build()
                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val features = json.getJSONArray("features")
                        if (features.length() > 0) {
                            val feature = features.getJSONObject(0)
                            val geometry = feature.getJSONObject("geometry")
                            val coordinates = geometry.getJSONArray("coordinates")
                            val lon = coordinates.getDouble(0).toFloat()
                            val lat = coordinates.getDouble(1).toFloat()
                            Pair(lat, lon)
                        } else null
                    } else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (liveCoords != null) {
            return liveCoords
        }

        // Deterministic hash based coordinates
        val hash = address.hashCode()
        val r = java.util.Random(hash.toLong())
        // Generate within a small lat/lng offset (~10km around custom depot center)
        val latOffset = (r.nextFloat() - 0.5f) * 0.08f
        val lngOffset = (r.nextFloat() - 0.5f) * 0.12f
        return Pair(hubX + latOffset, hubY + lngOffset)
    }

    fun addSavedAddress(label: String, address: String, latitude: Float? = null, longitude: Float? = null) {
        viewModelScope.launch {
            val coords = if (latitude != null && longitude != null) {
                Pair(latitude, longitude)
            } else {
                getCoordinatesForAddress(address)
            }
            val lat = coords.first
            val lng = coords.second
            val newAddress = SavedAddressEntity(
                label = label,
                address = address,
                latitude = lat,
                longitude = lng
            )
            savedAddressDao.insertAddress(newAddress)
        }
    }

    fun deleteSavedAddress(address: SavedAddressEntity) {
        viewModelScope.launch {
            savedAddressDao.deleteAddress(address)
        }
    }

    fun addStop(
        address: String,
        contactName: String,
        hasTimeSlot: Boolean,
        timeSlotStart: String,
        timeSlotEnd: String,
        durationMinutes: Int,
        notes: String,
        latitude: Float? = null,
        longitude: Float? = null,
        priority: String = "Normal"
    ) {
        viewModelScope.launch {
            val coords = if (latitude != null && longitude != null) {
                Pair(latitude, longitude)
            } else {
                getCoordinatesForAddress(address)
            }
            val lat = coords.first
            val lng = coords.second

            val currentStops = repository.getAllStops()
            val nextIndex = currentStops.size + 1

            val newStop = StopEntity(
                address = address,
                contactName = contactName,
                hasTimeSlot = hasTimeSlot,
                timeSlotStart = if (hasTimeSlot) timeSlotStart else "08:00",
                timeSlotEnd = if (hasTimeSlot) timeSlotEnd else "18:00",
                latitude = lat,
                longitude = lng,
                orderIndex = nextIndex,
                notes = notes,
                durationMinutes = durationMinutes,
                priority = priority
            )

            repository.insertStop(newStop)
            _uiState.value = _uiState.value.copy(isAddingStop = false)
            
            // Auto optimize with new stop
            optimizeRoute()
        }
    }

    fun deleteStop(stop: StopEntity) {
        viewModelScope.launch {
            repository.deleteStop(stop)
            
            // Adjust indices and re-optimize
            val remaining = repository.getAllStops()
            val reordered = remaining.mapIndexed { idx, s ->
                s.copy(orderIndex = idx + 1)
            }
            repository.replaceStops(reordered)

            optimizeRoute()
        }
    }

    fun updateStop(stop: StopEntity) {
        viewModelScope.launch {
            repository.updateStop(stop)
            optimizeRoute()
        }
    }

    fun toggleCompleted(stop: StopEntity) {
        viewModelScope.launch {
            val updated = stop.copy(isCompleted = !stop.isCompleted)
            repository.updateStop(updated)
            
            // Add a cheerful log/alert
            if (updated.isCompleted) {
                val updatedAlerts = _uiState.value.alerts.toMutableList()
                updatedAlerts.add(0, "Visite terminée à ${stop.contactName} (${stop.address})")
                _uiState.value = _uiState.value.copy(alerts = updatedAlerts)
            }
        }
    }

    fun updateTrafficFactor(scale: Float) {
        _uiState.value = _uiState.value.copy(trafficFactor = scale)
        // Re-optimize route taking newly set traffic modifier into consideration
        optimizeRoute()
    }

    fun triggerTrafficIncident() {
        viewModelScope.launch {
            val alerts = _uiState.value.alerts.toMutableList()
            val incidents = listOf(
                "Accident sur la N104: +12m d'attente.",
                "Zone de travaux temporaires signalée sur l'axe République: trafic dense.",
                "Manifestation centre-ville: déviations actives sur le Boulevard.",
                "Bouchon inattendu détecté par la communauté à l'entrée Est."
            )
            val added = incidents.random()
            alerts.add(0, "Trafic Live: $added")

            // Push traffic modifier upwards to trigger recalculations
            _uiState.value = _uiState.value.copy(
                trafficFactor = 1.8f,
                alerts = alerts
            )
            optimizeRoute()
        }
    }

    fun startVehicleSimulation() {
        val currentStops = stopsFlow.value
        if (currentStops.isEmpty()) return

        // Toggle simulation state
        if (_uiState.value.isSimulating) {
            stopVehicleSimulation()
            return
        }

        _uiState.value = _uiState.value.copy(
            isSimulating = true,
            vehicleStopIndex = 0,
            vehicleProgress = 0.0f
        )

        simulationJob = viewModelScope.launch {
            while (_uiState.value.isSimulating) {
                val stops = stopsFlow.value
                if (stops.isEmpty()) {
                    stopVehicleSimulation()
                    break
                }

                val currentProgress = _uiState.value.vehicleProgress
                if (currentProgress < 1.0f) {
                    _uiState.value = _uiState.value.copy(
                        vehicleProgress = currentProgress + 0.08f
                    )
                } else {
                    // Reached stop
                    val currentIndex = _uiState.value.vehicleStopIndex
                    val nextIndex = currentIndex + 1
                    
                    if (nextIndex <= stops.size) {
                        // Mark previous stop completed as the driver moves on (very interactive!)
                        val previousStop = stops.getOrNull(currentIndex - 1)
                        if (previousStop != null && !previousStop.isCompleted) {
                            toggleCompleted(previousStop)
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            vehicleStopIndex = nextIndex,
                            vehicleProgress = 0.0f
                        )
                    } else {
                        // Tour completed! Reset.
                        _uiState.value = _uiState.value.copy(
                            isSimulating = false,
                            vehicleStopIndex = 0,
                            vehicleProgress = 0.0f
                        )
                        val completedAlerts = _uiState.value.alerts.toMutableList()
                        completedAlerts.add(0, "Tournée terminée! Le technicien est de retour au dépôt.")
                        _uiState.value = _uiState.value.copy(alerts = completedAlerts)
                        break
                    }
                }
                delay(200) // fast animated increments
            }
        }
    }

    fun stopVehicleSimulation() {
        simulationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isSimulating = false,
            vehicleStopIndex = 0,
            vehicleProgress = 0.0f
        )
    }

    fun selectStop(stop: StopEntity?) {
        _uiState.value = _uiState.value.copy(selectedStop = stop)
    }

    fun toggleAddingStop(isAdding: Boolean) {
        _uiState.value = _uiState.value.copy(isAddingStop = isAdding)
    }

    fun clearAllStops() {
        viewModelScope.launch {
            repository.clearAllStops()
            stopVehicleSimulation()
            _uiState.value = _uiState.value.copy(
                aiExplanation = "Tous les arrêts ont été supprimés de la feuille de route.",
                alerts = listOf("Feuille de route vide. Prêt à renseigner une nouvelle tournée."),
                selectedStop = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
    }
}
