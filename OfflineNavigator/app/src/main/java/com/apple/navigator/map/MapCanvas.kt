package com.apple.navigator.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Hosts the osmdroid MapView inside Compose; the controller owns the instance. */
@Composable
fun MapCanvas(controller: MapController, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { controller.mapView },
        modifier = modifier,
    )
    DisposableEffect(Unit) {
        onDispose {
            controller.onDetach()
        }
    }
}
