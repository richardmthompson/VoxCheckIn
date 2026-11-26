package com.example.voxmanifestorapp.ui.main

/**
 * MainScreen.kt - Primary UI screen for VoxManifestor application
 *
 * This file contains the main composable screen and supporting UI components for the
 * VoxManifestor app, which helps users manifest their goals through voice-driven check-ins
 * with an AI agent.
 *
 * ## Key Components:
 *
 * ### MainScreen
 * - Root composable orchestrating all UI elements
 * - Complex Box-based layout with multiple z-indexed layers
 * - Manages state from ViewModels using StateFlow + collectAsState()
 * - Contains: agent terminal, user voice entry terminal, toolbars, side panels
 *
 * ## Architecture Notes:
 *
 * **State Management:**
 * - Uses MVVM pattern with StateFlow for reactive updates
 * - State hoisted from MainViewModel and VoiceManagedViewModel
 * - Local UI state (panel expansion) uses remember { mutableStateOf() }
 *
 * **Layout Strategy:**
 * - Box-based absolute positioning instead of Scaffold
 * - Bottom elements (toolbar, status bar) stacked with padding
 * - Side panels (agent, status, history) anchored with alignment
 *
 * @see MainViewModel For manifestation and dialogue state management
 * @see VoiceManagedViewModel For voice recognition state management
 * @see com.example.voxmanifestorapp.ui.agent.components For panel composables
 */

// TODO: Re-enable affirmation functionality post-MVP
// import com.example.voxmanifestorapp.ui.agent.affirmations.AffirmationOverlay

import android.view.RoundedCorner
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.voxmanifestorapp.R
import com.example.voxmanifestorapp.data.MAX_WISH_SLOTS
import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.data.RecognitionState
import com.example.voxmanifestorapp.ui.agent.DialogueState
import com.example.voxmanifestorapp.ui.agent.components.AgentProcessPanel
import com.example.voxmanifestorapp.ui.agent.components.AgentToolbar
import com.example.voxmanifestorapp.ui.agent.components.ConversationHistoryPanel
import com.example.voxmanifestorapp.ui.agent.components.GenieAvatar
import com.example.voxmanifestorapp.ui.agent.components.ModeStatusBar
import com.example.voxmanifestorapp.ui.agent.components.StatusPanel
import com.example.voxmanifestorapp.ui.agent.timer.TimerIntent
import com.example.voxmanifestorapp.ui.basevox.VoiceManagedViewModel
import com.example.voxmanifestorapp.ui.main.MainViewModel
import com.example.voxmanifestorapp.ui.statuslog.StatusMessageService
import com.example.voxmanifestorapp.ui.agent.components.AgentTerminal
import com.example.voxmanifestorapp.ui.agent.components.UserTerminal

/**
 * Main screen composable for VoxManifestor application.
 *
 * @param onRequestPermission Callback to request microphone permission from Activity
 * @param viewModel Main ViewModel managing manifestations, agent state, and dialogue
 * @param voiceModel ViewModel managing voice recognition and TTS
 * @param statusMessageService Service providing status message logging
 * @param onDetailsOpen Callback when user wants to view manifestation details (currently unused)
 */
