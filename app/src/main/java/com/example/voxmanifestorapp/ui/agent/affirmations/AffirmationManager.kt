package com.example.voxmanifestorapp.ui.agent.affirmations

import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.BrainService
import com.example.voxmanifestorapp.ui.agent.ConversationSequence
import com.example.voxmanifestorapp.ui.agent.ConversationTool
import com.example.voxmanifestorapp.ui.agent.DisplayState
import com.example.voxmanifestorapp.ui.agent.affirmations.AffirmationTool
import com.example.voxmanifestorapp.ui.agent.affirmations.AffirmationToolInput
import com.example.voxmanifestorapp.ui.agent.AgentCortex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages affirmation generation and execution sequences.
 * Handles the complete affirmation workflow from command processing to sequence execution.
 */
class AffirmationManager(
    private val brainService: BrainService,
    private val agentCortex: AgentCortex,
    private val logger: (String, StatusColor) -> Unit,
    private val scope: CoroutineScope
) {
    
    // Affirmation tool for generating affirmation sequences
    private val affirmationTool: ConversationTool = AffirmationTool(
        brainService = brainService,
        logger = logger
    )

    // Reference to current sequence job so we can cancel sequenced conversations mid-flow
    private var currentSequenceJob: Job? = null

    /**
     * Handles the 'affirm' voice command
     * Extracts number of affirmations and initiates the affirmation sequence
     */
    suspend fun handleAffirmCommand(text: String, currentWishes: List<Manifestation>, speak: suspend (String) -> Unit) {
        logger("Handling Affirmation command", StatusColor.Go)

        if (currentWishes.isEmpty()) {
            speak("You need to add some wishes before we can do affirmations.")
            return
        }

        // Extract the number of affirmations from our utterance  
        val numAffirmations = extractNumber(text) ?: 1

        // Create input for the affirmation tool
        val input = AffirmationToolInput(
            wishes = currentWishes,
            numAffirmations = numAffirmations
        )

        // The tool execution returns an entire conversation sequence
        val sequence = affirmationTool.execute(input)
        executeSequence(sequence)
    }

    /**
     * Executes a conversation sequence (typically for affirmations)
     * Manages display states, speech, and timing for each action in the sequence
     */
    private suspend fun executeSequence(sequence: ConversationSequence) {
        // Cancel any existing sequence
        stopCurrentSequence()

        currentSequenceJob = scope.launch {
            try {
                sequence.actions.forEach { action ->
                    if (!isActive) return@forEach

                    // Update display state first
                    agentCortex.updateDisplayState(action.displayState)

                    // Speak if there's text
                    if (action.speak.isNotEmpty()) {
                        // Note: speak function needs to be passed in from the calling context
                        // This is a design decision - the AffirmationManager doesn't own speech
                        // TODO: Consider how to handle speech dependency
                    }

                    // Wait specified time
                    delay(action.pauseAfter)
                }
            } finally {
                // Reset display state when done or cancelled
                agentCortex.updateDisplayState(DisplayState.None)
                sequence.onComplete()
            }
        }
    }

    /**
     * Stops the current affirmation sequence if one is running
     */
    fun stopCurrentSequence() {
        currentSequenceJob?.cancel()
        currentSequenceJob = null
        agentCortex.updateDisplayState(DisplayState.None)
    }

    /**
     * Checks if an affirmation sequence is currently running
     */
    fun isSequenceRunning(): Boolean = currentSequenceJob?.isActive == true

    /**
     * Extracts numbers from text, trying digits first then word numbers
     */
    private fun extractNumber(text: String): Int? {
        // First try to extract digit numbers
        val digitNumber = extractIntNumber(text)
        if (digitNumber != null) {
            return digitNumber
        }

        // If that fails, try to extract word numbers
        return parseNumberWord(text)
    }

    /**
     * Extracts integer numbers from text using regex
     */
    private fun extractIntNumber(text: String): Int? {
        val regex = "\\d+".toRegex()
        return regex.find(text)?.value?.toIntOrNull()
    }

    /**
     * Parses word numbers (one, two, etc.) to integers
     */
    private fun parseNumberWord(text: String): Int? {
        val numberWords = mapOf(
            "one" to 1, "first" to 1,
            "two" to 2, "second" to 2, "to" to 2,
            "three" to 3, "third" to 3,
            "four" to 4, "fourth" to 4, "for" to 4,
            "five" to 5, "fifth" to 5
        )
        return numberWords.entries.firstOrNull { (word, _) ->
            text.lowercase().contains(word)
        }?.value
    }
}