package com.example.voxmanifestorapp.ui.agent.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.voxmanifestorapp.ui.agent.timer.TimerState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow

/**
 * A status bar component that displays the current mode and timer state.
 * Used in the Check-In phase to show the current mode and remaining time.
 *
 * @param currentMode The current mode of the conversation
 * @param timerState The current state of the timer
 * @param onAddTime Callback when the add time button is clicked
 * @param onSubtractTime Callback when the subtract time button is clicked
 * @param onStop Callback when the stop button is clicked
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun ModeStatusBar(
    currentMode: String,
    timerState: TimerState,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = null,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
        // Left section - Mode indicator
        Text(
            text = "CHECK-IN",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.weight(1f)
        )

        // Center section - Timer controls
        CheckInTimer(
            timerState = timerState,
            onAddTime = onAddTime,
            onSubtractTime = onSubtractTime,
            onStop = onStop,
            modifier = Modifier.weight(2f)
        )
        }
    }
}