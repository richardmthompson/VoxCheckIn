package com.example.voxmanifestorapp.ui.agent

import android.util.Log
import com.example.voxmanifestorapp.data.ConceptActionState
import com.example.voxmanifestorapp.data.ConversationRepository
import com.example.voxmanifestorapp.ui.agent.checkin.CheckInStage
import com.example.voxmanifestorapp.ui.agent.checkin.ConversationalTheme
import com.example.voxmanifestorapp.ui.agent.checkin.UserEngagementMetrics
import com.example.voxmanifestorapp.ui.agent.timer.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Manages session context loading and restoration for session continuity.
 * Handles loading conversation history from database and resetting agent state
 * for fresh conversation starts with historical context.
 */
class SessionContextManager(
    private val conversationRepository: ConversationRepository,
    private val agentCortex: AgentCortex,
    private val conversationAgent: ConversationAgent,
    private val scope: CoroutineScope
) {
    private val TAG = "SessionContextManager"

    /**
     * Unloads a currently loaded session from the app's 'working memory' and re-sets the state
     * for a new conversation to begin.
     */

    suspend fun unloadSessionContext(): Boolean {
        return try {
            // reset conversation history
            agentCortex.clearConversationHistory()

            // reset agent state
            resetAgentState()

            // start a new session and update AgentCortex with new sessionId
            val newSessionId = conversationRepository.startNewSession()
            agentCortex.updateSessionId(newSessionId)

            Log.d(TAG, "Session context unloaded. New session ID: $newSessionId")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading session context: ${e.message}", e)
            false
        }
    }
    /**
     * Loads a previous session's context and prepares the agent for resumption.
     * This method:
     * 1. Loads conversation history from the database
     * 2. Extracts themes from conversation metadata
     * 3. Resets agent state to initial state (preserving themes)
     * 4. Loads historical conversation entries into AgentCortex
     * 5. Prepares for fresh check-in process with historical context
     */
    suspend fun loadSessionContext(sessionId: String): Boolean {
        return try {
            Log.d(TAG, "Loading session context for session ID: $sessionId")

            // Update AgentCortex with the loaded session ID
            agentCortex.updateSessionId(sessionId)
            Log.d(TAG, "Updated AgentCortex sessionId to: $sessionId")

            // Load conversation entries from database and process them
            conversationRepository.getSessionEntriesFlow(sessionId)
                .collect { entries ->
                    Log.d(TAG, "Loaded ${entries.size} conversation entries from session $sessionId")

                    // Extract themes from conversation history metadata
                    val extractedThemes = extractThemesFromHistory(entries)
                    Log.d(TAG, "Extracted ${extractedThemes.size} themes from conversation history")

                    // Reset agent state to initial state (preserving themes)
                    resetAgentState(extractedThemes)

                    // Load historical conversation entries into AgentCortex
                    loadConversationHistory(entries)

                    // Log successful context loading
                    Log.d(TAG, "Successfully loaded session context for session $sessionId with ${extractedThemes.size} themes")
                }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading session context for session $sessionId: ${e.message}", e)
            false
        }
    }

    /**
     * Extracts themes from conversation history metadata.
     * Reconstructs comprehensive theme list from all agent responses.
     * Ensures we get the latest, most complete version of each theme.
     */
    private fun extractThemesFromHistory(entries: List<ConversationEntry>): List<ConversationalTheme> {
        Log.d("SessionContextManager", "=== THEME EXTRACTION DEBUG ===")
        Log.d("SessionContextManager", "Extracting themes from ${entries.size} conversation entries")
        
        // Log all entries to see what we're working with
        entries.forEachIndexed { index, entry ->
            Log.d("SessionContextManager", "Entry $index: Speaker=${entry.speaker}, Content=${entry.content.take(50)}...")
            Log.d("SessionContextManager", "Entry $index metadata keys: ${entry.metadata.keys}")
            if (entry.metadata.isNotEmpty()) {
                Log.d("SessionContextManager", "Entry $index metadata: $entry.metadata")
            }
        }
        
        val json = Json { 
            ignoreUnknownKeys = true 
            isLenient = true
            coerceInputValues = true
        }

        // Track the latest version of each theme by title
        val latestThemes = mutableMapOf<String, ConversationalTheme>()

        // Process agent responses in chronological order
        val agentEntries = entries.filter { it.speaker == Speaker.Agent }
        Log.d("SessionContextManager", "Found ${agentEntries.size} agent entries to process for themes")
        
        agentEntries.forEachIndexed { index, entry ->
            Log.d("SessionContextManager", "Processing agent entry $index: ${entry.content.take(50)}...")
            val themesString = entry.metadata["themes"]
            Log.d("SessionContextManager", "Agent entry $index themes string: ${themesString ?: "NULL"}")

            if (!themesString.isNullOrBlank()) {
                Log.d("SessionContextManager", "Parsing themes JSON from entry: ${themesString.take(100)}")
                try {
                    // The metadata now comes as a String from ConversationEntry (converted from JsonElement)
                    // We need to parse it as JSON to get the themes
                    val themes = json.decodeFromString<List<ConversationalTheme>>(themesString)
                    Log.d("SessionContextManager", "Successfully parsed ${themes.size} themes from entry $index")
                    themes.forEach { theme ->
                        // Update with the latest version of this theme
                        // Later entries will overwrite earlier ones for the same title
                        val existingTheme = latestThemes[theme.title]
                        if (existingTheme != null) {
                            Log.d(TAG, "REPLACING older theme '${theme.title}' (${existingTheme.observations.size} obs) with newer version (${theme.observations.size} obs)")
                        } else {
                            Log.d(TAG, "ADDING new theme '${theme.title}' with ${theme.observations.size} observations")
                        }
                        latestThemes[theme.title] = theme
                        Log.d(TAG, "Updated theme '${theme.title}' with ${theme.observations.size} observations from entry: ${entry.content.take(50)}...")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse themes from entry $index: ${e.message}")
                    Log.w(TAG, "Problematic JSON: $themesString")
                }
            } else {
                Log.d("SessionContextManager", "Agent entry $index has no themes in metadata")
            }
        }

        // Convert to final list, ensuring we have the latest version of each theme
        val finalThemes = latestThemes.values.toList()

        Log.d(TAG, "=== THEME EXTRACTION SUMMARY ===")
        Log.d(TAG, "Extracted ${finalThemes.size} unique themes with latest versions")
        finalThemes.forEach { theme ->
            Log.d(TAG, "Theme '${theme.title}': ${theme.observations.size} observations")
        }
        
        return finalThemes
    }

    /**
     * Resets the agent state to initial state for fresh conversation start.
     * This ensures the agent starts with a clean state while preserving
     * the loaded conversation history and extracted themes.
     */
    private fun resetAgentState(extractedThemes: List<ConversationalTheme> = emptyList()) {
        Log.d(TAG, "Resetting agent state for fresh conversation start with ${extractedThemes.size} themes")

        // Reset dialogue state to Idle
        agentCortex.updateDialogueState(DialogueState.Idle)

        // Reset conversation type to null (will be set when check-in starts)
        agentCortex.updateConversationType(null)

        // Reset display state to None
        agentCortex.updateDisplayState(DisplayState.None)

        // Reset core loop state to CHECK_IN phase
        agentCortex.updateCoreLoopState(
            phase = ConversationPhase.CHECK_IN,
            wishIndex = -1,
            understanding = ""
        )

        // Reset check-in state to initial state BUT PRESERVE THEMES
        agentCortex.updateCheckInState(
            agentCortex.checkInState.value.copy(
                currentStage = CheckInStage.OPENING,
                activeThemes = extractedThemes, // ← PRESERVE EXTRACTED THEMES
                engagementMetrics = UserEngagementMetrics(), // Reset engagement metrics
                timerState = TimerState.Inactive
            )
        )

        // Reset conversation plan
        agentCortex.updateConversationPlan(null)

        // Reset current action
        agentCortex.updateCurrentAction(ConceptActionState.INITIATE)

        Log.d(TAG, "Agent state reset complete with ${extractedThemes.size} preserved themes")
    }

    /**
     * Loads conversation history entries into AgentCortex.
     * This preserves the historical context while allowing for fresh conversation flow.
     */
    private fun loadConversationHistory(entries: List<ConversationEntry>) {
        Log.d(TAG, "Loading ${entries.size} conversation entries into AgentCortex")

        // Clear existing conversation history
        agentCortex.clearConversationHistory()

        // Load historical entries into AgentCortex
        entries.forEach { entry ->
            agentCortex.addConversationEntry(entry)
        }

        Log.d(TAG, "Conversation history loaded successfully")
    }

    /**
     * Validates that a session can be resumed.
     * Checks if the session exists and has conversation entries.
     */
    suspend fun canResumeSession(sessionId: String): Boolean {
        return try {
            val sessionInfo = conversationRepository.getAllSessionsInfo(agentCortex.currentSessionId)
                .find { it.sessionId == sessionId }
            
            sessionInfo != null && sessionInfo.entryCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error validating session $sessionId: ${e.message}", e)
            false
        }
    }

    /**
     * Gets a summary of the session for display purposes.
     * Returns basic session information for user confirmation.
     */
    suspend fun getSessionSummary(sessionId: String): SessionSummary? {
        return try {
            val sessionInfo = conversationRepository.getAllSessionsInfo(agentCortex.currentSessionId)
                .find { it.sessionId == sessionId }
            
            sessionInfo?.let {
                SessionSummary(
                    sessionId = it.sessionId,
                    dateTime = it.dateTimeFormatted,
                    entryCount = it.entryCount,
                    isResumable = it.entryCount > 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting session summary for $sessionId: ${e.message}", e)
            null
        }
    }
}

/**
 * Data class representing a session summary for display purposes.
 */
data class SessionSummary(
    val sessionId: String,
    val dateTime: String,
    val entryCount: Int,
    val isResumable: Boolean
) 