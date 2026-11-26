package com.example.voxmanifestorapp.ui.agent.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.voxmanifestorapp.ui.agent.ConversationEntry
import com.example.voxmanifestorapp.ui.agent.Speaker
import com.example.voxmanifestorapp.ui.theme.LightGreenCard

/**
 * Display components for agent UI - components that show information without user interaction.
 * These are pure display components for showing data to the user.
 */

@Composable
fun ConversationMessage(entry: ConversationEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalArrangement = when (entry.speaker) {
            Speaker.Agent -> Arrangement.Start
            else -> Arrangement.End
        }
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (entry.speaker) {
                    Speaker.Agent -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> LightGreenCard
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
                Text(
                    text = when (entry.speaker) {
                        Speaker.Agent -> "The Genie"  // Updated label from "Agent" to "The Genie"
                        else -> "You"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.align(
                        when (entry.speaker) {
                            Speaker.Agent -> Alignment.Start
                            else -> Alignment.End
                        }
                    )
                )
            }
        }
    }
}

/**
 * Mini chat/status window card for use in the monitoring bar
 */
@Composable
fun MinimizedCard(
    title: String,
    backgroundColor: Color,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(96.dp)
            .height(48.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onExpand,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Expand",
                    tint = if (backgroundColor == Color.Black) Color.White else Color.Black
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = if (backgroundColor == Color.Black) Color.White else Color.Black,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            )
        }
    }
}