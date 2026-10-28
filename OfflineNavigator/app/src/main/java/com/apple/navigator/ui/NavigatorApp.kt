package com.apple.navigator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.TurnSharpLeft
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.material.icons.rounded.TurnSlightLeft
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.navigator.Mode
import com.apple.navigator.NavigatorModel
import com.apple.navigator.map.MapCanvas
import com.apple.navigator.map.MapController
import com.apple.navigator.routing.TurnSign
import com.apple.navigator.util.Format
import org.osmdroid.util.GeoPoint

@Composable
fun NavigatorApp(
    model: NavigatorModel,
    permissionRequested: Boolean,
) {
    val context = LocalContext.current
    val controller = remember {
        MapController(
            context = context,
            onUserPanned = { /* follow mode drops automatically */ },
            onLongPress = { lat, lon -> model.setPointAsDestination(lat, lon) },
        )
    }

    LaunchedEffect(model.route) {
        controller.showRoute(model.route)
    }
    LaunchedEffect(model.currentFix) {
        model.currentFix?.let { controller.onUserLocation(it.lat, it.lon, it.bearingDegrees) }
    }
    LaunchedEffect(permissionRequested) {
        if (permissionRequested) model.startLocation()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapCanvas(controller = controller, modifier = Modifier.fillMaxSize())

        when (model.mode) {
            is Mode.Setup -> SetupOverlay(model)
            is Mode.Browse -> BrowseOverlay(model, controller)
            is Mode.Search -> SearchOverlay(model)
            is Mode.Preview -> PreviewOverlay(model)
            is Mode.Navigating -> NavigateOverlay(model, controller)
            is Mode.Arrived -> ArrivalOverlay(model)
        }
    }
}

// =============================================================== setup

@Composable
private fun SetupOverlay(model: NavigatorModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.let { model.importMap(it) }
                    ?: run { model.importError = "Could not read the selected file" }
            } catch (t: Exception) {
                model.importError = "Could not open file: ${t.message}"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(AppleStyle.cardColor())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Offline Navigator",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (model.importMessage.isBlank())
                    "Import an OpenStreetMap file (.pbf) once — after that, maps, search and driving directions all work fully offline."
                else model.importMessage,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (model.importMessage.isBlank()) {
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Import Map Data\u2026", fontWeight = FontWeight.SemiBold)
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            model.importError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFCC6666), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// =============================================================== browse

@Composable
private fun BrowseOverlay(model: NavigatorModel, controller: MapController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(AppleStyle.cardColor())
                    .clickable { model.openSearch() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Search offline maps",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            RoundButton(
                icon = Icons.Rounded.CloudDownload,
                contentDescription = "Cache visible area",
                enabled = true,
                onClick = {
                    val bb = controller.visibleBoundingBox
                    model.cacheVisibleRegion(bb.latNorth, bb.lonEast, bb.latSouth, bb.lonWest)
                },
            )
        }

        if (model.recents.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(AppleStyle.cardColor())
                    .padding(vertical = 4.dp),
            ) {
                model.recents.take(3).forEach { recent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { model.pick(recent) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(recent.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            RoundButton(
                icon = Icons.Rounded.NearMe,
                contentDescription = "Center on me",
                enabled = !controller.isFollowing,
                onClick = {
                    model.currentFix?.let { fix ->
                        controller.setFollow(true, GeoPoint(fix.lat, fix.lon))
                    }
                },
            )
        }

        model.infoMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(AppleStyle.cardColor())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = 14.sp,
            )
        }
        model.errorMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color(0xCC1C1C1E))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable { model.errorMessage = null },
                fontSize = 14.sp,
            )
        }
    }
}

// =============================================================== search

