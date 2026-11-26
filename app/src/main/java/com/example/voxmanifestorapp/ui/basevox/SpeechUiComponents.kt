package com.example.voxmanifestorapp.ui.basevox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.voxmanifestorapp.R
import com.example.voxmanifestorapp.ui.agent.DialogueState

@Composable
fun SpeechIndicatorLights(dialogueState: DialogueState?) {
    Row(
        modifier = Modifier.height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Agent speaking indicator (mood icon)
        Icon(
            painter = painterResource(id = R.drawable.mood),
            contentDescription = "Agent Speaking",
            tint = if (dialogueState is DialogueState.Speaking)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp)
        )

        // User can speak indicator (person icon)
        Icon(
            painter = painterResource(id = R.drawable.person),
            contentDescription = "User Turn",
            tint = if (dialogueState is DialogueState.ExpectingInput)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp)
        )
    }
}