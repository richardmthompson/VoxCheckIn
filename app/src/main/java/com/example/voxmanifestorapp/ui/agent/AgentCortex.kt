package com.example.voxmanifestorapp.ui.agent

import com.example.voxmanifestorapp.data.ConceptActionState
import com.example.voxmanifestorapp.ui.agent.checkin.CheckInStage
import com.example.voxmanifestorapp.ui.agent.checkin.CheckInState
import com.example.voxmanifestorapp.ui.agent.checkin.ConversationalTheme
import com.example.voxmanifestorapp.ui.agent.checkin.UserEngagementMetrics
import com.example.voxmanifestorapp.ui.agent.timer.TimerIntent
import com.example.voxmanifestorapp.ui.agent.timer.TimerState
import com.example.voxmanifestorapp.ui.agent.voice.VoiceCommandEntity
import kotlinx.coroutines.flow.*
import android.util.Log

/**
 * Central state store for agent-related state and communication hub for UI intents.
 * Provides a single source of truth for state observable by the UI (via ViewModel)
 * and updated exclusively by the ConversationAgent.
 * It also provides a channel for UI intents to be sent to the ConversationAgent.
 */
class AgentCortex {
    // Inject a scope if AgentCortex needs its own lifecycle not tied to a ViewModel
    // private val cortexScope: CoroutineScope

    // --- STATE DEFINITIONS ---
    // AgentCortex remains the SSoT for this state. Updated ONLY by the Agent.

    private val _dialogueState = MutableStateFlow<DialogueState>(DialogueState.Idle)
    val dialogueState: StateFlow<DialogueState> = _dialogueState.asStateFlow()

    private val _displayState = MutableStateFlow<DisplayState>(DisplayState.None)
    val displayState: StateFlow<DisplayState> = _displayState.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ConversationEntry>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationEntry>> = _conversationHistory.asStateFlow()

    // Store the current session ID - starts with -1 to indicate uninitialized state
    private var _currentSessionId = "-1"
    val currentSessionId: String
        get() = _currentSessionId

    private val _rawSpeechText = MutableStateFlow("")
    val rawSpeechText: StateFlow<String> = _rawSpeechText.asStateFlow()

    private val _commandState = MutableStateFlow<VoiceCommandEntity>(VoiceCommandEntity.BACKGROUND)
    val commandState: StateFlow<VoiceCommandEntity> = _commandState.asStateFlow()

    private val _conversationPlan = MutableStateFlow<ConversationPlan?>(null)
    val conversationPlan: StateFlow<ConversationPlan?> = _conversationPlan.asStateFlow()

    private val _currentAction = MutableStateFlow<ConceptActionState>(ConceptActionState.INITIATE)
    val currentAction: StateFlow<ConceptActionState> = _currentAction.asStateFlow()

    // CoreLoopState implementation
    private val _coreLoopState = MutableStateFlow(CoreLoopState())
    val coreLoopState: StateFlow<CoreLoopState> = _coreLoopState.asStateFlow()

    // CHECK_IN state variable (for within the first stage of core loop):
    private val _checkInState = MutableStateFlow(CheckInState())
    val checkInState: StateFlow<CheckInState> = _checkInState.asStateFlow()

    // First, add a private MutableStateFlow for conversationType
    private val _conversationType = MutableStateFlow<ConversationType?>(null)
    val conversationType: StateFlow<ConversationType?> = _conversationType.asStateFlow()
    
    // Latest agent utterance for immediate UI display
    private val _latestUtterance = MutableStateFlow<String?>(null)
    val latestUtterance: StateFlow<String?> = _latestUtterance.asStateFlow()

    /**
     * Defines intents that the UI can send to signal desired actions from the Agent.
     */
    sealed class UiIntent {
        object RequestInterrupt : UiIntent()
        object RequestToggleConversation : UiIntent()
        object RequestTerminateConversation : UiIntent()
        object NotifyConceptScreenExit : UiIntent() // Agent might need to clean up
        object SendResponse : UiIntent()           // User confirmed sending input
        data class InitiateCoreLoop(val startPhase: ConversationPhase = ConversationPhase.CHECK_IN) : UiIntent()
        data class TimerControl(val timerIntent: TimerIntent) : UiIntent()
        // Add other UI-driven intents here if needed
    }

    // Private MutableSharedFlow used internally to emit UI intents.
    // extraBufferCapacity helps handle potential bursts of intents from the UI.
    private val _uiIntentFlow = MutableSharedFlow<UiIntent>(extraBufferCapacity = 64)

    // Public, immutable SharedFlow for the ConversationAgent to collect (listen to)
    // UI-initiated intents.
    val uiIntentFlow: SharedFlow<UiIntent> = _uiIntentFlow.asSharedFlow()

    /**
     * Called by the ViewModel to submit a user intent for the Agent to process.
     * Uses tryEmit for non-suspending emission. The ConversationAgent collects these intents.
     * Returns true if emission was successful, false otherwise (e.g., buffer overflow).
     */
    fun submitIntent(intent: UiIntent) : Boolean {
        return _uiIntentFlow.tryEmit(intent) // Non-suspending, use in ViewModelScope
    }

    fun updateSessionId(id: String) {
        _currentSessionId = id
    }

    // --- AGENT-EXCLUSIVE STATE UPDATE METHODS ---
    // These methods are the ONLY way state should be mutated.
    // Called exclusively by the ConversationAgent after its logic runs.
    // Consider making these `internal` if modules allow, or rely on convention/documentation.

