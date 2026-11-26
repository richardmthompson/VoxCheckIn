package com.example.voxmanifestorapp.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.data.SessionInfo
import com.example.voxmanifestorapp.data.ConversationRepository
import ManifestationRepository
import com.example.voxmanifestorapp.ui.agent.AgentCortex
import com.example.voxmanifestorapp.ui.agent.AgentViewModel
import com.example.voxmanifestorapp.ui.agent.ConversationEntry
import com.example.voxmanifestorapp.ui.agent.ConversationPhase
import com.example.voxmanifestorapp.ui.agent.DialogueState
import com.example.voxmanifestorapp.ui.agent.Speaker
import com.example.voxmanifestorapp.ui.agent.timer.TimerIntent
import com.example.voxmanifestorapp.ui.main.MainScreenState
import com.example.voxmanifestorapp.ui.main.TerminalEntryType
import com.example.voxmanifestorapp.ui.basevox.TextToSpeechRepository
import com.example.voxmanifestorapp.ui.basevox.VoiceManagedViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: ManifestationRepository,
    private val ttsRepo: TextToSpeechRepository,
    private val mainScreenState: MainScreenState,
    private val agentCortex: AgentCortex,
    private val agentViewModel: AgentViewModel,
    private val conversationRepository: ConversationRepository,
    private val voiceManagedViewModel: VoiceManagedViewModel
) : ViewModel() {

    // Use agentViewModel directly
    val dialogueState = agentViewModel.dialogueState
    val commandState = agentViewModel.commandState
    val agentDisplayState = agentViewModel.displayState
    val currentPlan = agentViewModel.conversationPlan
    val currentAction = agentViewModel.currentAction
    val coreLoopState = agentViewModel.coreLoopState
    val checkInState = agentViewModel.checkInState
    val conversationType = agentViewModel.conversationType

    // vars for sourcing conversation entries
    val conversationHistory = agentCortex.conversationHistory
    val latestAgentUtterance = agentCortex.latestUtterance

    // initialize user utterance to empty string, ready to receive from collection @ init
    private val _latestUserUtterance = MutableStateFlow<String>("")
    val latestUserUtterance: StateFlow<String> = _latestUserUtterance

    // agent terminal variable
    private val _agentTerminalContent = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val agentTerminalContent: StateFlow<List<TerminalEntry>> = _agentTerminalContent.asStateFlow()

    // user terminal variable
    private val _userTerminalContent = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val userTerminalContent: StateFlow<List<TerminalEntry>> = _userTerminalContent.asStateFlow()

    // Session information
    private val _sessionInfoList = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessionInfoList: StateFlow<List<SessionInfo>> = _sessionInfoList.asStateFlow()
    
    // Selected session entries
    private val _sessionEntries = MutableStateFlow<List<ConversationEntry>>(emptyList())
    val sessionEntries: StateFlow<List<ConversationEntry>> = _sessionEntries.asStateFlow()
    
    // Currently viewed session ID (if viewing a past session)
    private val _viewingSessionId = MutableStateFlow<String?>(null)
    val viewingSessionId: StateFlow<String?> = _viewingSessionId.asStateFlow()

    // Take selected slot from main screen state single source of truth
    val selectedSlot = mainScreenState.selectedSlot

    init {
        // Load session info when ViewModel is created
        loadAllSessionInfo()

        // Observe session count and auto-refresh list when new sessions are created
        viewModelScope.launch {
            conversationRepository.getSessionCountFlow()
                .distinctUntilChanged()  // Only emit when count actually changes
                .drop(1)                 // Skip initial value (already loaded above)
                .collect { newCount ->
                    Log.d("MainViewModel", "Session count changed to $newCount - refreshing session list")
                    loadAllSessionInfo()
                }
        }

        // Initialize agent terminal with ready message on app startup
        agentTerminalInit()

        viewModelScope.launch {
            latestAgentUtterance.collect { utterance ->
                // Only log non-null, non-empty utterances to prevent blank lines
                if (!utterance.isNullOrBlank()) {
                    logAgentTerminalEntry(TerminalEntry(
                        entryType = TerminalEntryType.AgentSpeech,
                        content = utterance,
                    ))
                }
            }
        }

        /** when we're receiving user input, update our latestUserUtterance variable so we can
         * display it in the user terminal
         */
        viewModelScope.launch {
            // set the user utterance flow variable to null when the agent speaks so the user terminal doesn't show agent speech!
            dialogueState.collect { dialogueState ->
                if (dialogueState is DialogueState.Speaking) {
                    _latestUserUtterance.value = ""
                }
            }
        }

        viewModelScope.launch {
            voiceManagedViewModel.rawTextState.collect { rawTextState ->
                // only triggers if we're in expecting input state.  so once we get our first emission of raw text from voice,
                // we only process it into the user UI if we're sure it's coming from the right source.
                if (dialogueState.value is DialogueState.ExpectingInput) {
                    _latestUserUtterance.value = rawTextState.first
                }
            }
        }

        viewModelScope.launch {
            // conversationHistory is a stateflow that emits a List<ConversationEntry>'s
            conversationHistory.collect { history ->
                _userTerminalContent.value = history
                    .filter { it.speaker == Speaker.User }
                    .map { entry ->
                        TerminalEntry(
                            entryType = TerminalEntryType.UserSpeech,
                            content = entry.content,
                            timestamp = entry.timestamp
                        )
                    }
            }
        }
    }

    fun logAgentTerminalEntry(entry: TerminalEntry) {
        _agentTerminalContent.value += entry
    }
    /**
     * Loads all session information for display in the session cards
     */
    fun loadAllSessionInfo() {
        viewModelScope.launch {
            _sessionInfoList.value = conversationRepository.getAllSessionsInfo(agentCortex.currentSessionId)
        }
    }
    
    /**
     * Loads conversation entries for a specific session
     */
    fun loadSessionEntries(sessionId: String) {
        viewModelScope.launch {
            _viewingSessionId.value = sessionId
            conversationRepository.getSessionEntriesFlow(sessionId)
                .collect { entries ->
                    _sessionEntries.value = entries
                }
        }
    }

    fun agentTerminalInit() {
        // show initial status message for user...
        logAgentTerminalEntry(
            TerminalEntry(
                entryType = TerminalEntryType.AgentStatus,
                content = "Genie is ready to assist..."
            )
        )
    }
    /**
     * Resets the session from a loaded historical session to a new, fresh session
     */
    fun resetSession() {
        agentViewModel.resetSessionState()
        // runs following function to simply unload the flag that's used when a previous session is being viewed!
        returnToCurrentSession()
        // next up, we need to re-set the UI to its base format.
        // that means wiping both the terminals and re-setting to start mode.
        // Clear agent terminal content
        _agentTerminalContent.value = emptyList()
        // wipe the latest agent utterance so the previous entry doesn't get sent to the terminal
        agentCortex.updateLatestUtterance("")
        // Re-initialize with ready message
        agentTerminalInit()
    }
    /**
     * Returns to viewing the current session
     */
    fun returnToCurrentSession() {
        _viewingSessionId.value = null
    }

    /**
     * Resumes a previous conversation session by loading its context
     */
    fun resumeSession(sessionId: String) {
        Log.d("MainViewModel", "Resuming session: $sessionId")
        
        // Delegate to AgentViewModel for session continuity
        agentViewModel.resumeSession(sessionId)
        
        // Return to current session view
        returnToCurrentSession()
    }

    /**
     * Starts the Core Conversation Loop by submitting a specific intent to the agent.
     * This function is called by the UI to initiate the structured coaching conversation.
     */
    fun startCoreLoop() {
        viewModelScope.launch {
            // Use the specific core loop intent
            agentViewModel.submitCoreLoopIntent()
        }
    }

    // Function to handle selection in the UI
    fun toggleSlotSelection(slot: Int) {
        val currentlySelected = selectedSlot.value
        if (currentlySelected == slot) {
            Log.d("TOGGLE_SLOT", "De-selecting slot ${slot} and interrupting agent.")
            clearSlotSelection()
            requestTerminate()
        } else {
            Log.d("TOGGLE_SLOT", "Selecting slot ${slot}.")
            setSelectedSlot(slot)
        }
    }

    fun setSelectedSlot(slot: Int) {
        mainScreenState.setSelectedSlot(slot)
    }

    fun clearSlotSelection() {
        mainScreenState.clearSelection()
    }

    val allManifestations: Flow<List<Manifestation>> = repository.getAllManifestations()

    fun addManifestation(manifestation: Manifestation) = viewModelScope.launch {
        repository.insertManifestation(manifestation)
    }

    fun deleteManifestation(manifestation: Manifestation) = viewModelScope.launch {
        repository.deleteManifestation(manifestation)
    }

    // Delegate agent control functions directly
    fun requestTerminate() = agentViewModel.requestTerminate()
    fun requestInterrupt() = agentViewModel.requestInterrupt()

    fun toggleConversation() = agentViewModel.toggleConversation()

    fun sendResponse() = agentViewModel.sendResponse()

    /**
     * Handles timer control intents from the UI
     */
    fun handleTimerIntent(intent: TimerIntent) {
        viewModelScope.launch {
            agentViewModel.handleTimerIntent(intent)
        }
    }

    /**
     * Triggers theme summary playback
     */
    fun playThemeSummary() {
        viewModelScope.launch {
            agentViewModel.playThemeSummary()
        }
    }


}
