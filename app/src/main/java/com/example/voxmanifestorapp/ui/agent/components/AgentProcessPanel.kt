package com.example.voxmanifestorapp.ui.agent.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.voxmanifestorapp.R
import com.example.voxmanifestorapp.data.ConceptActionState
import com.example.voxmanifestorapp.data.RecognitionState
import com.example.voxmanifestorapp.ui.agent.ConversationEntry
import com.example.voxmanifestorapp.ui.agent.ConversationPlan
import com.example.voxmanifestorapp.ui.agent.CoreLoopState
import com.example.voxmanifestorapp.ui.agent.DialogueState
import com.example.voxmanifestorapp.ui.agent.checkin.CheckInState
import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background

/**
 * Helper class for holding four related values.
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun AgentProcessPanel(
    recognitionState: RecognitionState,
    dialogueState: DialogueState,
    conversationHistory: List<ConversationEntry>,
    currentPlan: ConversationPlan?,
    currentAction: ConceptActionState,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    tabPosition: Float = 0.1f, // Position of tab as percentage from top (0.0f - 1.0f)
    coreLoopState: CoreLoopState? = null, // Add coreLoopState parameter
    checkInState: CheckInState? = null, // Add checkInState parameter for themes
    onThemePlaybackClick: () -> Unit = {} // Callback for theme summary playback
) {
    // Define panel properties
    val panelWidth = 250.dp
    val tabWidth = 28.dp  // Half of the original width (40dp)
    val cornerRadius = 16.dp
    val borderWidth = 2.dp
    val panelBackgroundColor = MaterialTheme.colorScheme.primaryContainer
    val borderColor = MaterialTheme.colorScheme.outline
    val panelShape = RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius)
    val panelElevation = 8.dp

    // State for theme detail popup
    var isThemePopupVisible by remember { mutableStateOf(false) }

    // Theme summary playback button - compact design with label
    val hasThemes = checkInState?.activeThemes?.isNotEmpty() == true
    val isPlaying = dialogueState is DialogueState.Speaking
    val buttonEnabled = hasThemes && !isPlaying

    // Use Box to layer the panel and the tab control correctly
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(panelWidth)
            .zIndex(10f)
    ) {
        // The main slidable panel content
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.matchParentSize()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .border(BorderStroke(borderWidth, borderColor), panelShape),
                shape = panelShape,
                colors = CardDefaults.cardColors(containerColor = panelBackgroundColor),
                elevation = CardDefaults.cardElevation(defaultElevation = panelElevation)
            ) {

                // spacer above top of panel header
                Spacer(modifier = Modifier.height(8.dp))

                HeaderBar()

                // Inside the panel content (below header)
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 8.dp, bottom = 48.dp, end = 8.dp)
                        .fillMaxSize()
                ) {

                    // Add prominent Agent State Card at the top (now includes the avatar)
                    AgentStateCard(
                        dialogueState = dialogueState,
                        recognitionState = recognitionState
                    )

                    SpeakThemeSummary(
                        buttonEnabled = buttonEnabled,
                        onThemePlaybackClick = onThemePlaybackClick
                    )

                    // Compact spacing after button
                    Spacer(modifier = Modifier.height(4.dp))

                    // Display conversational themes card when checkInState exists
                    ConversationalThemesCard(
                        checkInState = checkInState, modifier =
                            Modifier
                                .weight(1f) // Fill all remaining space between button and state info
                                .clickable {
                                    Log.d("AgentProcessPanel", "Theme card clicked - opening popup")
                                    isThemePopupVisible = true
                                }
                    )

                    // Simplified Debug State Variables Card (positioned at bottom)
                    DebugStateVariablesCard(
                        recognitionState = recognitionState,
                        conversationHistory = conversationHistory,
                        coreLoopState = coreLoopState
                    )

                }
            }
        }

        // The Tab - sits visually on the right edge of the panel or screen with custom position
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = borderWidth / 2,
                    // vertical position of tab
                    y = (LocalConfiguration.current.screenHeightDp * tabPosition).dp - 120.dp
                )
                .width(tabWidth)
                .height(80.dp)  // Slightly reduced height
                .border(BorderStroke(borderWidth, borderColor), panelShape)
                .clickable { onToggleExpanded() }
                .zIndex(11f),
            shape = panelShape,
            colors = CardDefaults.cardColors(containerColor = panelBackgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = panelElevation)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = if (isExpanded) "Collapse Menu" else "Expand Menu",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

// Theme Detail Popup
    checkInState?.activeThemes?.let { themes ->
        if (themes.isNotEmpty()) {
            ThemeDetailPopup(
                themes = themes,
                isVisible = isThemePopupVisible,
                onDismiss = {
                    Log.d("AgentProcessPanel", "Theme popup dismissed")
                    isThemePopupVisible = false
                }
            )
        }
    }
}

@Composable
fun DebugStateVariablesCard(
    recognitionState: RecognitionState,
    conversationHistory: List<ConversationEntry>,
    coreLoopState: CoreLoopState?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "State Info",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Recognition status
            val recognitionStateText = when (recognitionState) {
                RecognitionState.Listen_Active -> "Active (Mic On)"
                RecognitionState.Inactive -> "Disabled (Mic Off)"
                else -> recognitionState.toString()
            }
            Text(
                text = "Recognition: $recognitionStateText",
                style = MaterialTheme.typography.bodySmall,
                color = if (recognitionState == RecognitionState.Listen_Active)
                    Color.Green else Color.Gray
            )

            // History count
            Text(
                text = "Conversation Entries: ${conversationHistory.count { it.isVisible }}",
                style = MaterialTheme.typography.bodySmall
            )

            // Add Current Phase if coreLoopState is available
            if (coreLoopState != null) {
                Text(
                    text = "Current Phase: ${coreLoopState.currentPhase.name}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5C6BC0) // Indigo color for emphasis
                )

                // Add current wish if one is selected
                if (coreLoopState.currentWishIndex >= 0) {
                    Text(
                        text = "Current Wish: ${coreLoopState.currentWishIndex + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2196F3) // Blue accent color
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationalThemesCard(
    checkInState: CheckInState?,
    modifier: Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight() // Ensures content uses full card height
                .padding(12.dp)
        ) {
            Text(
                text = "CONVERSATIONAL THEMES",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (checkInState?.activeThemes?.isNotEmpty() == true) {
                val themes = checkInState.activeThemes
                Log.d("AgentProcessPanel", "=== DISPLAYING THEMES ===")
                Log.d("AgentProcessPanel", "Found ${themes.size} themes to display")
                themes.forEachIndexed { index, theme ->
                    Log.d(
                        "AgentProcessPanel",
                        "Theme $index: '${theme.title}' with ${theme.observations.size} observations"
                    )
                }

                Text(
                    text = "${themes.size} themes identified",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Theme list that expands to fill available space
                LazyColumn(
                    modifier = Modifier.weight(1f) // Makes themes list expand to fill available space
                ) {
                    items(themes) { theme ->
                        Text(
                            text = "• ${theme.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            } else {
                Log.d("AgentProcessPanel", "=== NO THEMES TO DISPLAY ===")
                Log.d("AgentProcessPanel", "checkInState is null: ${checkInState == null}")
                if (checkInState != null) {
                    Log.d(
                        "AgentProcessPanel",
                        "activeThemes is empty: ${checkInState.activeThemes.isEmpty()}"
                    )
                    Log.d(
                        "AgentProcessPanel",
                        "activeThemes count: ${checkInState.activeThemes.size}"
                    )
                }

                Text(
                    text = "No themes identified yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

}

@Composable
fun SpeakThemeSummary(
    buttonEnabled: Boolean,
    onThemePlaybackClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rounded button with play icon
        IconButton(
            onClick = onThemePlaybackClick,
            enabled = buttonEnabled,
            modifier = Modifier.size(28.dp),

            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary,
                disabledContainerColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play theme summary",
                modifier = Modifier.size(28.dp),
            )
        }

        // Two-line label to the right
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Speak Theme",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (buttonEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Summary",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (buttonEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

}

@Composable
fun HeaderBar() {
    // Header bar matching ConceptScreen styling - flush with panel edges
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Text(
            text = "// GENIE'S PROCESS //",
            style = MaterialTheme.typography.titleMedium, // Increased from titleSmall to titleMedium
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }

}

/**
 * Genie Avatar component that displays different states based on recognition and dialogue state
 */
