package com.example.voxmanifestorapp.ui.agent.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.voxmanifestorapp.R
import com.example.voxmanifestorapp.data.RecognitionState
import com.example.voxmanifestorapp.ui.agent.DialogueState
import com.example.voxmanifestorapp.ui.basevox.SpeechIndicatorLights

@Composable
fun AgentToolbar(
    recognitionState: RecognitionState,
    dialogueState: DialogueState,
    onMicClick: () -> Unit,
    onInterrupt: () -> Unit,
    onToggleConversation: () -> Unit,
    onSendResponse: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only enable send button for brain response conversations (check-in, core loop)
    // Command mode conversations auto-process voice input and don't need manual submission
    val canSendResponse = dialogueState is DialogueState.ExpectingInput &&
            dialogueState.inputType == com.example.voxmanifestorapp.ui.agent.VoxInputType.BRAIN_RESPONSE

    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = modifier
            .drawBehind {
                // Custom top border
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            },
//        border = BorderStroke(2.dp, Color(0xFF2C3E50)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic button
                MicButton(
                    recognitionState = recognitionState,
                    onMicClick = onMicClick
                )

                // Interrupt brain speech button
                InterruptButton(
                    onInterrupt = onInterrupt,
                    dialogueState = dialogueState
                )

                // Toggle conversation button
                ToggleConversationButton(
                    onToggleConversation = onToggleConversation,
                    dialogueState = dialogueState
                )

                SendResponseButton(
                    onSendResponse = onSendResponse,
                    canSendResponse = canSendResponse
                )

            }

            // Right side - Speech indicators
            SpeechIndicatorLights(dialogueState)
        }
    }
}

@Composable
fun MicButton(
    recognitionState: RecognitionState,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onMicClick,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            painter = painterResource(
                id = when (recognitionState) {
                    RecognitionState.Listen_Active -> R.drawable.mic_on
                    else -> R.drawable.mic_off
                }
            ),
            contentDescription = if (recognitionState == RecognitionState.Listen_Active)
                "Microphone On" else "Microphone Off",
            tint = if (recognitionState == RecognitionState.Listen_Active)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun InterruptButton(
    onInterrupt: () -> Unit,
    dialogueState: DialogueState,
    modifier: Modifier = Modifier
) {
    // Check if agent is speaking
    val isSpeaking = dialogueState is DialogueState.Speaking

    // The button is always visible, but we'll animate its appearance when speaking
    Box {
        // Base button (always visible but grayed out when not speaking)
        IconButton(
            onClick = { if (isSpeaking) onInterrupt() },
            modifier = modifier
                .size(48.dp)
                .alpha(if (isSpeaking) 0f else 0.5f) // Hidden when speaking, grayed when not
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Stop Speaking (Inactive)",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }

        // Animated button (only visible when speaking)
        AnimatedVisibility(
            visible = isSpeaking,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            IconButton(
                onClick = onInterrupt,
                modifier = modifier
                    .size(48.dp)
                    .background(Color(0x22FF0000), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Stop Speaking",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ToggleConversationButton(
    onToggleConversation: () -> Unit,
    dialogueState: DialogueState,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggleConversation,
        modifier = Modifier.size(50.dp)
    ) {
        Icon(
            painter = painterResource(
                id = if (dialogueState != DialogueState.Idle)
                    R.drawable.stop
                else
                    R.drawable.go
            ),
            contentDescription = if (dialogueState != DialogueState.Idle)
                "End Conversation"
            else
                "Start Conversation",
            tint = if (dialogueState != DialogueState.Idle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(50.dp)
        )
    }


}

@Composable
fun SendResponseButton(
    onSendResponse: () -> Unit,
    canSendResponse: Boolean
) {
    // Send response button - now with proper enabled state
    IconButton(
        onClick = onSendResponse,
        enabled = canSendResponse,
        modifier = Modifier.size(60.dp)
    ) {
        // Use background color to provide stronger visual cue for enabled state
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (canSendResponse) Color(0x1A4CAF50) else Color.Transparent)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.speech),
                contentDescription = "Send Response",
                tint = if (canSendResponse) Color.Green else Color.Gray,
                modifier = Modifier.size(60.dp)
            )
        }
    }

}
