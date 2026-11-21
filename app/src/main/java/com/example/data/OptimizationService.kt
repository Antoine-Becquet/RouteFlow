package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

data class OptimizationResult(
    val optimizedStops: List<StopEntity>,
    val explanation: String
)

object OptimizationService {

    private const val TAG = "OptimizationService"
    private const val DEFAULT_MODEL = "gemini-2.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(65, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Optimizes the tour stops. If GEMINI_API_KEY is available and active, uses the Gemini API.
     * Otherwise, falls back to the local high-fidelity solver.
     */
    suspend fun optimizeTour(
        stops: List<StopEntity>,
        hubX: Float,
        hubY: Float,
        startHour: String = "08:00",
        trafficFactor: Float = 1.0f // 1.0 (Normal), 1.5 (Heavy), 2.0 (Extreme)
    ): OptimizationResult = withContext(Dispatchers.IO) {
        if (stops.isEmpty()) {
            return@withContext OptimizationResult(emptyList(), "Aucun arrêt à optimiser.")
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        val hasApiKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

        if (hasApiKey) {
            try {
                Log.d(TAG, "Attempting Gemini AI route optimization with $DEFAULT_MODEL...")
                val result = optimizeWithGemini(stops, hubX, hubY, startHour, trafficFactor, apiKey)
                if (result != null) {
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini optimization unavailable (${e.javaClass.simpleName}: ${e.message}), falling back to local solver")
            }
        }

        // Offline / Fallback local heuristic solver (Highly stable & realistic)
        Log.d(TAG, "Using high-fidelity local itinerary solver")
        return@withContext optimizeLocally(stops, hubX, hubY, startHour, trafficFactor)
    }

    /**
     * Local greedy heuristic algorithm that builds an optimized path respecting:
     * 1. Distance (Euclidean)
     * 2. Desired Time Windows ("tranches horaires")
     * 3. Real-time simulated traffic conditions
     */
    fun optimizeLocally(
        stops: List<StopEntity>,
        hubX: Float,
        hubY: Float,
        startHour: String,
        trafficFactor: Float
    ): OptimizationResult {
        // Parse starting time
        var currentMinutes = parseTimeToMinutes(startHour)
        val sortedStopsList = mutableListOf<StopEntity>()
        val remainingStops = stops.toMutableList()

        var currentX = hubX
        var currentY = hubY

        // Traffic conditions simulation
        // Fluid = 10% average delay, Dense = 40% delay, Saturé = 90% delay
        val delayMultiplier = when {
            trafficFactor >= 1.8f -> 1.8f
            trafficFactor >= 1.4f -> 1.4f
            else -> 1.0f
        }

        while (remainingStops.isNotEmpty()) {
            // Find best next stop based on a score
            // Score = Distance_Cost + Time_Window_Urgency
            var bestCandidate: StopEntity? = null
            var bestScore = Double.MAX_VALUE
            var bestTravelTime = 0

            val hasHighRemaining = remainingStops.any { it.priority == "High" }
            val hasNormalRemaining = remainingStops.any { it.priority == "Normal" }

            for (stop in remainingStops) {
                // Tiered Priority: Force High priority stops to be visited before everything else
                if (hasHighRemaining && stop.priority != "High") {
                    continue
                }
                // Force Normal priority stops to be visited before Low priority stops
                if (!hasHighRemaining && hasNormalRemaining && stop.priority == "Low") {
                    continue
                }

                val distance = calculateDistance(currentX.toDouble(), currentY.toDouble(), stop.latitude.toDouble(), stop.longitude.toDouble())
                
                // Real-world travel time: ~25 km/h urban speed -> ~2.4 mins per km
                val realSpeedKmPerMin = 0.40f / delayMultiplier
                val travelTimeMinutes = (distance / realSpeedKmPerMin).toInt().coerceAtLeast(5)

                val arrivalMinutes = currentMinutes + travelTimeMinutes

                // Time window penalty
                var timeWindowPenalty = 0.0
                if (stop.hasTimeSlot) {
                    val slotStartMin = parseTimeToMinutes(stop.timeSlotStart)
                    val slotEndMin = parseTimeToMinutes(stop.timeSlotEnd)

                    if (arrivalMinutes > slotEndMin) {
                        // Late penalty is extremely heavy
                        timeWindowPenalty = (arrivalMinutes - slotEndMin) * 20.0
                    } else if (arrivalMinutes < slotStartMin) {
                        // Arriving early requires waiting, mild penalty to keep route compact
                        timeWindowPenalty = (slotStartMin - arrivalMinutes) * 2.0
                    }
                }

                // Priority factor influence on route sequencing
                val priorityBonus = when (stop.priority) {
                    "High" -> -15.0 // heavy incentive to prioritize earlier visits
                    "Low" -> 15.0   // de-prioritize
                    else -> 0.0
                }

                // Total score (lower is better)
                val totalScore = distance + timeWindowPenalty + priorityBonus

                if (totalScore < bestScore) {
                    bestScore = totalScore
                    bestCandidate = stop
                    bestTravelTime = travelTimeMinutes
                }
            }

            if (bestCandidate != null) {
                remainingStops.remove(bestCandidate)

                // Update travel stats for candidate
                val trafficStatus = when {
                    delayMultiplier >= 1.8f -> "Saturé (+80% temps)"
                    delayMultiplier >= 1.4f -> "Dense (+40% temps)"
                    else -> "Fluide"
                }

                val arrivalMinutes = currentMinutes + bestTravelTime
                
                // Wait if arrived too early
                var finalArrivalMinutes = arrivalMinutes
                if (bestCandidate.hasTimeSlot) {
                    val slotStartMin = parseTimeToMinutes(bestCandidate.timeSlotStart)
                    if (finalArrivalMinutes < slotStartMin) {
                        finalArrivalMinutes = slotStartMin // must wait till slot starts
                    }
                }

                // Simulated traffic incident addition for extra realism
                val trafficDelay = if (bestCandidate.id % 3 == 0 && trafficFactor > 1.0f) {
                    (8 * delayMultiplier).toInt()
                } else {
                    0
                }
                finalArrivalMinutes += trafficDelay

                val arrivalTimeString = formatMinutesToTime(finalArrivalMinutes)

                // Save optimized values
                val updatedStop = bestCandidate.copy(
                    orderIndex = sortedStopsList.size + 1,
                    estimatedArrivalTime = arrivalTimeString,
                    delayMinutes = trafficDelay + (bestTravelTime * (delayMultiplier - 1.0f)).toInt(),
                    trafficStatus = trafficStatus
                )

                sortedStopsList.add(updatedStop)

                // Advance time: arrival + duration of intervention at that stop + traffic delay
                currentMinutes = finalArrivalMinutes + bestCandidate.durationMinutes
                currentX = bestCandidate.latitude
                currentY = bestCandidate.longitude
            } else {
                break
            }
        }

        // Generate custom intelligent explanation in French
        val explanation = buildString {
            append("Tournée d'adresse optimisée localement avec succès. ")
            append("L'algorithme a ordonné les arrêts pour minimiser la distance cumulée tout en respectant au mieux vos tranches horaires. ")
            
            val lateCount = sortedStopsList.count { s ->
                if (s.hasTimeSlot && s.estimatedArrivalTime != null) {
                    val arr = parseTimeToMinutes(s.estimatedArrivalTime)
                    val end = parseTimeToMinutes(s.timeSlotEnd)
                    arr > end
                } else false
            }

            if (lateCount == 0) {
                append("Tous les créneaux horaires sont parfaitement respectés avec le trafic actuel.")
            } else {
                append("Attention: suite aux ralentissements ($lateCount) créneau(x) horaire(s) risque(nt) d'être dépassé(s). ")
                append("Pense d'ajuster l'ordre ou de partir plus tôt.")
            }
        }

        return OptimizationResult(sortedStopsList, explanation)
    }

    private suspend fun optimizeWithGemini(
        stops: List<StopEntity>,
        hubX: Float,
        hubY: Float,
        startHour: String,
        trafficFactor: Float,
        apiKey: String
    ): OptimizationResult? {
        val jsonStops = JSONArray()
        stops.forEach { stop ->
            val obj = JSONObject()
            obj.put("id", stop.id)
            obj.put("address", stop.address)
            obj.put("hasTimeSlot", stop.hasTimeSlot)
            obj.put("timeSlotStart", stop.timeSlotStart)
            obj.put("timeSlotEnd", stop.timeSlotEnd)
            obj.put("durationMinutes", stop.durationMinutes)
            obj.put("priority", stop.priority)
            obj.put("lat", stop.latitude)
            obj.put("lng", stop.longitude)
            jsonStops.put(obj)
        }

        val prompt = """
            On te fournit une liste d'arrêts de livraison (format JSON) à optimiser pour une tournée d'un technicien.
            Point de départ (Dépôt Central): Lat $hubX, Lng $hubY à $startHour.
            Facteur trafic: $trafficFactor (1.0 = normal, 1.5 = dense, 2.0 = saturé).
            
            Détermine l'ordre de visite optimal (orderIndex) en respectant ces règles strictes:
            1. Respecte STRICTEMENT les priorités: les arrêts avec une priorité "High" (haute) doivent être planifiés en premier, puis les arrêts de priorité "Normal", et enfin les arrêts de priorité "Low" (basse), même si les arrêts de priorité inférieure sont sur le chemin terrestre ou plus proches.
            2. Minimise la distance géométrique cumulée à l'intérieur de chaque niveau de priorité.
            3. Respecte au mieux les tranches horaires (timeSlotStart à timeSlotEnd) si hasTimeSlot est VRAI (au sein de son groupe de priorité).
            4. Ajoute des temps de trajet réalistes avec un délai aléatoire modéré dû au trafic. Le trafic moyen actuel est multiplié par le facteur trafic.
            
            Données d'entrée:
            ${jsonStops}
            
            Retourne STRICTEMENT et UNIQUEMENT un objet JSON valide contenant deux champs clés:
            1. "optimized": Un tableau d'objets pour chaque arrêt avec UNIQUEMENT les champs ("id", "orderIndex", "estimatedArrivalTime" au format "HH:MM", "delayMinutes" (nombre entier du délai trafic simulé), "trafficStatus" (parmi 'Fluide', 'Dense', 'Saturé')).
            2. "explanation": Un court texte en français résumant les grandes décisions d'optimisation prises (par exemple: pourquoi un arrêt a été planifié avant ou après, l'impact du trafic détecté).
            
            Exemple de format de réponse attendu:
            {
              "optimized": [
                { "id": 1, "orderIndex": 1, "estimatedArrivalTime": "08:25", "delayMinutes": 2, "trafficStatus": "Fluide" },
                { "id": 2, "orderIndex": 2, "estimatedArrivalTime": "09:10", "delayMinutes": 12, "trafficStatus": "Dense" }
              ],
              "explanation": "Itinéraire optimisé. L'arrêt #1 a été traité en premier car il est proche, et l'arrêt #2 a été reculé en raison d'un bouchon sur la route principale menant à son secteur."
            }
            
            Ne rajoute AUCUN texte d'introduction ni de conclusion, réponds uniquement avec le JSON.
        """.trimIndent()

        // Construct request body
        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", prompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        requestJson.put("contents", contentsArray)

        // Request fast, deterministic JSON output without extended thinking latency
        val generationConfig = JSONObject().apply {
            put("responseMimeType", "application/json")
            put("temperature", 0.1)
            put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }
        requestJson.put("generationConfig", generationConfig)

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$DEFAULT_MODEL:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val responseBodyString = client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini API returned HTTP ${response.code}: ${bodyStr?.take(200)}")
                return null
            }
            bodyStr ?: return null
        }

        val responseJson = JSONObject(responseBodyString)
        val candidates = responseJson.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null

        val rawText = parts.getJSONObject(0).optString("text", "")
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (rawText.isEmpty()) return null
        Log.d(TAG, "Raw response from Gemini:\n$rawText")

        // Parse Gemini JSON output
        val parsedResult = JSONObject(rawText)
        val optimizedArray = parsedResult.optJSONArray("optimized") ?: return null
        if (optimizedArray.length() == 0) return null
        val explanationText = parsedResult.optString("explanation", "Optimisation réussie.")

        // Build mapping
        val indexMap = mutableMapOf<Int, Triple<Int, String, Int>>() // id -> Triple(order, arrival, delay)
        val trafficMap = mutableMapOf<Int, String>() // id -> status
        for (i in 0 until optimizedArray.length()) {
            val sObj = optimizedArray.getJSONObject(i)
            val id = sObj.getInt("id")
            val orderIndex = sObj.getInt("orderIndex")
            val estArrival = sObj.optString("estimatedArrivalTime", "08:00")
            val delay = sObj.optInt("delayMinutes", 0)
            val tStatus = sObj.optString("trafficStatus", "Fluide")

            indexMap[id] = Triple(orderIndex, estArrival, delay)
            trafficMap[id] = tStatus
        }

        // Remap to original stops and ensure sequential 1-based ordering
        val optimizedStops = stops.mapIndexed { fallbackIdx, stop ->
            val mapping = indexMap[stop.id]
            if (mapping != null) {
                stop.copy(
                    orderIndex = mapping.first,
                    estimatedArrivalTime = mapping.second,
                    delayMinutes = mapping.third,
                    trafficStatus = trafficMap[stop.id] ?: "Fluide"
                )
            } else {
                stop.copy(orderIndex = stops.size + fallbackIdx + 1)
            }
        }.sortedBy { it.orderIndex }
            .mapIndexed { idx, stop -> stop.copy(orderIndex = idx + 1) }

        return OptimizationResult(optimizedStops, explanationText)
    }

    // --- Utility math functions ---

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val straightLine = r * c
        // Facteur de détour urbain pour simuler les routes réelles (~28% de distance en plus que le vol d'oiseau)
        return straightLine * 1.28
    }

    fun parseTimeToMinutes(timeString: String): Int {
        return try {
            val parts = timeString.split(":")
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            hours * 60 + minutes
        } catch (e: Exception) {
            480 // defaults to 08:00
        }
    }

    fun formatMinutesToTime(minutes: Int): String {
        val totalMins = minutes % 1440
        val h = totalMins / 60
        val m = totalMins % 60
        return String.format("%02d:%02d", h, m)
    }
}
