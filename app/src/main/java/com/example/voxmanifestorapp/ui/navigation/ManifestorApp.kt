package com.example.voxmanifestorapp.ui.navigation

import ManifestorNavHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.navigation.NavHostController
import com.example.voxmanifestorapp.ManifestorApplication
import androidx.navigation.compose.rememberNavController

@Composable
fun ManifestorApp(
    // passed in from the activity to manage requesting audio permission
    onRequestPermission: () -> Unit
    ) {
        ManifestorNavHost(
            onRequestPermission = onRequestPermission
        )
}
