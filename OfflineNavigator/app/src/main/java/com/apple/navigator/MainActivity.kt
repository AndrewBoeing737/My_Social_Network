package com.apple.navigator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.apple.navigator.ui.AppleTheme
import com.apple.navigator.ui.NavigatorApp

class MainActivity : ComponentActivity() {

    private val model: NavigatorModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppleTheme {
                NavigatorAppWithPermissions(model)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NavigatorAppWithPermissions(model: NavigatorModel) {
    var granted by remember { mutableStateOf(hasLocationPermission()) }
    var requested by remember { mutableStateOf(granted) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        granted = resultMap.values.any { it } || hasLocationPermission()
        requested = true
    }

    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    NavigatorApp(model = model, permissionRequested = requested)
}

private fun hasLocationPermission(): Boolean {
    val context = this
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
}
