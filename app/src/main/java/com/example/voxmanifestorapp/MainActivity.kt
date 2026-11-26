package com.example.voxmanifestorapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.example.voxmanifestorapp.ui.navigation.ManifestorApp
import com.example.voxmanifestorapp.ui.theme.VoxManifestorAppTheme

class MainActivity : ComponentActivity() {

    // create the lambda function that will pass through the UI and be called back to here when
    // we actually want to request permission through the UI
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // the state variable is set up inside the ManifestorApp class so that
        // 1. the app knows if we have permission to record audio
        // 2. we can inject the state into the voice view model from the factory
        (application as ManifestorApplication).updatePermissionState(isGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VoxManifestorAppTheme {

                /* not using prefs yet
                    - container is initialised in navhost.
                val container = (LocalContext.current.applicationContext as ManifestorApplication).container
                CompositionLocalProvider(LocalPreferences provides container.userPreferencesRepository) {
                */

                ManifestorApp(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
    }
}
