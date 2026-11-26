package com.example.voxmanifestorapp.ui.agent.utilities

import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.AgentCortex
import com.example.voxmanifestorapp.ui.agent.DialogueState
import com.example.voxmanifestorapp.ui.agent.NetworkException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles errors from brain service calls, whether returned as Result.failure or thrown as exceptions
 * Centralizes the error handling logic to avoid duplication across the agent system.
 */
suspend fun handleError(
    error: Throwable,
    onFailure: (suspend (Throwable) -> Unit)?,
    toggleConversation: suspend (Boolean) -> Unit,
    speak: suspend (String) -> Unit,
    updateDialogueState: (DialogueState) -> Unit,
    agentCortex: AgentCortex,
    logger: (String, StatusColor) -> Unit
) {
    // Ignore CancellationException - it's not an error but normal cancellation behavior
    if (error is CancellationException) {
        logger("HBSC: Ignoring CancellationException as it's a normal cancellation: ${error.message}", StatusColor.Default)
        return
    }
    
    if (error is NetworkException) {
        logger("HBSC: NetworkException detected. Standard network error handling commencing.", StatusColor.Stop)

        // if we got here, we're in a conversation. so we need to end it.
        // Pass true to indicate this is a network error situation
        toggleConversation(true)
        
        // Add detailed state logging after toggleBrainConversation
        logger("POST-TOGGLE: DialogueState=${agentCortex.dialogueState.value}", StatusColor.Stop)
        logger("POST-TOGGLE: conversationScope is null", StatusColor.Stop)
        
        // Force state reset if still not Idle
        if (agentCortex.dialogueState.value != DialogueState.Idle) {
            logger("POST-TOGGLE: DialogueState not Idle! Forcing reset...", StatusColor.Stop)
            updateDialogueState(DialogueState.Idle)
        }

    } else {
        logger("HBSC: Non-NetworkException. Type: ${error::class.simpleName}", StatusColor.Stop)
        // For other errors, use custom handler if provided
        if (onFailure != null) {
            logger("HBSC: Invoking custom onFailure handler.", StatusColor.Stop)
            onFailure(error)
        } else {
            // Default error handling
            logger("HBSC: Using default error handling for: ${error.message}", StatusColor.Stop)
            speak("I ran into a problem. Let's try again.")
            updateDialogueState(DialogueState.Idle)
        }
    }
}
