package com.example.voxmanifestorapp.ui.agent.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.voxmanifestorapp.ui.agent.checkin.ConversationalTheme

/**
 * Retro High-Tech Terminal Design Theme Detail Popup
 * Displays conversational themes in a terminal-style overlay with Windows-style title bar
 * 
 * Features:
 * - Dark Mode Terminal Aesthetic: Very dark indigo/purple backgrounds
 * - Light blue/cyan or gold/yellow text for readability
 * - Sharp RectangleShape edges for classic tech look
 * - Windows-Style Title Bar with classic "CONVERSATIONAL THEMES" title and 'X' close button
 * - Theme Items Mirror Wish List Design: Same RectangleShape and layout pattern as MainScreen wish items
 * - Inverted colors: Dark purple background with gold/yellow text
 * - Large monospace numbers on the right side (1, 2, 3...)
 * - Bullet-pointed observations under each theme title
 * - Full Screen Experience: 90% width, 85% height for maximum information display
 * - Scrollable LazyColumn for handling extensive theme lists
 * - Horizontal divider lines between themes for clear separation
 */
@Composable
fun ThemeDetailPopup(
    themes: List<ConversationalTheme>,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            // Retro Terminal Color Scheme
            val terminalBackground = Color(0xFF1A1A2E) // Very dark indigo/purple
            val titleBarBackground = Color(0xFF0F3460) // Lighter contrasting indigo
            val terminalText = Color(0xFFFFD700) // Gold/yellow text
            val terminalAccent = Color(0xFF64FFDA) // Light blue/cyan accent
            val themeItemBackground = Color(0xFF4A2545) // Deep purple for theme items
            val dividerColor = Color(0xFF7209B7) // Light purple for dividers
            
            // Full screen overlay with semi-transparent background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                // Main popup container (90% width, 85% height)
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.85f)
                        .clickable(enabled = false) { } // Prevent clicks from dismissing
                ) {
                    // Windows-Style Title Bar
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = titleBarBackground),
                        shape = RectangleShape,
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Title
                            Text(
                                text = "CONVERSATIONAL THEMES",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = terminalText
                            )
                            
                            // Windows-style close button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = terminalText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    
                    // Main Terminal Body
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(containerColor = terminalBackground),
                        shape = RectangleShape,
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Theme count header
                            Text(
                                text = "${themes.size} themes identified",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = terminalAccent,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            // Scrollable theme list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(themes.size) { index ->
                                    val theme = themes[index]
                                    
                                    // Theme item card (mirroring ManifestationItem design)
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = themeItemBackground),
                                        shape = RectangleShape,
                                        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.3f)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            // Large background number (right side)
                                            Text(
                                                text = "${index + 1}",
                                                color = terminalText.copy(alpha = 0.3f),
                                                style = MaterialTheme.typography.displayLarge.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .padding(end = 8.dp)
                                            )
                                            
                                            // Theme content (left side)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(end = 48.dp) // Space for number
                                            ) {
                                                // Theme title
                                                Text(
                                                    text = theme.title,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = terminalText,
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                )
                                                
                                                // Observations as bullet points
                                                theme.observations.forEach { observation ->
                                                    Text(
                                                        text = "• $observation",
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontFamily = FontFamily.Monospace
                                                        ),
                                                        color = terminalAccent,
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Horizontal divider line between themes (except last)
                                    if (index < themes.size - 1) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(dividerColor.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
