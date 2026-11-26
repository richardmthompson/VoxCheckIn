package com.example.voxmanifestorapp.ui.agent.voice

import android.util.Log
import com.example.voxmanifestorapp.data.RecognitionState
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.AgentCortex
import com.example.voxmanifestorapp.ui.agent.ConversationType
import com.example.voxmanifestorapp.ui.agent.DialogueState
import com.example.voxmanifestorapp.ui.agent.voice.VoiceCommandEntity
import com.example.voxmanifestorapp.ui.agent.utilities.TextFormatting
import com.example.voxmanifestorapp.ui.basevox.AudioInfo
import com.example.voxmanifestorapp.ui.basevox.TextToSpeechRepository
import com.example.voxmanifestorapp.ui.basevox.VoiceManagedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// Voice state tracking
private var lastAgentSpeech: String = ""
private var lastSpeechEndTime: Long = 0L

/**
 * Main speech synthesis function
 */
suspend fun speak(
    message: String,
    ttsRepo: TextToSpeechRepository,
    logger: (String, StatusColor) -> Unit
) {
    val agentId = System.identityHashCode(ttsRepo)
    
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
        val audioInfo = ttsRepo.speak(cleanedMessage)

        // Store the cleaned message before speaking it
        lastAgentSpeech = cleanedMessage.lowercase()
        logger("🗣️ AGENT SPEAKS: '$cleanedMessage' -> lastAgentSpeech: '$lastAgentSpeech'", StatusColor.Default)

        // Monitor actual playback with logging
        var loopCount = 0
        while (audioInfo.audioTrack.playbackHeadPosition.toLong() < audioInfo.totalFrames) {
            val currentPosition = audioInfo.audioTrack.playbackHeadPosition.toLong()
            val progress = (currentPosition.toFloat() / audioInfo.totalFrames * 100).toInt()
            
            // Log every 10th check (every 500ms) to avoid spam
            if (loopCount % 10 == 0) {
                Log.d("VoiceTiming", "Playback progress: $currentPosition / ${audioInfo.totalFrames} frames ($progress%)")
            }
            
            loopCount++
            delay(50L)
        }

        // Log completion
        Log.d("VoiceTiming", "Playback COMPLETE: ${audioInfo.audioTrack.playbackHeadPosition} / ${audioInfo.totalFrames} frames after ${loopCount * 50}ms")

    } finally {
        // Record the speech end time
        lastSpeechEndTime = System.currentTimeMillis()
        
        // IMPORTANT: Do NOT automatically reset the state to Idle
        // This allows conversation flows to explicitly set the next state
        // (e.g., ExpectingInput, Thinking, etc.) based on their specific needs
        
        // Debug log
        logger("(speak) speaking finished at timestamp [${lastSpeechEndTime}]", StatusColor.Default)
    }
}

/**
 * Sets the voice manager for voice control operations
 * Called from NavHost after VM is created
 */
fun setVoiceManager(
    vm: VoiceManagedViewModel,
    agentCortex: AgentCortex,
    scope: CoroutineScope,
    logger: (String, StatusColor) -> Unit,
    onVoiceInput: suspend (VoiceCommandEntity, String, Long) -> Unit
): VoiceManagedViewModel? {
    logger(
        "Captured voice manager for voice control... Starting monitoring now...",
        StatusColor.Pause
    )
    setupVoiceMonitoring(vm, agentCortex, scope, logger, onVoiceInput)
    Log.d(
        "AgentInit",
        "setVoiceManager called with manager ${vm.hashCode()}"
    )
    return vm
}

/**
 * Sets up voice recognition monitoring once we have a VoiceManagedViewModel
 */
private fun setupVoiceMonitoring(
    voiceManager: VoiceManagedViewModel,
    agentCortex: AgentCortex,
    scope: CoroutineScope,
    logger: (String, StatusColor) -> Unit,
    onVoiceInput: suspend (VoiceCommandEntity, String, Long) -> Unit
) {
    logger("Setting up voice monitoring, about to launch voice recog.", StatusColor.Pause)
    
    scope.launch {
        combine(
            voiceManager.recognitionState,
            voiceManager.rawTextState,        // Now a pair with timestamps
            agentCortex.dialogueState
        ) { recogState, rawTextPair, dialogState ->
            val (text, timestamp) = rawTextPair
            if (dialogState != DialogueState.Speaking &&
                text.isNotEmpty() && timestamp > lastSpeechEndTime) {
                
                val command = VoiceCommandEntity.processCommand(text, agentCortex.conversationType.value)
                
                // Delegate to the voice input handler
                onVoiceInput(command, text.lowercase(), timestamp)
            }
        }.collect()
    }
}

/**
 * Interrupts ongoing speech immediately
 */
fun interruptSpeech(
    ttsRepo: TextToSpeechRepository,
    logger: (String, StatusColor) -> Unit
) {
    // Stop TTS immediately
    ttsRepo.release()

    // Update last speech end time because this will have been interrupted when we stopped the process.
    lastSpeechEndTime = System.currentTimeMillis()
    
    logger("Speech interrupted", StatusColor.Pause)
}

/**
 * Processes user response (typically from button press)
 */
suspend fun processUserResponse(
    voiceManager: VoiceManagedViewModel?,
    agentCortex: AgentCortex,
    logger: (String, StatusColor) -> Unit
): String? {
    if (agentCortex.dialogueState.value !is DialogueState.ExpectingInput) {
        logger("Cannot send data, not expecting input.", StatusColor.Default)
        return null
    }

    logger("Processing user input (button pressed)", StatusColor.Go)
    
    // Get the user's raw text from the voice model
    val rawText = voiceManager?.rawTextState?.value?.first ?: ""
    if (rawText.isEmpty()) {
        logger("No input text to process", StatusColor.Stop)
        return null
    }
    
    return rawText
}

/**
 * Checks if the detected speech contains the agent's last utterance (to prevent loops)
 */
fun shouldIgnoreSpeech(text: String): Boolean {
    val cleanInput = cleanSpeech(text.lowercase())
    val cleanLastSpeech = cleanSpeech(lastAgentSpeech)
    val shouldIgnore = lastAgentSpeech.isNotEmpty() && cleanInput.contains(cleanLastSpeech)
    
    if (shouldIgnore) {
        // This will be logged by the calling function in ConversationAgent
        // Just capture the details here for debugging
    }
    
    return shouldIgnore
}

/**
 * Cleans speech text for comparison (removes punctuation, extra spaces)
 */
private fun cleanSpeech(text: String): String {
    return text.replace(Regex("[^a-zA-Z0-9\\s]"), "")
               .replace(Regex("\\s+"), " ")
               .trim()
}

