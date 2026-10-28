package com.apple.navigator.util

import java.util.Locale
import kotlin.math.roundToInt

object Format {
    fun distance(meters: Double): String = when {
        meters < 25 -> "in ${meters.roundToInt()} m"
        meters < 950 -> "in ${meters.roundToInt() / 10 * 10} m"
        meters < 95_000 -> String.format(Locale.US, "in %.1f km", meters / 1000)
        else -> String.format(Locale.US, "in %d km", (meters / 1000).roundToInt())
    }

    fun shortDistance(meters: Double): String = when {
        meters < 950 -> "${meters.roundToInt()} m"
        meters < 95_000 -> String.format(Locale.US, "%.1f km", meters / 1000)
        else -> String.format(Locale.US, "%d km", (meters / 1000).roundToInt())
    }

    fun duration(seconds: Double): String {
        val minutes = (seconds / 60).roundToInt()
        if (minutes < 1) return "<1 min"
        return if (minutes < 60) "$minutes min"
        else "${minutes / 60} hr ${minutes % 60} min"
    }

    fun turnPhrase(sign: com.apple.navigator.routing.TurnSign, name: String): String = when (sign) {
        com.apple.navigator.routing.TurnSign.STRAIGHT ->
            if (name.isBlank()) "Continue straight" else "Continue straight onto $name"
        com.apple.navigator.routing.TurnSign.TURN_LEFT -> "Turn left onto $name"
        com.apple.navigator.routing.TurnSign.TURN_RIGHT -> "Turn right onto $name"
        com.apple.navigator.routing.TurnSign.TURN_SLIGHT_LEFT -> "Turn slight left onto $name"
        com.apple.navigator.routing.TurnSign.TURN_SLIGHT_RIGHT -> "Turn slight right onto $name"
        com.apple.navigator.routing.TurnSign.TURN_SHARP_LEFT -> "Turn sharp left onto $name"
        com.apple.navigator.routing.TurnSign.TURN_SHARP_RIGHT -> "Turn sharp right onto $name"
        com.apple.navigator.routing.TurnSign.KEEP_LEFT -> "Keep left onto $name"
        com.apple.navigator.routing.TurnSign.KEEP_RIGHT -> "Keep right onto $name"
        com.apple.navigator.routing.TurnSign.UTURN -> "Make a U-turn onto $name"
        com.apple.navigator.routing.TurnSign.ROUNDABOUT -> "Take the roundabout onto $name"
    }
}