@Composable
fun MainScreen(
    onRequestPermission: () -> Unit,
    viewModel: MainViewModel,
    voiceModel: VoiceManagedViewModel,
    statusMessageService: StatusMessageService,
    //onDetailsOpen: (Int) -> Unit
) {
    // ========== STATE COLLECTION ==========
    // The Composable needs to observe the values emitted by StateFlow streams
    // and react by redrawing. This requires the specific mechanism provided by collectAsState()
    // to bridge the Flow world with the Compose State world.

    // --- Manifestation State ---
    val manifestations by viewModel.allManifestations.collectAsState(initial = emptyList())
    val selectedSlot by viewModel.selectedSlot.collectAsState() // Currently selected wish slot

    // --- Voice Recognition State ---
    val recognitionState by voiceModel.recognitionState.collectAsState()
    val rawTextState by voiceModel.rawTextState.collectAsState() // Raw speech-to-text output

    // --- Agent Dialogue State ---
    val commandState by viewModel.commandState.collectAsState() // Current voice command being processed
    val dialogueState by viewModel.dialogueState.collectAsState() // Agent's speaking/listening state
    val conversationType by viewModel.conversationType.collectAsState() // Type of conversation (check-in, etc.)
    val latestAgentUtterance by viewModel.latestAgentUtterance.collectAsState() // Latest agent response
    val latestUserUtterance by viewModel.latestUserUtterance.collectAsState() // Latest agent response

    // --- Agent Terminal Inputs ---
    val agentTerminalContent =
        viewModel.agentTerminalContent.collectAsState()    // agent terminal content

    // --- User Terminal Inputs ---
    val userTerminalContent =
        viewModel.userTerminalContent.collectAsState()    // agent terminal content

    // TODO: Re-enable affirmation functionality post-MVP
    // Agent overlays for affirmations - currently disabled
    val agentDisplayState by viewModel.agentDisplayState.collectAsState()

    // --- Agent Process Panel State ---
    val conversationHistory by viewModel.conversationHistory.collectAsState() // Full conversation log
    val currentPlan by viewModel.currentPlan.collectAsState() // Agent's current plan/goal
    val currentAction by viewModel.currentAction.collectAsState() // Agent's current action

    // --- Core Loop & Check-In State ---
    val coreLoopState by viewModel.coreLoopState.collectAsState() // 7-phase Core Loop state machine
    val checkInState by viewModel.checkInState.collectAsState() // Check-in timer and dialogue state

    // --- Session History State ---
    val sessionInfoList by viewModel.sessionInfoList.collectAsState() // List of all sessions
    val sessionEntries by viewModel.sessionEntries.collectAsState() // Entries for a specific session
    val viewingSessionId by viewModel.viewingSessionId.collectAsState() // Currently viewed session (null = current)

    // --- Status Messages ---
    val statusMessages by statusMessageService.statusMessages.collectAsState()

    // --- Wish Slot Management ---
    // NOTE: This array is rebuilt on every recomposition - consider moving to a derived state
    // IMPROVEMENT: Could be computed as a Map for O(1) lookup instead of array iteration
    var wishSlot = Array<Manifestation?>(MAX_WISH_SLOTS) { null }

    // --- Local UI State (Panel Expansion) ---
    // These control the visibility/expansion of collapsible panels and don't need to persist
    var isAgentPanelExpanded by remember { mutableStateOf(false) }
    var isStatusPanelExpanded by remember { mutableStateOf(false) }
    var isConversationPanelExpanded by remember { mutableStateOf(false) }
    var isDialogueExpanded by remember { mutableStateOf(false) }

    // ========== COLOR SCHEME ==========
    // NOTE: These colors are duplicated across multiple composables
    // IMPROVEMENT: Extract to a centralized MaterialTheme or design system
    val backgroundBeige = Color(0xFFF2EAD3) // Warm beige background
    val borderBlue = Color(0xFF3F51B5) // Indigo border for agent panel

    // ========== LAYOUT DIMENSIONS ==========
    /** Mode Status Bar and Agent Toolbar Height Configs */
    val toolbarHeight = 48.dp
    val statusBarHeight = 48.dp
    val barSpacing = 0.dp // No space between toolbar and status bar
    val totalBottomHeight = toolbarHeight + statusBarHeight + barSpacing
    val recogZoneBottomPadding =
        totalBottomHeight + 16.dp // Position recog zone above toolbar with 16dp gap

    // ========== MAIN LAYOUT ==========
    // Root container using Box for absolute positioning with z-index layering
    // NOTE: Consider using Scaffold for more standard Material3 layout patterns
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding() // Accounts for system status bar (notch, etc.)
    ) {
        // This is a column of columns - containing the <terminals> and <status bars>
        Column() {

            AppTitle(
                modifier = Modifier.fillMaxWidth()
            )

            // ========== DUAL TERMINAL LAYOUT ==========
            // Column-based layout with two equal-height terminal windows
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 0.dp) // Minimal side padding
                        .padding(bottom = 0.dp) // Account for bottom toolbars
                        .weight(1f),
                //verticalArrangement = Arrangement.spacedBy(4.dp) // Small spacing between windows
            ) {
                // Top Terminal Window - Agent Dialogue
                AgentTerminal(
                    agentTerminalContent = agentTerminalContent.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // 50% of available height
                )

                // Bottom Terminal Window - User Speech Input
                UserTerminal(
                    latestUserUtterance = latestUserUtterance,
                    userTerminalContent = userTerminalContent.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // 50% of available height
                )
            } // End Dual Terminal Layout

            // ========== BOTTOM UI ELEMENTS ==========
            // Column stacking Agent Toolbar above Mode Status Bar at bottom of screen
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
            ) {
                // --- Agent Toolbar (Top) ---
                // Contains primary controls: mic toggle, interrupt, conversation toggle, send response
                AgentToolbar(
                    recognitionState = recognitionState,
                    dialogueState = dialogueState,
                    onMicClick = { voiceModel.handleMicrophoneClick(onRequestPermission) },
                    onInterrupt = { viewModel.requestInterrupt() },
                    onToggleConversation = { viewModel.toggleConversation() },
                    onSendResponse = { viewModel.sendResponse() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(toolbarHeight)
                )

                // --- Mode Status Bar (Bottom) ---
                // Displays current mode (Check-In) with timer controls (+/- time, stop)
                ModeStatusBar(
                    currentMode = "Check-In", // NOTE: Currently hardcoded, should be dynamic
                    timerState = checkInState.timerState,
                    onAddTime = { viewModel.handleTimerIntent(TimerIntent.AddTime) },
                    onSubtractTime = { viewModel.handleTimerIntent(TimerIntent.SubtractTime) },
                    onStop = { viewModel.handleTimerIntent(TimerIntent.Stop) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight)
                )
            } // End Bottom UI Elements Column

        } // end column of columns (visible UI)

        // ========== COLLAPSIBLE SIDE PANELS ==========
        // Three side panels that can be expanded/collapsed independently
        // All use zIndex(10f) to float above main content

        // --- Status Panel (Right Side) ---
        // Shows status message log with newest messages at top
        // Tab positioned at 27% from top for ergonomic access
        StatusPanel(
            statusMessages = statusMessages,
            isExpanded = isStatusPanelExpanded,
            onToggleExpanded = { isStatusPanelExpanded = !isStatusPanelExpanded },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd) // Anchored to bottom-right
                    .navigationBarsPadding()
                    .padding(bottom = 64.dp) // Space above toolbar
                    .zIndex(10f), // Float above main content
            height = 350.dp,
            tabPosition = 0.27f // Position tab closer to the top (27% down from top)
        )

        // --- Conversation History Panel (Left Side) ---
        // Shows current conversation history or archived session entries
        // Can switch between current and historical sessions

        // Reload button - Only visible when panel is expanded
        // NOTE: Using conditional composition for FAB is acceptable, but consider using AnimatedVisibility
        if (isConversationPanelExpanded) {
            FloatingActionButton(
                onClick = { viewModel.loadAllSessionInfo() }, // Refresh session list from database
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 128.dp)
                        .zIndex(20f), // Higher z-index than panel to stay on top
                containerColor = Color(0xFF2196F3) // Material blue
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Reload Sessions",
                    tint = Color.White
                )
            }
        }

        ConversationHistoryPanel(
            conversationHistory =
                // Toggle between current session and historical session view
                if (viewingSessionId == null) conversationHistory else sessionEntries,
            isExpanded = isConversationPanelExpanded,
            onToggleExpanded = { isConversationPanelExpanded = !isConversationPanelExpanded },
            modifier =
                Modifier
                    .align(Alignment.BottomStart) // Anchored to bottom-left
                    .navigationBarsPadding()
                    .padding(bottom = 64.dp) // Space above toolbar
                    .zIndex(10f), // Float above main content
            panelWidth = 400.dp, // Fixed width for consistency
            contentPadding = PaddingValues(16.dp),
            heightFraction = 0.8f, // Takes up 80% of screen height when expanded
            tabPosition = 0.715f, // Position tab at center of panel (71.5% from top)
            sessionInfoList = sessionInfoList, // List of all available sessions
            onSessionSelected = { sessionId ->
                // Toggle between viewing session and returning to current
                if (viewingSessionId == sessionId) {
                    viewModel.returnToCurrentSession()
                } else {
                    viewModel.loadSessionEntries(sessionId)
                }
            },
            onResumeSession = { sessionId -> viewModel.resumeSession(sessionId) }, // Load session as current
            onResetSession = { viewModel.resetSession() },
            title = if (viewingSessionId == null) "Conversation History" else "Session History"
        )

        // --- Agent Process Panel (Top-Right) ---
        // Shows agent's internal state: current plan, action, conversation flow, and core loop phase
        // Most complex panel with multiple tabs for different aspects of agent processing
        AgentProcessPanel(
            recognitionState = recognitionState, // For avatar animation
            dialogueState = dialogueState, // For avatar animation
            conversationHistory = conversationHistory, // Full conversation log
            currentPlan = currentPlan, // Agent's current high-level plan
            currentAction = currentAction, // Agent's current specific action
            isExpanded = isAgentPanelExpanded,
            onToggleExpanded = { isAgentPanelExpanded = !isAgentPanelExpanded },
            modifier =
                Modifier
                    .align(Alignment.TopEnd) // Anchored to top-right
                    .padding(
                        top = 56.dp, // Space below title band
                        bottom = 56.dp // Space above toolbar
                    )
                    .zIndex(10f), // Float above main content
            tabPosition = 0.25f, // Position tab 25% down from top for easy access
            coreLoopState = coreLoopState, // 7-phase Core Loop state machine
            checkInState = checkInState, // Check-in state including extracted themes
            onThemePlaybackClick = {
                // Trigger TTS playback of theme summary
                viewModel.playThemeSummary()
            }
        )

        /* TODO: Re-enable affirmation functionality post-MVP
     * agent overlay: the agent can display helper graphics during in depth conversations
     * if (agentDisplayState != DisplayState.None) {
     *   AffirmationOverlay(
     *           displayState = agentDisplayState,
     *           onStop = { agent.affirmationManager.stopCurrentSequence() }
     *   )
     * }
     */
    } // End Main Layout Box
}

/**
 * Displays the app title "Coherence."
 *
 * @param modifier Modifier to apply to the root Box composable
 */
@Composable
fun AppTitle(modifier: Modifier = Modifier) {

    val outlineColor = MaterialTheme.colorScheme.outline

    // Title band with top and bottom borders only
    Box(
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RectangleShape
                )
                //.border(width = 1.dp, color = accentGold, shape = RectangleShape)
                .drawBehind {
                    // Custom top border
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                .padding(top = 20.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Row containing stars and title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = androidx.compose.ui.res.stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

        }
    }
}
