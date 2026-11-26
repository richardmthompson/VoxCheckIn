package com.example.voxmanifestorapp.ui.agent.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.voxmanifestorapp.R
import com.example.voxmanifestorapp.ui.agent.timer.TimerState
import java.util.concurrent.TimeUnit

/**
 * A composable that displays a timer with control buttons.
 * Used in the Check-In phase to show remaining time and allow user control.
 *
 * @param timerState The current state of the timer
 * @param onAddTime Callback when the add time button is clicked
 * @param onSubtractTime Callback when the subtract time button is clicked
 * @param onStop Callback when the stop button is clicked
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun CheckInTimer(
    timerState: TimerState,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp, horizontal = 12.dp),  // More padding for less dominance
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Timer controls group
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Subtract time button
            IconButton(
                onClick = onSubtractTime,
                enabled = timerState is TimerState.Active,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.remove),
                    contentDescription = "Subtract Time",
                    tint = if (timerState is TimerState.Active)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Timer display in rounded box
            Surface(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
            ) {
                Text(
                    text = when (timerState) {
                        is TimerState.Active -> TimerState.formatTime(timerState.remainingTimeMillis)
                        TimerState.Expired -> "00:00"
                        TimerState.Inactive -> "--:--"
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = when (timerState) {
                            is TimerState.Active -> MaterialTheme.colorScheme.tertiary
                            TimerState.Expired -> MaterialTheme.colorScheme.tertiary
                            TimerState.Inactive -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                        }
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }

            // Add time button
            IconButton(
                onClick = onAddTime,
                enabled = timerState is TimerState.Active,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Time",
                    tint = if (timerState is TimerState.Active)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Stop button
        IconButton(
            onClick = onStop,
            enabled = timerState is TimerState.Active,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.cancel),
                contentDescription = "Stop Timer",
                tint = if (timerState is TimerState.Active)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}