@Composable
private fun SearchOverlay(model: NavigatorModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { model.cancelSearch() }) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back")
            }
            BasicTextField(
                value = model.query,
                onValueChange = { model.onQueryChanged(it) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (model.query.isEmpty()) {
                        Text("Search street\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                },
            )
            IconButton(onClick = { model.cancelSearch() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(model.suggestions) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { model.pick(suggestion) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(suggestion.title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (model.suggestions.isEmpty() && model.query.isNotBlank()) {
                item {
                    Text(
                        "No streets match \u201c${model.query}\u201d",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// =============================================================== preview

@Composable
private fun PreviewOverlay(model: NavigatorModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(AppleStyle.cardColor())
                .padding(18.dp),
        ) {
            Text(
                model.destination?.title ?: "Route",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            val route = model.route
            if (route == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Calculating route\u2026", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    Format.shortDistance(route.distanceMeters) + "  \u00b7  " + Format.duration(route.durationSeconds),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    Button(
                        onClick = { model.startNavigation() },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Go", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { model.cancelPreview() },
                        modifier = Modifier.padding(start = 6.dp),
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

// =============================================================== navigating

@Composable
private fun NavigateOverlay(model: NavigatorModel, controller: MapController) {
    val legs = model.route?.legs
    val next = legs?.getOrNull(model.nextLeg)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        AnimatedVisibility(visible = next != null, enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(AppleStyle.cardColor())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (next != null) {
                    Icon(
                        iconFor(next.sign),
                        contentDescription = next.sign.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            Format.distance(metersToNextTurn(model)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            next.roadName,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .padding(bottom = 18.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(AppleStyle.cardColor())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(
                    Format.duration(model.remainingSeconds),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    Format.shortDistance(model.remainingMeters),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            RoundButton(
                icon = if (model.voiceOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                contentDescription = if (model.voiceOn) "Mute voice" else "Unmute voice",
                enabled = true,
                onClick = { model.toggleVoice() },
            )
            Spacer(Modifier.width(10.dp))
            RoundButton(
                icon = Icons.Rounded.Close,
                contentDescription = "End navigation",
                enabled = true,
                onClick = { model.stopNavigation() },
            )
        }
    }
}

private fun metersToNextTurn(model: NavigatorModel): Double {
    val route = model.route ?: return 0.0
    val cum = route.legStartCumulative
    val idx = model.nextLeg
    val progress = route.distanceMeters - model.remainingMeters
    val target = if (idx >= 0 && idx < cum.size) cum[idx] else route.distanceMeters
    return (target - progress).coerceAtLeast(0.0)
}

// =============================================================== arrival

@Composable
private fun ArrivalOverlay(model: NavigatorModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .clip(MaterialTheme.shapes.large)
                .background(AppleStyle.cardColor())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.Flag,
                contentDescription = "Arrived",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("You have arrived", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            model.destination?.let {
                Text(it.title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { model.dismissArrival() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Done", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// =============================================================== shared

@Composable
private fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(AppleStyle.cardColor(0.92f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

fun iconFor(sign: TurnSign): ImageVector = when (sign) {
    TurnSign.TURN_LEFT -> Icons.Rounded.TurnLeft
    TurnSign.TURN_RIGHT -> Icons.Rounded.TurnRight
    TurnSign.TURN_SLIGHT_LEFT -> Icons.Rounded.TurnSlightLeft
    TurnSign.TURN_SLIGHT_RIGHT -> Icons.Rounded.TurnSlightRight
    TurnSign.TURN_SHARP_LEFT -> Icons.Rounded.TurnSharpLeft
    TurnSign.TURN_SHARP_RIGHT -> Icons.Rounded.TurnSharpRight
    TurnSign.KEEP_LEFT -> Icons.Rounded.TurnSlightLeft
    TurnSign.KEEP_RIGHT -> Icons.Rounded.TurnSlightRight
    TurnSign.UTURN -> Icons.Rounded.UTurnLeft
    TurnSign.ROUNDABOUT -> Icons.Rounded.Straight
    TurnSign.STRAIGHT -> Icons.Rounded.Straight
}
