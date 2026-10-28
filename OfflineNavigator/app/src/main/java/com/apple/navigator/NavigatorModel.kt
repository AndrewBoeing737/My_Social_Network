package com.apple.navigator

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apple.navigator.location.Fix
import com.apple.navigator.location.LocationSource
import com.apple.navigator.map.OfflineMaps
import com.apple.navigator.map.OfflineMaps.prefetchRegion
import com.apple.navigator.routing.GraphHopperService
import com.apple.navigator.routing.RouteResult
import com.apple.navigator.routing.Suggestion
import com.apple.navigator.routing.TurnSign
import com.apple.navigator.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.Locale

sealed interface Mode {
    data object Setup : Mode
    data object Browse : Mode
    data object Search : Mode
    data object Preview : Mode
    data object Navigating : Mode
    data object Arrived : Mode
}

class NavigatorModel(application: Application) : AndroidViewModel(application) {

    val engine = GraphHopperService.create(application)

    var mode by androidx.compose.runtime.mutableStateOf<Mode>(Mode.Setup)
        private set

    var importMessage by androidx.compose.runtime.mutableStateOf("")
        private set
    var importError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    var query by androidx.compose.runtime.mutableStateOf("")
    var suggestions by androidx.compose.runtime.mutableStateOf<List<Suggestion>>(emptyList())
        private set

    var destination by androidx.compose.runtime.mutableStateOf<Suggestion?>(null)
        private set
    var route by androidx.compose.runtime.mutableStateOf<RouteResult?>(null)
        private set

    var nextLeg by androidx.compose.runtime.mutableStateOf(0)
        private set
    var remainingMeters by androidx.compose.runtime.mutableStateOf(0.0)
        private set
    var remainingSeconds by androidx.compose.runtime.mutableStateOf(0.0)
        private set

    var voiceOn by androidx.compose.runtime.mutableStateOf(true)
        private set
    var infoMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var errorMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    var recents by androidx.compose.runtime.mutableStateOf<List<Suggestion>>(emptyList())
        private set
    var currentFix by androidx.compose.runtime.mutableStateOf<Fix?>(null)
        private set

    private var importJob: Job? = null
    private var searchJob: Job? = null
    private lateinit var locationSource: LocationSource

    private var routePoints = emptyList<DoubleArray>()
    private var routeCumulative = doubleArrayOf()
    private var routeTotal = 0.0
    private var lastPointIndex = 0
    private var legIndex = 0
    private var offRouteCount = 0
    private var spokenLegs = HashSet<Int>()

    private val prefs: android.content.SharedPreferences =
        application.getSharedPreferences("navigator", Context.MODE_PRIVATE)

