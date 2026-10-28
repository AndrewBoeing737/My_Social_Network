package com.apple.navigator.routing

import android.content.Context
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import com.graphhopper.util.FetchMode
import com.graphhopper.util.Instruction
import com.graphhopper.util.Parameters
import java.io.File

enum class TurnSign {
    STRAIGHT, TURN_LEFT, TURN_SLIGHT_LEFT, TURN_SHARP_LEFT,
    TURN_RIGHT, TURN_SLIGHT_RIGHT, TURN_SHARP_RIGHT,
    UTURN, ROUNDABOUT, KEEP_LEFT, KEEP_RIGHT,
}

data class RouteLeg(
    val sign: TurnSign,
    val roadName: String,
    val distanceMeters: Double,
)

data class RouteResult(
    /** full route geometry, lat/lon pairs; also used to draw the polyline */
    val points: List<DoubleArray>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val legs: List<RouteLeg>,
) {
    /** cumulative distance at the start of each leg */
    val legStartCumulative: DoubleArray by lazy {
        val acc = DoubleArray(legs.size + 1)
        for (i in legs.indices) acc[i + 1] = acc[i] + legs[i].distanceMeters
        acc
    }
}

data class Suggestion(
    val title: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Fully offline routing + street search built on GraphHopper.
 *
 *  1. User imports an OSM extract (.pbf, e.g. from Geofabrik) once -> importAndIndex()
 *  2. The graph is stored locally and re-loaded from disk on every launch -> load()
 *  3. route() / search() afterwards never touch the network.
 */
class GraphHopperService private constructor(
    private val context: Context,
    private val ghDir: File,
) {
    private var hopper: GraphHopper? = null

    /** street name -> sample coordinate (built at import; used for offline search) */
    private val streetIndex = LinkedHashMap<String, GHPoint2>(4096)

    val isReady: Boolean get() = hopper != null

    companion object {
        fun graphHopperDir(context: Context): File =
            File(context.filesDir, "graph-osm").apply { mkdirs() }

        fun hasGraph(context: Context): Boolean =
            File(graphHopperDir(context), "properties").exists()

        fun create(context: Context): GraphHopperService =
            GraphHopperService(context, graphHopperDir(context))
    }

    // ------------------------------------------------------------- import

    /**
     * Imports an OSM PBF into local graph storage and builds the street index.
     * Blocking: run on a background dispatcher. City extracts take a few
     * minutes; a whole country needs lots of RAM and much more time.
     */
    fun importAndIndex(osmFile: File) {
        release()
        val hopper = newHopper(osmFile.absolutePath)
        try {
            hopper.importOrLoad()
        } catch (t: Throwable) {
            releaseSafely(hopper)
            throw t
        }
        this.hopper = hopper
        buildStreetIndex()
    }

    /** Loads an already imported graph from disk (fast, no OSM parsing). */
    fun load() {
        release()
        val hopper = GraphHopper().apply {
            setGraphHopperLocation(ghDir.absolutePath)
            setProfiles(
                Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false)
            )
        }
        try {
            if (!hopper.load()) {
                throw IllegalStateException("No imported map found. Import a map file first.")
            }
        } catch (t: Throwable) {
            releaseSafely(hopper)
            throw t
        }
        this.hopper = hopper
        buildStreetIndex()
    }

    private fun newHopper(osmPath: String): GraphHopper =
        GraphHopper().apply {
            setOSMFile(osmPath)
            setGraphHopperLocation(ghDir.absolutePath)
            setProfiles(
                Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false)
            )
            // smaller index => faster startup + less RAM, fine for navigation
            setPreciseIndexResolution(500)
            setMinNetworkSize(100)
        }

    // ------------------------------------------------------------- routing

    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RouteResult {
        val hopper = hopper ?: throw IllegalStateException("Graph not imported yet")
        val request = GHRequest(fromLat, fromLon, toLat, toLon)
            .setProfile("car")
            .setLocale("en")
            .setAlgorithm(Parameters.Algorithms.ASTAR_BI)
        val response = hopper.route(request)
        if (response.hasErrors()) {
            val message = response.errors.firstOrNull()?.message ?: "No route found"
            throw IllegalArgumentException(message)
        }
        val path = response.best
        val points = path.points
        val geometry = ArrayList<DoubleArray>(points.size)
        for (i in 0 until points.size) {
            geometry.add(doubleArrayOf(points.getLat(i), points.getLon(i)))
        }
        val legs = ArrayList<RouteLeg>(path.instructions.size)
        for (leg in path.instructions) legs += leg.toRouteLeg()
        return RouteResult(
            points = geometry,
            distanceMeters = path.distance,
            durationSeconds = path.time / 1000.0,
            legs = legs,
        )
    }

    private fun com.graphhopper.util.Instruction.toRouteLeg(): RouteLeg {
        val turn = when (sign) {
            Instruction.KEEP_LEFT, Instruction.U_TURN_LEFT -> TurnSign.KEEP_LEFT
            Instruction.TURN_SHARP_LEFT -> TurnSign.TURN_SHARP_LEFT
            Instruction.TURN_LEFT -> TurnSign.TURN_LEFT
            Instruction.TURN_SLIGHT_LEFT -> TurnSign.TURN_SLIGHT_LEFT
            Instruction.TURN_SLIGHT_RIGHT -> TurnSign.TURN_SLIGHT_RIGHT
            Instruction.TURN_RIGHT -> TurnSign.TURN_RIGHT
            Instruction.TURN_SHARP_RIGHT -> TurnSign.TURN_SHARP_RIGHT
            Instruction.KEEP_RIGHT, Instruction.U_TURN_RIGHT -> TurnSign.KEEP_RIGHT
            Instruction.U_TURN_UNKNOWN -> TurnSign.UTURN
            Instruction.USE_ROUNDABOUT, Instruction.LEAVE_ROUNDABOUT -> TurnSign.ROUNDABOUT
            Instruction.FINISH -> TurnSign.STRAIGHT
            else -> TurnSign.STRAIGHT
        }
        return RouteLeg(turn, name.ifBlank { "the road" }, distance)
    }

    // ------------------------------------------------------------- search

    /**
     * Offline search: prefix + word match over street names of the imported
     * map. Enough to pick a street as a destination.
     */
    fun search(query: String, limit: Int = 12): List<Suggestion> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val prefix = ArrayList<Suggestion>()
        val contained = ArrayList<Suggestion>()
        for ((name, point) in streetIndex) {
            val lower = name.lowercase()
            when {
                lower.startsWith(q) -> prefix += Suggestion(name, point.lat, point.lon)
                lower.contains(" $q") || lower.contains("-$q") ->
                    contained += Suggestion(name, point.lat, point.lon)
            }
        }
        return (prefix + contained).take(limit)
    }

    private fun buildStreetIndex() {
        streetIndex.clear()
        val storage = hopper?.baseGraph ?: return
        val edges = storage.allEdges
        while (edges.next()) {
            val name = edges.name
            if (name.isNullOrBlank() || streetIndex.containsKey(name)) continue
            val geometry = edges.fetchWayGeometry(FetchMode.PILLAR_ONLY)
            if (geometry.size == 0) continue
            val mid = geometry.size / 2
            streetIndex[name] = GHPoint2(geometry.getLat(mid), geometry.getLon(mid))
        }
    }

    fun release() {
        hopper?.let { releaseSafely(it) }
        hopper = null
        streetIndex.clear()
    }

    private fun releaseSafely(h: GraphHopper) {
        try {
            h.close()
        } catch (_: Throwable) {
        }
    }
}

private data class GHPoint2(val lat: Double, val lon: Double)
