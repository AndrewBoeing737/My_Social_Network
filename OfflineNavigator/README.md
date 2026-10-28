# OfflineNavigator — Apple-Maps-style offline turn-by-turn navigator for Android

A self-contained Android app (Kotlin + Jetpack Compose) that looks like Apple
Maps and works **fully offline**: map tiles, street search, routing,
re-routing and voice guidance are all local. Nothing in daily use touches the
network.

Stack:

- **osmdroid** — raster map + local sqlite tile cache
- **GraphHopper embedded** — OSM PBF -> street graph, driving routes,
  turn instructions (runs 100% on-device)
- OpenStreetMap data: you import the map area once, e.g. from Geofabrik

## Build

1. Open the `OfflineNavigator/` folder in **Android Studio** (Ladybug or
   newer). The wrapper (`gradle-8.10.2`, AGP 8.7.3, Kotlin 2.0.20) is pinned
   in `gradle/wrapper/gradle-wrapper.properties`; Studio downloads it
   automatically.
2. Press Run. Requires Android **8.0+ (API 26)**.

## Prepare offline data (one time, while you have connectivity)

1. Download an OSM extract in **PBF format** from
   <https://download.geofabrik.de> (pick a state/city-sized region for the
   first try; whole countries need lots of RAM).
2. In the app: **Import Map Data…** -> pick the `.pbf`. Import takes minutes
   for a city (a country can take 20+ minutes).
3. For map tiles: optionally download MBTiles of the same area with
   Mobile Atlas Creator (osmdroid/OpenStreetMap format) and copy them into
   `Android/data/com.apple.navigator/files/osmdroid/tiles/`, **or** press the
   download button in the app while the map shows your region — it caches
   tiles zoom 14-16 of the visible area into the local sqlite cache.
4. Done. Airplane mode now: search, routing, navigation and voice all work.

## How to use

- **Long-press the map** to drop a pin (like Apple Maps), or search a street
  by name (search covers every road name in the imported area).
- Route preview shows distance/time, then **Go** starts turn-by-turn
  navigation: the route line, the maneuver banner, ETA bar, automatic
  voice announcements (via the system TTS), and automatic re-routing when
  you leave the route.
- Recents are remembered on the home screen; a downloaded-area button keeps
  offline tiles topped up before you go offline.

## File map

```
app/src/main/java/com/apple/navigator/
  MainActivity.kt            # permissions + entry
  NavigatorModel.kt          # app state machine, offline nav logic, voice
  routing/GraphHopperService.kt  # OSM import, routing, street-name index
  map/MapController.kt       # osmdroid: map, route line, user marker, follow cam
  map/MapCanvas.kt           # Compose <-> MapView bridge
  map/OfflineMaps.kt         # osmdroid init + offline tile downloader
  location/LocationSource.kt # GPS fixes (no Google Play dependency)
  ui/Theme.kt                # iOS-style colors, shapes with rounded cards
  ui/NavigatorApp.kt         # all five UI states
  util/Format.kt             # distance/time/turn phrases
```

## Notes & limits

- The embedded GraphHopper engine is pinned to **6.2** (classic stable API);
  the graph loads into RAM (`RAM_STORE`), so prefer city/state extracts.
- Search is a street-name index (forwards + word-contains match), built at
  import time. Points-of-interest search is not included.
- Map raster tiles come from the standard osmdroid tile cache — after the
  one-time download no tile server is contacted.