    init {
        loadRecents()
        viewModelScope.launch(Dispatchers.IO) {
            OfflineMaps.init(application)
            try {
                if (GraphHopperService.hasGraph(application)) {
                    engine.load()
                    withContext(Dispatchers.Main) { mode = Mode.Browse }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { errorMessage = "Failed to load map: ${t.message}" }
            }
        }
        locationSource = LocationSource(application) { fix ->
            currentFix = fix
            if (mode == Mode.Navigating) updateNavigation(fix)
        }
    }

    fun startLocation() = locationSource.start()

    // ------------------------------------------------------------- setup

    fun importMap(stream: java.io.InputStream) {
        if (importJob?.isActive == true) return
        importError = null
        importMessage = "Copying map data\u2026"
        importJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val target = File(getApplication<Application>().filesDir, "import.osm.pbf")
                stream.use { input ->
                    target.outputStream().use { out -> input.copyTo(out, 1024 * 256) }
                }
                importMessage = "Importing map data\u2026 this may take several minutes"
                engine.importAndIndex(target)
                withContext(Dispatchers.Main) {
                    importMessage = ""
                    mode = Mode.Browse
                    infoMessage = "Map ready \u00b7 Turn on GPS to see your position"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    importMessage = ""
                    importError = "Import failed: ${t.message}"
                }
            }
        }
    }

    /** One-time tile download for the visible region (call while still online). */
    fun cacheVisibleRegion(north: Double, east: Double, south: Double, west: Double) {
        infoMessage = "Downloading map tiles\u2026"
        viewModelScope.launch(Dispatchers.IO) {
            var done = -1
            prefetchRegion(getApplication(), org.osmdroid.util.BoundingBox(north, east, south, west), 14, 16) { d, total ->
                done = d * 100 / total
            }
            withContext(Dispatchers.Main) {
                infoMessage = if (done >= 0) "Offline tiles saved \u2713" else "Tile download failed (no network?)"
                kotlinx.coroutines.delay(3000)
                infoMessage = null
            }
        }
    }

    // ------------------------------------------------------------- search

    fun onQueryChanged(text: String) {
        query = text
        searchJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val result = engine.search(text)
            withContext(Dispatchers.Main) { suggestions = result }
        }
    }

    fun openSearch() {
        mode = Mode.Search
        query = ""
        suggestions = emptyList()
    }

    fun cancelSearch() {
        mode = Mode.Browse
        query = ""
        suggestions = emptyList()
    }

    // ------------------------------------------------------------- routing

    fun pick(suggestion: Suggestion) {
        val fix = currentFix ?: locationSource.lastKnown()
        route = null
        destination = suggestion
        saveRecent(suggestion)
        if (fix == null) {
            errorMessage = "Waiting for GPS fix\u2026 try again"
            return
        }
        mode = Mode.Preview
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = engine.route(fix.lat, fix.lon, suggestion.lat, suggestion.lon)
                withContext(Dispatchers.Main) {
                    adoptRoute(result)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    errorMessage = if (t.message != null) t.message!! else "No route found"
                }
            }
        }
    }

    fun cancelPreview() {
        route = null
        destination = null
        mode = Mode.Browse
        resetNavState()
    }

    fun setPointAsDestination(lat: Double, lon: Double) {
        pick(Suggestion("Dropped pin", lat, lon))
    }

    fun startNavigation() {
        if (route == null) return
        mode = Mode.Navigating
        legIndex = 0
        nextLeg = 0
        spokenLegs.clear()
        if (voiceOn) speak("Starting route to ${destination?.title}")
    }

    fun stopNavigation() {
        setRouteOverlay(null)
        route = null
        destination = null
        mode = Mode.Browse
        resetNavState()
    }

    fun dismissArrival() {
        setRouteOverlay(null)
        route = null
        destination = null
        mode = Mode.Browse
        resetNavState()
    }

    fun toggleVoice() {
        voiceOn = !voiceOn
        if (!voiceOn) stopSpeaking()
    }

    private fun adoptRoute(result: RouteResult) {
        route = result
        routePoints = result.points
        routeTotal = 0.0
        routeCumulative = DoubleArray(result.points.size)
        for (i in result.points.indices) {
            if (i > 0) {
                routeTotal += distanceMeters(
                    routePoints[i - 1][0], routePoints[i - 1][1],
                    routePoints[i][0], routePoints[i][1]
                )
            }
            routeCumulative[i] = routeTotal
        }
        lastPointIndex = 0
        legIndex = 0
        offRouteCount = 0
        spokenLegs = HashSet()
        if (result.legs.isNotEmpty()) nextLeg = 0
        if (mode != Mode.Navigating) mode = Mode.Preview
        infoMessage = null
    }

    private fun resetNavState() {
        legIndex = 0
        lastPointIndex = 0
        offRouteCount = 0
        spokenLegs = HashSet()
        routePoints = emptyList()
        routeCumulative = doubleArrayOf()
        routeTotal = 0.0
    }

    private fun updateNavigation(fix: Fix) {
        if (routePoints.isEmpty()) return
        // find closest route geometry point near previous index
        var bestIdx = lastPointIndex
        var bestDist = Double.MAX_VALUE
        val window = 60
        val from = (lastPointIndex - window).coerceAtLeast(0)
        val to = (lastPointIndex + window).coerceAtMost(routePoints.size - 1)
        for (i in from..to) {
            val d = distanceMeters(fix.lat, fix.lon, routePoints[i][0], routePoints[i][1])
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        lastPointIndex = bestIdx
        val progress = routeCumulative[bestIdx]
        remainingMeters = (routeTotal - progress).coerceAtLeast(0.0)
        val avgSpeed = if (routeTotal > 0) routeTotal / (route?.durationSeconds ?: 1.0) else 50.0
        remainingSeconds = remainingMeters / maxOf(avgSpeed, 4.0)

        // leg switching
        val legs = route?.legs ?: return
        var li = legIndex
        while (li + 1 < legs.size && resultLegStart(li + 1) - progress < 30.0) {
            li++
        }
        if (li != legIndex) {
            legIndex = li
            nextLeg = li
        }

        // voice: announce the upcoming maneuver ~350 m before it begins
        val nextStart = if (legIndex + 1 < legs.size) resultLegStart(legIndex + 1) else routeTotal
        val metersToNext = nextStart - progress
        val legToAnnounce = if (legIndex + 1 < legs.size) legIndex + 1 else null
        if (legToAnnounce != null && metersToNext < 350.0 && !spokenLegs.contains(legToAnnounce)) {
            spokenLegs.add(legToAnnounce)
            val leg = legs[legToAnnounce]
            if (voiceOn) speak("${Format.distance(metersToNext)}, ${Format.turnPhrase(leg.sign, leg.roadName)}")
        }
        nextLeg = if (metersToNext < 30.0 && legIndex + 1 < legs.size) legIndex + 1 else legIndex

        // off-route detection
        if (fix.speedMps > 1.0 && bestDist > 45) {
            offRouteCount++
            if (offRouteCount >= 3) {
                offRouteCount = 0
                reroute(fix)
            }
        } else {
            offRouteCount = 0
        }

        // arrival
        if (remainingMeters < 30.0 && bestIdx >= routePoints.size - 3) {
            if (voiceOn) speak("You have arrived at your destination")
            mode = Mode.Arrived
        }
    }

    private fun resultLegStart(i: Int): Double = route?.legStartCumulative?.get(i) ?: 0.0

    private fun reroute(fix: Fix) {
        val dest = destination ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = engine.route(fix.lat, fix.lon, dest.lat, dest.lon)
                withContext(Dispatchers.Main) {
                    adoptRoute(result)
                    if (voiceOn) speak("Rerouting")
                }
            } catch (_: Throwable) {
            }
        }
    }

    // ------------------------------------------------------------- voice

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.US
            }
        }
    }

    fun speak(text: String) {
        ensureTts()
        if (voiceOn) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, System
                .currentTimeMillis().toString())
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun shutDownVoice() {
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------- recents

    private fun loadRecents() {
        val saved = prefs.getString("recents", null) ?: return
        recents = saved.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size == 3) {
                val latLon = parts[1].toDoubleOrNull() to parts[2].toDoubleOrNull()
                if (latLon.first != null && latLon.second != null) {
                    Suggestion(parts[0], latLon.first!!, latLon.second!!)
                } else null
            } else null
        }.take(6)
    }

    private fun saveRecent(s: Suggestion) {
        if (s.title == "Dropped pin") return
        val line = "${s.title}|${s.lat}|${s.lon}"
        val current = prefs.getString("recents", null) ?: ""
        val lines = (line + "\n" + current).split("\n")
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
            .joinToString("\n")
        prefs.edit().putString("recents", lines).apply()
        loadRecents()
    }

    // ------------------------------------------------------------- helpers

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rad = Math.PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 6371000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    val lastKnown: GeoPoint?
        get() {
            val f = currentFix ?: return null
            return GeoPoint(f.lat, f.lon)
        }

    override fun onCleared() {
        stopSpeaking()
        shutDownVoice()
        locationSource.stop()
        engine.release()
        super.onCleared()
    }
}
