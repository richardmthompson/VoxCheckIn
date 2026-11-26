package com.example.voxmanifestorapp.ui.agent.utilities

import com.example.voxmanifestorapp.data.ConceptActionState
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.ConversationEntry
import com.example.voxmanifestorapp.ui.agent.ConversationPhase
import com.example.voxmanifestorapp.ui.agent.Speaker

object HistoryTokenManager {
    private const val APPROXIMATE_CHARS_PER_TOKEN = 4
    // This default is for the function signature, but should typically be overridden by callers
    // with a more context-appropriate limit for the history *segment* of their prompt.
    private const val DEFAULT_TOKEN_LIMIT = 25000

    /**
     * Returns a token-aware subset of conversation history that fits within limits.
     * Maintains the same List<ConversationEntry> type as the input.
     *
     * @param history The full conversation history
     * @param tokenLimit Maximum number of tokens to allow for this specific history segment.
     * @param logger Optional logging function
     * @return A List<ConversationEntry> that fits within token limits
     */
    fun getTokenSafeHistory(
        history: List<ConversationEntry>,
        tokenLimit: Int = DEFAULT_TOKEN_LIMIT,
        logger: ((String, StatusColor) -> Unit)? = null
    ): List<ConversationEntry> {
        if (history.isEmpty()) {
            // No specific log message for empty history in PRD, so just return.
            return history
        }

        // Calculate total tokens based on PRD's method
        val historyWithTokens = history.map { entry ->
            entry to estimateTokens(entry.content)
        }
        val totalOriginalTokens = historyWithTokens.sumOf { it.second }

        // If under limit, return full history
        if (totalOriginalTokens <= tokenLimit) {
            logger?.invoke("🔤 Using full history: $totalOriginalTokens tokens", StatusColor.Default)
            return history
        }

        // Truncation is necessary
        val systemMessageContent = "<History truncated to respect Token Limits of Language Model>"
        // This is an internal, non-visible entry. Using a generic default phase is acceptable
        // and avoids complicating the function signature for this minor, internal use case.
        val systemEntry = ConversationEntry(
            speaker = Speaker.Agent, // Using Agent, but content is clearly marked
            content = systemMessageContent,
            phase = ConversationPhase.CHECK_IN,
            isVisible = false, // Likely hidden from UI if this flag is used for that
            metadata = emptyMap()
        )
        val systemEntryTokens = estimateTokens(systemEntry.content)

        val canSystemEntryFit = tokenLimit >= systemEntryTokens
        val tokenLimitForConversational = if (canSystemEntryFit) tokenLimit - systemEntryTokens else tokenLimit

        var accumulatedConversationalTokens = 0
        val truncatedConversationalEntries = mutableListOf<ConversationEntry>()

        for ((entry, tokens) in historyWithTokens.asReversed()) {
            if (accumulatedConversationalTokens + tokens <= tokenLimitForConversational) {
                truncatedConversationalEntries.add(0, entry)
                accumulatedConversationalTokens += tokens
            } else {
                break
            }
        }

        val conversationalWasTruncated = history.size > truncatedConversationalEntries.size

        if (canSystemEntryFit && conversationalWasTruncated) {
            val totalTokensWithSystemNote = accumulatedConversationalTokens + systemEntryTokens
            val removedEntriesCount = history.size - truncatedConversationalEntries.size
            logger?.invoke(
                "🔤 History truncated. System note added. Conv: $accumulatedConversationalTokens, SysNote: $systemEntryTokens (Total: $totalTokensWithSystemNote). Removed $removedEntriesCount msgs.",
                StatusColor.Pause
            )
            return listOf(systemEntry) + truncatedConversationalEntries
        } else {
            // Fallback to original truncation if system note can't fit or wasn't needed
            // This ensures we always try to return some conversational history if possible.
            // If canSystemEntryFit was false, truncatedConversationalEntries was already truncated against the original tokenLimit.
            // If conversationalWasTruncated was false, it means after reserving for system note, all history fit, so no sys note needed.
            val finalEntries = if (!canSystemEntryFit) {
                 // Recalculate truncation if system note cannot fit, using full tokenLimit for conversation
                var fallbackTokens = 0
                val fallbackEntries = mutableListOf<ConversationEntry>()
                for ((entry, tokens) in historyWithTokens.asReversed()) {
                    if (fallbackTokens + tokens <= tokenLimit) {
                        fallbackEntries.add(0, entry)
                        fallbackTokens += tokens
                    } else {
                        break
                    }
                }
                logger?.invoke(
                    "🔤 History truncated (standard, sys note didn't fit). Total: $fallbackTokens tokens. Removed ${history.size - fallbackEntries.size} msgs.",
                    StatusColor.Pause
                )
                fallbackEntries
            } else {
                 // This means system note could fit, but conversational history wasn't truncated after reserving space for it.
                 // So, we don't need the system note, just return the (potentially) full history up to tokenLimit.
                 logger?.invoke(
                    "🔤 History token check: All conversational entries fit after reserving for sys note ($accumulatedConversationalTokens tokens). No truncation note needed. Removed ${history.size - truncatedConversationalEntries.size} msgs.",
                    StatusColor.Default // Or Pause if history.size > truncatedConversationalEntries.size
                )
                truncatedConversationalEntries
            }
            return finalEntries
        }
    }

    /**
     * Estimates tokens using character-based approximation.
     */
    private fun estimateTokens(text: String): Int {
        // Ensure non-negative token count, especially for empty strings.
        if (text.isEmpty()) return 0
        return text.length / APPROXIMATE_CHARS_PER_TOKEN
    }
}
