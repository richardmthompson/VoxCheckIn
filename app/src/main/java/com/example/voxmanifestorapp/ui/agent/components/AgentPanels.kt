package com.example.voxmanifestorapp.ui.agent.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.voxmanifestorapp.data.SessionInfo
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.ConversationEntry

/**
 * Status panel component that slides in/out from the right side of the screen
 * with a vertical tab that allows toggling its visibility
 */
@Composable
fun StatusPanel(
    statusMessages: List<Pair<String, StatusColor>>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 400.dp, // Fixed height of 400dp
    tabPosition: Float = 0.5f // Position tab along the panel height (0.0f-1.0f)
) {
    val panelWidth = 400.dp  // Panel width
    val tabWidth = 25.dp  // Increased from 20.dp to 30.dp (50% increase)
    val tabHeight = 80.dp
    val cornerRadius = 16.dp
    val borderWidth = 0.dp  // Remove border
    val panelShape = RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius)
    val panelColor = Color.Black.copy(alpha = 0.9f)
    val borderColor = Color(0xFF3F51B5)

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .width(panelWidth)
            .zIndex(10f)
    ) {
        val boxHeight = this.maxHeight

        // The main slidable panel content
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInHorizontally(
                initialOffsetX = { it }, // Slide in from right
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it }, // Slide out to right
                animationSpec = tween(
                    500,
                    easing = FastOutSlowInEasing
                )
            ),
            modifier = Modifier.matchParentSize()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize(),
                shape = panelShape,
                colors = CardDefaults.cardColors(containerColor = panelColor)
            ) {
                // Scroll the status messages when status messages change size.
                val scrollState = rememberScrollState()
                LaunchedEffect(statusMessages.size) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        "System Status",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .align(alignment = Alignment.CenterHorizontally)
                    )
                    statusMessages.forEach { (text, color) ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = color.color,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Calculate vertical position based on the tabPosition parameter
        val yOffset = (boxHeight * tabPosition) - (tabHeight / 2)

        // Tab to toggle expansion - with custom positioning
        Card(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 0.dp, y = yOffset)
                .width(tabWidth)
                .height(tabHeight)
                .clickable { onToggleExpanded() },
            shape = RoundedCornerShape(
                topStart = cornerRadius,
                bottomStart = cornerRadius
            ), // Flat side on right
            colors = CardDefaults.cardColors(containerColor = panelColor)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = if (isExpanded)
                        Icons.Default.KeyboardArrowRight
                    else
                        Icons.Default.KeyboardArrowLeft,
                    contentDescription = if (isExpanded) "Collapse Status" else "Expand Status",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Conversation history panel component that slides in/out from the left side of the screen
 * with a vertical tab that allows toggling its visibility.
 * Displays the conversation between the agent and user.
 *
 * The enhanced version shows expandable session cards for historical conversations.
 */
@Composable
fun ConversationHistoryPanel(
    conversationHistory: List<ConversationEntry>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    panelWidth: Dp = 400.dp,
    tabPosition: Float = 0.5f, // Position tab 50% down from the top (center) by default
    contentPadding: PaddingValues = PaddingValues(16.dp),
    title: String = "// CONVERSATION HISTORY",
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
    heightFraction: Float = 0.4f, // Take up 50% of screen height by default
    sessionInfoList: List<SessionInfo> = emptyList(),
    onSessionSelected: (String) -> Unit = {},
    onResumeSession: (String) -> Unit = {},
    onResetSession: () -> Unit = {}
) {
    // Define panel properties
    val tabWidth = 25.dp // Increased from 20.dp to 30.dp (50% increase)
    val tabHeight = 80.dp
    val cornerRadius = 16.dp
    val panelShape = RoundedCornerShape(topEnd = cornerRadius, bottomEnd = cornerRadius)
    val panelColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.secondary

    // Calculate screen height using LocalConfiguration
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val panelHeight = screenHeight * heightFraction

    BoxWithConstraints(
        modifier = modifier
            .height(panelHeight)
            .width(panelWidth)
            .zIndex(10f)
    ) {
        val boxHeight = this.maxHeight

        // The main slidable panel content
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier.matchParentSize()
        ) {
            Card(
                modifier = Modifier
//                    .border(width = 2.dp, color = borderColor, shape = panelShape)
                    .fillMaxSize()
                    // No left border - just don't draw it!
                    .drawBehind {
                        val strokeWidth = 3.dp.toPx()
                        val cornerRadiusPx = cornerRadius.toPx()

                        // Top border (from left edge to where curve starts)
                        drawLine(
                            color = borderColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width - cornerRadiusPx, 0f),
                            strokeWidth = strokeWidth
                        )

                        // Top-right corner arc
                        drawArc(
                            color = borderColor,
                            startAngle = -90f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(size.width - cornerRadiusPx * 2, 0f),
                            size = Size(cornerRadiusPx * 2, cornerRadiusPx * 2),
                            style = Stroke(width = strokeWidth)
                        )

                        // Right border (between the two curves)
                        drawLine(
                            color = borderColor,
                            start = Offset(size.width, cornerRadiusPx),
                            end = Offset(size.width, size.height - cornerRadiusPx),
                            strokeWidth = strokeWidth
                        )

                        // Bottom-right corner arc
                        drawArc(
                            color = borderColor,
                            startAngle = 0f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(
                                size.width - cornerRadiusPx * 2,
                                size.height - cornerRadiusPx * 2
                            ),
                            size = Size(cornerRadiusPx * 2, cornerRadiusPx * 2),
                            style = Stroke(width = strokeWidth)
                        )

                        // Bottom border (from right curve to left edge)
                        drawLine(
                            color = borderColor,
                            start = Offset(size.width - cornerRadiusPx, size.height),
                            end = Offset(0f, size.height),
                            strokeWidth = strokeWidth
                        )

                        // No left border!
                    },

                shape = panelShape,
                colors = CardDefaults.cardColors(containerColor = panelColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    Text(
                        title,
                        style = titleStyle,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .align(alignment = Alignment.CenterHorizontally)
                    )

                    // button to re-set the current session!
                    Row {
                        Button(
                            onClick = { onResetSession() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha=0.7f)
                            )
                        ) {
                            Text("Start New Session",
                                color = MaterialTheme.colorScheme.onSecondary)
                        }
                    }

                    if (sessionInfoList.isEmpty()) {
                        // Default view - show current conversation only
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            reverseLayout = true
                        ) {
                            items(conversationHistory.filter { it.isVisible }.reversed()) { entry ->
                                ConversationMessage(entry)
                            }
                        }
                        // if we have a list of sessions to manage...
                    } else {

                        // Enhanced view with session cards
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // Add current session at the top if there are messages
                            if (conversationHistory.isNotEmpty()) {
                                item {
                                    Text(
                                        "Current Session",
                                        style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                items(conversationHistory.filter { it.isVisible }
                                    ) { entry ->
                                    ConversationMessage(entry)
                                }

                            }
                            // Collapsible previous sessions header
                            items(sessionInfoList.filter { !it.isCurrentSession }) { session ->
                                SessionCard(
                                    sessionInfo = session,
                                    onSessionSelected = onSessionSelected,
                                    onResumeSession = onResumeSession
                                )
                            }
                        }
                    }
                }
            }
        }

        // Calculate vertical position based on the tabPosition parameter
        // This places the tab at the specified position along the panel height
        val yOffset = (boxHeight * tabPosition) - (tabHeight / 2)

        // Tab to toggle expansion with custom positioning based on constraints
        Card(
            modifier = Modifier
                .offset(x = 0.dp, y = yOffset)
                .width(tabWidth)
                .height(tabHeight)
                .clickable { onToggleExpanded() }
                .graphicsLayer {
                    clip = false
                },
            shape = RoundedCornerShape(
                topEnd = cornerRadius,
                bottomEnd = cornerRadius
            ), // Flat side on left
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val icon = if (isExpanded)
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                else
                    Icons.AutoMirrored.Filled.KeyboardArrowRight

                Icon(
                    imageVector = icon,
                    contentDescription = if (isExpanded) "Hide Conversation" else "Show Conversation",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(44.dp)
                )
            }
        }
    }
}

/**
 * Displays a single session card with metadata and expandable conversation content.
 */
@Composable
fun SessionCard(
    sessionInfo: SessionInfo,
    onSessionSelected: (String) -> Unit,
    onResumeSession: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Session header (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Show session name if available, otherwise show date/time
                    Text(
                        text = sessionInfo.sessionName ?: sessionInfo.dateTimeFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Show date/time as subtitle if session name is available
                    if (sessionInfo.sessionName != null) {
                        Text(
                            text = sessionInfo.dateTimeFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${sessionInfo.entryCount} messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.White
                )
            }

            // Expandable content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { onResumeSession(sessionInfo.sessionId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha=0.7f)
                            )
                        ) {
                            Text("Resume",
                                color = MaterialTheme.colorScheme.onTertiary)
                        }

                        Button(
                            onClick = { onSessionSelected(sessionInfo.sessionId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha=0.7f)
                            )
                        ) {
                            Text("View Conversation",
                                color = MaterialTheme.colorScheme.onSecondary)
                        }
                    }
                }
            }
        }
    }
}