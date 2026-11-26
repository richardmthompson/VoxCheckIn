package com.example.voxmanifestorapp.ui.agent.voice

import android.util.Log
import com.example.voxmanifestorapp.data.RecognitionState
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.ConversationType
import com.example.voxmanifestorapp.ui.agent.DialogueState
import com.example.voxmanifestorapp.ui.agent.voice.VoiceCommandEntity
import com.example.voxmanifestorapp.ui.agent.VoxInputType
import com.example.voxmanifestorapp.ui.agent.AgentCortex
import com.example.voxmanifestorapp.ui.agent.utilities.TextFormatting
import com.example.voxmanifestorapp.ui.basevox.AudioInfo
import com.example.voxmanifestorapp.ui.basevox.TextToSpeechRepository
import com.example.voxmanifestorapp.ui.basevox.VoiceManagedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Manages voice input and output processing for the ConversationAgent.
 * Handles speech synthesis, voice recognition monitoring, and input routing.
 */
class VoiceProcessor(
    private val ttsRepo: TextToSpeechRepository,
    private val agentCortex: AgentCortex,
    private val logger: (String, com.example.voxmanifestorapp.data.StatusColor) -> Unit,
    private val scope: CoroutineScope
) {
    private val TAG = "genie"
    
    // Control voice input and output (Text to Speech and Speech to Text)
    private var voiceManager: VoiceManagedViewModel? = null
    
    // Record agent's most recent utterance so it can be ignored
    private var lastAgentSpeech: String = ""
    
    // Used to ignore recognition text when agent speaking
    private var lastSpeechEndTime: Long = 0L

    /**
     * Sets the voice manager for voice control operations
     * Called from NavHost after VM is created
     */
    fun setVoiceManager(vm: VoiceManagedViewModel) {
        if (voiceManager == null) {
            voiceManager = vm
            logger(
                "Captured voice manager for voice control... Starting monitoring now...",
                StatusColor.Pause
            )
            setupVoiceMonitoring()
            Log.d(
                "AgentInit",
                "setVoiceManager called with manager ${vm.hashCode()}"
            )
        }
    }

    /**
     * Sets up voice recognition monitoring once we have a VoiceManagedViewModel
     */
    private fun setupVoiceMonitoring() {
        voiceManager?.let { vm ->
            logger("Setting up voice monitoring, about to launch voice recog.", 
                StatusColor.Pause)
            
            scope.launch {
                combine(
                    vm.recognitionState,
                    vm.rawTextState,        // Now a pair with timestamps
                    agentCortex.dialogueState
                ) { recogState, rawTextPair, dialogState ->
                    val (text, timestamp) = rawTextPair
                    if (dialogState != DialogueState.Speaking &&
                        text.isNotEmpty() && timestamp > lastSpeechEndTime) {
                        
                        val command = VoiceCommandEntity.processCommand(text, agentCortex.conversationType.value)
                        
                        // Delegate to the voice input handler
                        onVoiceInputReceived?.invoke(command, text.lowercase(), timestamp)
                    }
                }.collect()
            }
        }
    }

    /**
     * Callback for when voice input is received
     * This will be set by the ConversationAgent to route input appropriately
     */
    var onVoiceInputReceived: (suspend (VoiceCommandEntity, String, Long) -> Unit)? = null

    /**
     * Main speech synthesis function
     */
    suspend fun speak(message: String) {
        val agentId = System.identityHashCode(this)
        
        // Clean the message for TTS synthesis
        val cleanedMessage = TextFormatting.cleanForTTS(message)
        
        // Log if markdown was removed
        if (TextFormatting.containsMarkdown(message)) {
            Log.d("AgentDebug", "Agent $agentId: Removed markdown formatting from speech")
            Log.d("AgentDebug", "Original: ${message.take(100)}...")
            Log.d("AgentDebug", "Cleaned: ${cleanedMessage.take(100)}...")
        } else {
            Log.d("AgentDebug", "Agent $agentId speaking: $cleanedMessage")
        }

        try {
            agentCortex.updateDialogueState(DialogueState.Speaking)
            val audioInfo = ttsRepo.speak(cleanedMessage)

            // Store the cleaned message before speaking it
            lastAgentSpeech = cleanedMessage.lowercase()

            // Monitor actual playback with logging
            var loopCount = 0
            while (audioInfo.audioTrack.playbackHeadPosition.toLong() < audioInfo.totalFrames) {
                val currentPosition = audioInfo.audioTrack.playbackHeadPosition.toLong()
                val progress = (currentPosition.toFloat() / audioInfo.totalFrames * 100).toInt()
                
                // Log every 10th check (every 500ms) to avoid spam
                if (loopCount % 10 == 0) {
                    Log.d("VoiceProcessor", "Playback progress: $currentPosition / ${audioInfo.totalFrames} frames ($progress%)")
                }
                
                loopCount++
                delay(50L)
            }

            // Log completion
            Log.d("VoiceProcessor", "Playback COMPLETE: ${audioInfo.audioTrack.playbackHeadPosition} / ${audioInfo.totalFrames} frames after ${loopCount * 50}ms")

        } finally {
            // Record the speech end time
            lastSpeechEndTime = System.currentTimeMillis()
            
            // IMPORTANT: Do NOT automatically reset the state to Idle
            // This allows conversation flows to explicitly set the next state
            // (e.g., ExpectingInput, Thinking, etc.) based on their specific needs
            
            // Debug log
            logger("(speak) speaking finished at timestamp [${lastSpeechEndTime}]", 
                StatusColor.Default)
        }
    }

    /**
     * Interrupts ongoing speech immediately
     */
    fun interruptSpeech() {
        // Stop TTS immediately
        ttsRepo.release()

        // Update last speech end time because this will have been interrupted when we stopped the process.
        lastSpeechEndTime = System.currentTimeMillis()
        
        // Update dialogue state - i.e. there is no ongoing conversation. no one is speaking.
        agentCortex.updateDialogueState(DialogueState.Idle)

        logger("Speech interrupted", com.example.voxmanifestorapp.data.StatusColor.Pause)
    }

    /**
     * Processes user response (typically from button press)
     */
    suspend fun processUserResponse(): String? {
        if (agentCortex.dialogueState.value !is DialogueState.ExpectingInput) {
            logger("Cannot send data, not expecting input.", com.example.voxmanifestorapp.data.StatusColor.Default)
            return null
        }

        logger("Processing user input (button pressed)", com.example.voxmanifestorapp.data.StatusColor.Go)
        
        // Get the user's raw text from the voice model
        val rawText = voiceManager?.rawTextState?.value?.first ?: ""
        if (rawText.isEmpty()) {
            logger("No input text to process", com.example.voxmanifestorapp.data.StatusColor.Stop)
            return null
        }
        
        return rawText
    }

    /**
     * Adds user response to conversation history
     */
    fun handleUserResponse(text: String) {
        // Add to conversation history
        val currentPhase = agentCortex.coreLoopState.value.currentPhase
        // Add to conversation history
        // TODO: This needs to be handled by the calling context
        // The VoiceProcessor shouldn't directly manage conversation history
    }

    /**
     * Checks if the detected speech contains the agent's last utterance (to prevent loops)
     */
    fun shouldIgnoreSpeech(text: String): Boolean {
        return lastAgentSpeech.isNotEmpty() && 
               cleanSpeech(text.lowercase()).contains(cleanSpeech(lastAgentSpeech))
    }

    /**
     * Cleans speech text for comparison (removes punctuation, extra spaces)
     */
    private fun cleanSpeech(text: String): String {
        return text.replace(Regex("[^a-zA-Z0-9\\s]"), "")
                   .replace(Regex("\\s+"), " ")
                   .trim()
    }

    /**
     * Gets the current voice manager
     */
    fun getVoiceManager(): VoiceManagedViewModel? = voiceManager

    /**
     * Checks if microphone is active
     */
    fun isMicrophoneActive(): Boolean {
        return voiceManager?.recognitionState?.value == RecognitionState.Listen_Active
    }

    /**
     * Ensures microphone is active for conversation operations
     */
    fun ensureMicrophoneActive() {
        voiceManager?.let { vm -> 
            if (vm.recognitionState.value == RecognitionState.Inactive || 
                vm.recognitionState.value == RecognitionState.Listen_Pause) {
                logger("Ensuring microphone is active before starting conversation", 
                    com.example.voxmanifestorapp.data.StatusColor.Go)
                vm.startVoiceRecognition()
            }
        }
    }
}