    fun updateDialogueState(state: DialogueState) {
        android.util.Log.d("AgentCortex", "Updating DialogueState to: ${state::class.simpleName}")
        _dialogueState.value = state
    }

    fun updateDisplayState(state: DisplayState) {
        _displayState.value = state
    }

    fun addConversationEntry(entry: ConversationEntry) {
        // Ensure thread-safety if agent modifies concurrently (though unlikely if agent uses single scope)
        _conversationHistory.update { currentList -> currentList + entry }
    }

    fun clearConversationHistory() {
        _conversationHistory.value = emptyList()
    }

    fun updateRawSpeechText(text: String) {
        _rawSpeechText.value = text
    }

    fun updateCommandState(command: VoiceCommandEntity) {
        _commandState.value = command
    }

    fun updateConversationPlan(plan: ConversationPlan?) {
        _conversationPlan.value = plan
    }

    fun updateCurrentAction(state: ConceptActionState) {
        _currentAction.value = state
    }

    /**
     * Updates the CoreLoopState.
     */
    fun updateCoreLoopState(state: CoreLoopState) {
        _coreLoopState.value = state
    }

    /**
     * Updates specific CoreLoopState fields without requiring manual state copying.
     * This eliminates the need for hacky copy() patterns throughout the codebase.
     */
    fun updateCoreLoopState(
        phase: ConversationPhase? = null,
        wishIndex: Int? = null,
        understanding: String? = null,
        loopCount: Int? = null
    ) {
        _coreLoopState.update { currentState ->
            currentState.copy(
                currentPhase = phase ?: currentState.currentPhase,
                currentWishIndex = wishIndex ?: currentState.currentWishIndex,
                currentUnderstanding = understanding ?: currentState.currentUnderstanding,
                loopCount = loopCount ?: currentState.loopCount
            )
        }
    }

    /**
     * Updates the timer state within CheckInState.
     */
    fun updateTimerState(newTimerState: TimerState) {
        _checkInState.update { currentState ->
            currentState.copy(timerState = newTimerState)
        }
    }

    fun updateCheckInState(state: CheckInState) {
        _checkInState.value = state
    }

    /**
     * Updates the CheckInState using a transform function.
     * This allows for partial updates to the state without replacing the entire object.
     */
    fun updateCheckInState(transform: (CheckInState) -> CheckInState) {
        _checkInState.update { currentState -> 
            transform(currentState)
        }
    }

    /**
     * Updates the active conversational themes within CheckInState.
     */
    fun updateActiveThemes(themes: List<ConversationalTheme>) {
        Log.d("AgentCortex", "=== UPDATING ACTIVE THEMES ===")
        Log.d("AgentCortex", "Received ${themes.size} themes to update")
        themes.forEachIndexed { index, theme ->
            Log.d("AgentCortex", "Theme $index: '${theme.title}' with ${theme.observations.size} observations")
        }
        
        _checkInState.update { currentState ->
            Log.d("AgentCortex", "Previous activeThemes count: ${currentState.activeThemes.size}")
            val newState = currentState.copy(activeThemes = themes)
            Log.d("AgentCortex", "Updated activeThemes count: ${newState.activeThemes.size}")
            newState
        }
        
        Log.d("AgentCortex", "Active themes update complete")
    }

    /**
     * Updates the engagement metrics within CheckInState.
     */
    fun updateEngagementMetrics(metrics: UserEngagementMetrics) {
        _checkInState.update { currentState ->
            currentState.copy(engagementMetrics = metrics)
        }
    }

    /**
     * Adds an observation to an existing theme or creation a new theme if it doesn't exist,
     * within CheckInState.
     */
    fun addOrUpdateThemeObservation(themeTitle: String, observation: String) {
        _checkInState.update { currentState ->
            val currentThemes = currentState.activeThemes.toMutableList()
        val existingTheme = currentThemes.find { it.title == themeTitle }

        if (existingTheme != null) {
            existingTheme.observations.add(observation)
        } else {
            currentThemes.add(ConversationalTheme(title = themeTitle, observations = mutableListOf(observation)))
        }
            currentState.copy(activeThemes = currentThemes)
        }
    }

    /**
     * Clears all active conversational themes within CheckInState.
     */
    fun clearActiveThemes() {
        _checkInState.update { currentState ->
            currentState.copy(activeThemes = emptyList())
        }
    }

    /**
     * Advances the check-in stage to the next stage in the sequence.
     * This is a pure state management operation that doesn't require business logic.
     */
    fun progressCheckInStage() {
        // Simplified flow: OPENING → TRANSITION
        _checkInState.update { currentState -> 
            currentState.copy(
                currentStage = when (currentState.currentStage) {
                    CheckInStage.OPENING -> CheckInStage.TRANSITION
                    // If somehow we're already in TRANSITION, stay there
                    else -> CheckInStage.TRANSITION
                }
            )
        }
    }

    // Then add a function to update it
    fun updateConversationType(type: ConversationType?) {
        _conversationType.value = type
    }
    
    /**
     * Updates the latest agent utterance for immediate UI display.
     * Called by ConversationAgent before speaking to ensure UI updates instantly.
     */
    fun updateLatestUtterance(text: String?) {
        _latestUtterance.value = text
    }

    /**
     * Resets conversation state variables with timer management.
     * This combines timer state reset and main screen selection clearing.
     */
    fun resetConversationState() {
        // Reset timer state to Inactive
        updateCheckInState { currentState ->
            currentState.copy(timerState = TimerState.Inactive)
        }
    }
}