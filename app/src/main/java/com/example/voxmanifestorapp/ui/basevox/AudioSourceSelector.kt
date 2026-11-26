package com.example.voxmanifestorapp.ui.basevox

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.voxmanifestorapp.R

@Composable
fun AudioSourceSelector(voiceModel: VoiceManagedViewModel) {
    var isBluetoothSelected by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        Icon(
            painter = painterResource(
                if (isBluetoothSelected) R.drawable.headset_mic else R.drawable.mic
            ),
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = isBluetoothSelected,
            onCheckedChange = { checked ->
                isBluetoothSelected = checked
                voiceModel.switchAudioSource(checked)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

fun getAvailableAudioSources(context: Context): List<Pair<String, Int>> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
    val sources = mutableListOf<Pair<String, Int>>()

    sources.add("Phone" to AudioDeviceInfo.TYPE_BUILTIN_MIC)
    devices.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        .forEach { sources.add(it.productName.toString() to it.type) }

    Log.d("AUDIO", "Gathered available audio devices.")
    sources.forEach { source -> Log.d("AUDIO", "${source.first} : ${source.second}") }

    return sources
}
