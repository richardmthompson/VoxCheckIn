package com.example.voxmanifestorapp.ui.agent.utilities

import com.example.voxmanifestorapp.ui.agent.ConversationEntry
import com.example.voxmanifestorapp.ui.agent.Speaker

/**
 * A utility object for formatting conversation history into a string suitable for LLM prompts.
 */
object HistoryFormatter {

    /**
     * Formats a list of conversation entries into a single string.
     *
     * @param history The list of `ConversationEntry` objects to format.
     * @param agentName The name to use for the agent's speaker role (e.g., "Coach").
     * @param emptyHistoryMessage The message to return if the history is empty.
     * @return A formatted string representing the conversation history.
     */
    fun format(
        history: List<ConversationEntry>,
        agentName: String = "Coach",
        emptyHistoryMessage: String = "No conversation has yet occurred. This is the very beginning of the check-in."
    ): String {
        if (history.isEmpty()) {
            return emptyHistoryMessage
        }

        return history.joinToString(separator = "\n\n") { entry ->
            val speaker = when (entry.speaker) {
                Speaker.User -> "User"
                Speaker.Agent -> agentName
                else -> entry.speaker.toString()
            }
            "$speaker: ${entry.content}"
        }
    }
}
