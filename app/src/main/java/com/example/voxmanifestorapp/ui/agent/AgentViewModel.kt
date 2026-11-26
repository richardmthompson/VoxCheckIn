package com.example.voxmanifestorapp.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voxmanifestorapp.R
import com.example.voxmanifestorapp.data.ConceptActionState
import com.example.voxmanifestorapp.data.ConversationRepository
import com.example.voxmanifestorapp.data.RecognitionState
import com.example.voxmanifestorapp.ui.basevox.VoiceManagedViewModel
import com.example.voxmanifestorapp.ui.utils.SoundPlayer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.voxmanifestorapp.ui.agent.timer.TimerIntent

/**
 * Shared ViewModel that handles common agent-related functionality across different screens.
 * This avoids duplicating agent interaction code in multiple ViewModels.
 */
class AgentViewModel(
    private val agentCortex: AgentCortex,
    private val soundPlayer: SoundPlayer,
    private val conversationRepository: ConversationRepository,
    private val conversationAgent: ConversationAgent
) : ViewModel() {

    // SessionContextManager for session continuity (internal implementation detail)
    private val sessionContextManager = SessionContextManager(
        conversationRepository = conversationRepository,
        agentCortex = agentCortex,
        conversationAgent = conversationAgent,
        scope = viewModelScope
    )

    // Expose agent state flows for UI consumption
    val dialogueState = agentCortex.dialogueState
    val conversationHistory = agentCortex.conversationHistory
    val conversationPlan = agentCortex.conversationPlan
    val currentAction = agentCortex.currentAction
    val commandState = agentCortex.commandState
    val displayState = agentCortex.displayState
    val coreLoopState = agentCortex.coreLoopState
    val checkInState = agentCortex.checkInState
    
    // Add access to conversation type
    val conversationType = agentCortex.conversationType
    
    // Expose latest agent utterance for UI display
    val latestUtterance = agentCortex.latestUtterance

    /**
     * Initiates the Core Conversation Loop by submitting an intent to the agent.
     * This intent will be handled by the ConversationAgent.
     */
    fun submitCoreLoopIntent(startPhase: ConversationPhase = ConversationPhase.CHECK_IN) {
        viewModelScope.launch {
            playButtonSound()
            agentCortex.submitIntent(AgentCortex.UiIntent.InitiateCoreLoop(startPhase))
        }
    }

    /**
     * Handles timer control intents from the UI.
     */
    fun handleTimerIntent(intent: TimerIntent) {
        viewModelScope.launch {
            playDisabledSound()
            agentCortex.submitIntent(AgentCortex.UiIntent.TimerControl(intent))
        }
    }

    /**
     * Sends accrued user speech to the agent for processing.
     */
    fun sendResponse() {
        viewModelScope.launch {
            if (dialogueState.value is DialogueState.ExpectingInput) {
                playSuccessSound()
                agentCortex.submitIntent(AgentCortex.UiIntent.SendResponse)
            } else {
                playDisabledSound()
            }
        }
    }

    /**
     * Requests interruption of the agent's speech.
     */
    fun requestInterrupt() {
        viewModelScope.launch {
            playButtonSound()
            agentCortex.submitIntent(AgentCortex.UiIntent.RequestInterrupt)
        }
    }

    /**
     * Toggles the conversation state (start/pause).
     */
    fun toggleConversation() {
        viewModelScope.launch {
            playButtonSound()
            agentCortex.submitIntent(AgentCortex.UiIntent.RequestToggleConversation)
        }
    }

    /**
     * Terminates the current conversation.
     */
    fun requestTerminate() {
        viewModelScope.launch {
            agentCortex.submitIntent(AgentCortex.UiIntent.RequestTerminateConversation)
        }
    }

    /**
     * Notifies the agent that a screen has been exited.
     */
    fun notifyScreenExit() {
        viewModelScope.launch {
            agentCortex.submitIntent(AgentCortex.UiIntent.NotifyConceptScreenExit)
        }
    }

    /**
     * Handles microphone button click by delegating to the voice model.
     */
    fun handleMicClick(voiceModel: VoiceManagedViewModel, onRequestPermission: () -> Unit) {
        voiceModel.handleMicrophoneClick(onRequestPermission)
    }

    /**
     * Triggers theme summary playback by delegating to the conversation agent.
     */
    fun playThemeSummary() {
        viewModelScope.launch {
            playButtonSound()
            conversationAgent.playThemeSummary()
        }
    }

    // Sound effect methods
    private suspend fun playButtonSound() {
        soundPlayer.playSound(R.raw.button_click)
    }

    private suspend fun playSuccessSound() {
        soundPlayer.playSound(R.raw.button_click)
    }

    private suspend fun playDisabledSound() {
        soundPlayer.playSound(R.raw.button_disabled)
    }

    /**
     * Resets the session from a loaded historical session to a new, fresh session.
     */
    fun resetSessionState() {
        viewModelScope.launch {
            sessionContextManager.unloadSessionContext()
        }
    }
    /**
     * Resumes a previous conversation session by loading its context.
     * This is the public API for session continuity - MainViewModel calls this method.
     */
    fun resumeSession(sessionId: String) {
        viewModelScope.launch {
            try {
                // Validate that the session can be resumed
                val canResume = sessionContextManager.canResumeSession(sessionId)
                if (!canResume) {
                    // Log warning but don't throw - this is expected for invalid sessions
                    return@launch
                }
                
                // Load session context
                val success = sessionContextManager.loadSessionContext(sessionId)
                if (success) {
                    // Session context loaded successfully
                    // The UI will observe the state changes through the exposed flows
                }
            } catch (e: Exception) {
                // Log error but don't crash the UI
                // In a production app, you might want to show a user-friendly error message
            }
        }
    }
} 