@Composable
fun GenieAvatar(
    recognitionState: RecognitionState,
    dialogueState: DialogueState,
    size: Dp = 100.dp,
    modifier: Modifier = Modifier
) {
    val avatarResource = when {
        // First check if mic is off - she's sleeping in this case
        recognitionState != RecognitionState.Listen_Active -> R.drawable.g_sleeping

        // When mic is on, show different states based on dialogue
        dialogueState is DialogueState.Speaking -> R.drawable.g_speaking
        dialogueState is DialogueState.Thinking -> R.drawable.g_thinking
        dialogueState is DialogueState.ExpectingInput -> R.drawable.g_listening
        dialogueState is DialogueState.Idle -> R.drawable.g_listening // When idle but mic is on, she's still listening

        // Fallback (although we should never reach this with the above conditions)
        else -> R.drawable.g_confused
    }

    Image(
        painter = painterResource(id = avatarResource),
        contentDescription = "Genie Avatar",
        modifier = modifier.size(size)
    )
}

/**
 * A prominently displayed card showing the agent's current state in an easily understandable format.
 */
@Composable
private fun AgentStateCard(
    dialogueState: DialogueState,
    recognitionState: RecognitionState
) {
    val (stateLabel, stateColor, stateDescription, _) = when (dialogueState) {
        is DialogueState.Speaking -> Quadruple(
            "Speaking",
            Color(0xFF2196F3), // Blue
            "Agent is talking",
            painterResource(id = R.drawable.g_speaking)
        )

        is DialogueState.ExpectingInput -> Quadruple(
            "Listening",
            MaterialTheme.colorScheme.tertiary,
            "Waiting for your response",
            painterResource(id = R.drawable.g_listening)
        )

        is DialogueState.Thinking -> Quadruple(
            "Thinking",
            MaterialTheme.colorScheme.primary,
            "Processing your request",
            painterResource(id = R.drawable.g_thinking)
        )

        else -> Quadruple(
            "Idle",
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Not active",
            painterResource(id = R.drawable.g_sleeping)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Genie avatar on the left side (replacing the previous state icon)
            GenieAvatar(
                recognitionState = recognitionState,
                dialogueState = dialogueState,
                size = 60.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // State information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stateDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}