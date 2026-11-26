package com.example.voxmanifestorapp

import ManifestationRepository
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.voxmanifestorapp.data.AppContainer
import com.example.voxmanifestorapp.data.AppDataContainer
import com.example.voxmanifestorapp.data.ManifestationDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.Manifest

class ManifestorApplication : Application() {

    lateinit var container: AppContainer

    private val _permissionState = MutableStateFlow(false)
    val permissionState: StateFlow<Boolean> = _permissionState

    fun updatePermissionState(granted: Boolean) {
        _permissionState.value = granted
    }

    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)

        // initialise permission state
        _permissionState.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onTerminate() {
        super.onTerminate()
    }
}
