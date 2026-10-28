package com.apple.navigator.map

import android.content.Context
import androidx.core.content.ContextCompat
import com.apple.navigator.R
import com.apple.navigator.routing.RouteResult
import org.osmdroid.events.MapEvent
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.MapEventsReceiver
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Owns the osmdroid [MapView], route drawing, user marker and "follow me"
 * camera. Pure android views, embedded into Compose via AndroidView in
 * MapCanvas.kt.
 */
class MapController(
    private val context: Context,
    private val onUserPanned: () -> Unit,
    private val onLongPress: (lat: Double, lon: Double) -> Unit,
) {
    val mapView: MapView = MapView(context).apply {
        setTileSource(OfflineMaps.TILE_SOURCE)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        setTilesScaledToDpi(true)
        minZoomLevel = 4.0
        maxZoomLevel = 19.0
        controller.setZoom(14.0)
    }

    private val mapController: MapController = mapView.controller

    private val routeCasing = Polyline(mapView).apply {
        outlinePaint.strokeWidth = 26f
        outlinePaint.color = 0xE61E3A8A.toInt() // deep blue casing
    }
    private val routeLine = Polyline(mapView).apply {
        paint.strokeWidth = 18f
        paint.color = 0xFF0A84FF.toInt() // Apple Maps route blue
    }

    private val destinationMarker = Marker(mapView).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.marker_destination)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    }
    private val userMarker = Marker(mapView).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.marker_user)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    }

    private var followUser = true
    private var userMarkerOnMap = false

    init {
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (event != null) {
                    followUser = false
                    onUserPanned()
                }
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean = false
        })
        mapView.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false

                override fun longPressHelper(p: GeoPoint?): Boolean {
                    p ?: return false
                    onLongPress(p.latitude, p.longitude)
                    return true
                }
            })
        )
        showRoute(null)
    }

    fun setFollow(enabled: Boolean, recenterTo: GeoPoint? = null) {
        followUser = enabled
        recenterTo?.let { recenter(it) }
    }

    val isFollowing: Boolean get() = followUser

    private fun recenter(point: GeoPoint) {
        mapController.animateTo(point)
    }

    fun onUserLocation(lat: Double, lon: Double, headingDegrees: Float?) {
        val point = GeoPoint(lat, lon)
        userMarker.position = point
        headingDegrees?.let { userMarker.rotation = it }
        if (!userMarkerOnMap) {
            mapView.overlays.add(userMarker)
            userMarkerOnMap = true
        }
        if (followUser) {
            mapController.animateTo(point)
        }
        mapView.invalidate()
    }

    fun showRoute(route: RouteResult?) {
        mapView.overlays.remove(routeLine)
        mapView.overlays.remove(routeCasing)
        mapView.overlays.remove(destinationMarker)
        if (route == null || route.points.isEmpty()) {
            mapView.invalidate()
            return
        }
        val points = route.points.map { GeoPoint(it[0], it[1]) }
        routeLine.setPoints(points)
        routeCasing.setPoints(points)
        destinationMarker.position = points.last()
        mapView.overlays.add(routeCasing)
        mapView.overlays.add(routeLine)
        mapView.overlays.add(destinationMarker)
        fitRoute(points)
        mapView.invalidate()
    }

    private fun fitRoute(points: List<GeoPoint>) {
        if (points.isEmpty()) return
        var north = points.first().latitude
        var south = north
        var east = points.first().longitude
        var west = east
        for (p in points) {
            if (p.latitude > north) north = p.latitude
            if (p.latitude < south) south = p.latitude
            if (p.longitude > east) east = p.longitude
            if (p.longitude < west) west = p.longitude
        }
        val box = BoundingBox(north, east, south, west)
        mapView.post {
            try {
                mapView.zoomToBoundingBox(box, false, 96)
            } catch (_: Exception) {
                // map not laid out yet, skip
            }
        }
    }

    fun centerOn(lat: Double, lon: Double, zoom: Double = 17.0) {
        mapController.setZoom(zoom)
        recenter(GeoPoint(lat, lon))
    }

    val visibleBoundingBox: BoundingBox
        get() = mapView.boundingBox

    fun onDetach() {
        mapView.onDetach()
    }
}
