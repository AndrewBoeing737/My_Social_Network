package com.apple.navigator.map

import android.content.Context
import android.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Offline services around osmdroid: initialization and the one-time
 * ("while you're still online") tile pre-download into osmdroid's sqlite
 * cache. After the download the map renders with zero network access.
 */
object OfflineMaps {

    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        Configuration.getInstance().apply {
            load(context, prefs)
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.filesDir, "osmdroid/tiles")
        }
    }

    fun cacheDir(context: Context): File =
        File(context.filesDir, "osmdroid/tiles").apply { mkdirs() }

    val TILE_SOURCE = TileSourceFactory.MAPNIK

    /**
     * Downloads all MAPNIK tiles for the given bounding box at zoom levels
     * [minZoom, maxZoom] into osmdroid's local sqlite tile cache.
     * Run once with connectivity; afterwards the map is fully offline.
     */
    fun prefetchRegion(
        context: Context,
        box: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        onProgress: (done: Int, total: Int) -> Unit,
    ) {
        val writer = SqlTileWriter()
        val source = TILE_SOURCE
        var done = 0

        val tasks = ArrayList<TileTask>()
        for (zoom in minZoom..maxZoom) {
            val tilesX = 1 shl zoom
            val minX = lonToTileX(box.lonEast, zoom).coerceIn(0, tilesX - 1)
            val maxX = lonToTileX(box.lonWest, zoom).coerceIn(0, tilesX - 1)
            val minY = latToTileY(box.latNorth, zoom).coerceIn(0, tilesX - 1)
            val maxY = latToTileY(box.latSouth, zoom).coerceIn(0, tilesX - 1)
            for (x in minOf(minX, maxX)..maxOf(minX, maxX)) {
                for (y in minOf(minY, maxY)..maxOf(minY, maxY)) {
                    tasks += TileTask(zoom, x, y, "https://tile.openstreetmap.org/$zoom/$x/$y.png")
                }
            }
        }

        for (task in tasks) {
            try {
                val connection = URL(task.url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "OfflineNavigator/1.0 (Android)")
                if (connection.responseCode == 200) {
                    val bytes = connection.inputStream.readBytes()
                    val index = MapTileIndex.getTile(task.zoom, task.x, task.y)
                    val stream = java.io.ByteArrayInputStream(bytes)
                    writer.saveFile(source, index, stream, bytes.size.toLong())
                }
            } catch (_: Exception) {
                // skip tile; area stays cached-less but map still works online
            }
            done++
            if (done % 20 == 0 || done == tasks.size) onProgress(done, tasks.size)
        }
        writer.close()
    }

    private data class TileTask(val zoom: Int, val x: Int, val y: Int, val url: String)

    fun lonToTileX(lon: Double, zoom: Int): Int =
        floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()

    fun latToTileY(lat: Double, zoom: Int): Int {
        val clamped = lat.coerceIn(MIN_LAT, MAX_LAT)
        val rad = Math.toRadians(clamped)
        val n = 1 shl zoom
        return floor(
            (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / Math.PI) / 2.0 * n
        ).toInt()
    }

    private const val MIN_LAT = -85.0511
    private const val MAX_LAT = 85.0511
}
