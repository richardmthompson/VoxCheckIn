package com.example.voxmanifestorapp.ui.agent.checkin

import android.util.Log
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.AgentCortex
import com.example.voxmanifestorapp.ui.agent.BrainService
import com.example.voxmanifestorapp.ui.agent.ConversationEntry
import com.example.voxmanifestorapp.ui.agent.Speaker
import com.example.voxmanifestorapp.ui.agent.utilities.HistoryFormatter
import com.example.voxmanifestorapp.ui.agent.utilities.HistoryTokenManager
import com.example.voxmanifestorapp.ui.agent.utilities.WishSummary
import com.example.voxmanifestorapp.ui.agent.timer.TimerState

const val TAG = "DialogueChain"

/**
 * DialogueChain defines the contract for the check-in dialog orchestration.
 * Provides entry points for transition evaluation and response generation.
 */
interface DialogueChain {
    /**
     * Evaluates whether to transition from check-in phase.
     * This is Chain 1 of the dialogue process - lightweight evaluation with basic wish data.
     *
     * @param userWishes List of user wishes to provide context for theme extraction
     * @return TransitionEvaluationResult indicating whether to transition and extracted themes
     */
    suspend fun evaluateTransition(userWishes: List<WishSummary> = emptyList()): TransitionEvaluationResult

    /**
     * Continues the check-in conversation with response generation.
     * This is Chain 2 of the dialogue process - generates conversational response.
     *
     * @param evaluationResult The result from evaluateTransition containing themes and context
     * @return Response object with the conversational response data
     */
    // have allowed userWishes to be optional, as in the MVP, we are not dealing with user's wishes at all.
    suspend fun continueCheckIn(evaluationResult: TransitionEvaluationResult, userWishes: List<WishSummary> = emptyList()): Response
}

/**
 * Concrete implementation of DialogueChain for the check-in phase.
 * Orchestrates transition evaluation, strategy selection, and response generation.
 * Uses LLM via BrainService and provides robust fallback logic.
 */
class CheckInDialogueChain(
    private val brainService: BrainService,
    private val agentCortex: AgentCortex,
    private val logger: (String, StatusColor) -> Unit
) : DialogueChain {

    /**
     * Evaluates whether to transition from check-in phase.
     * This is Chain 1 of the dialogue process - lightweight evaluation with basic wish data.
     *
     * @param userWishes List of user wishes to provide context for theme extraction
     * @return TransitionEvaluationResult indicating whether to transition and extracted themes
     */
    override suspend fun evaluateTransition(userWishes: List<WishSummary>): TransitionEvaluationResult {
        logger("🔄 CHECK-IN EVALUATE: Initiating transition evaluation with direct state access", StatusColor.Default)

        // Access current state directly from AgentCortex
        val currentCheckInState = agentCortex.checkInState.value
        val currentHistory = agentCortex.conversationHistory.value

        logger("🔄 CHECK-IN EVALUATE: Accessed current state - History: ${currentHistory.size} entries, Themes: ${currentCheckInState.activeThemes.size}", StatusColor.Default)

        // Compute updated metrics based on the latest user response
        val updatedMetrics = computeMetricsFromHistory(currentHistory, currentCheckInState.engagementMetrics)

        // --- Step 1A: Evaluate Transition (Chain 1) ---
        val transitionDecision = evaluateTransitionDecision(updatedMetrics)

        // --- Step 1B: Extract Themes (Chain 1B) ---
        logger("🔄 CHECK-IN EVALUATE: Extracting themes from conversation", StatusColor.Default)
        Log.d("DialogueChain", "🔍 [CHAIN 1B - THEMES] examining user message: ${currentHistory.lastOrNull { it.speaker == Speaker.User }?.content}")
        val themesFromCurrentUserMessage = extractThemesFromHistory(userWishes)

        Log.d("DialogueChain", "🔍 [CHAIN 1B - THEMES] Themes from current user message: $themesFromCurrentUserMessage")

        // Combine themes for this turn
        val combinedThemesForThisTurn = (currentCheckInState.activeThemes + themesFromCurrentUserMessage).distinctBy { it.title }

        logger("🔄 CHECK-IN EVALUATE: Evaluation complete. Should transition: ${transitionDecision.shouldTransition}", StatusColor.Default)

        // Return only the computed new data (no context wrapper)
        return TransitionEvaluationResult(
            shouldTransition = transitionDecision.shouldTransition,
            extractedThemes = combinedThemesForThisTurn,
            updatedMetrics = updatedMetrics,
            transitionReasoning = transitionDecision.reasoning
        )
    }

    /**
     * Continues the check-in conversation with response generation.
     * This is Chain 2 of the dialogue process - generates conversational response.
     *
     * @param evaluationResult The result from evaluateTransition containing themes and context
     * @return Response object with the conversational response data
     */
    override suspend fun continueCheckIn(evaluationResult: TransitionEvaluationResult, userWishes: List<WishSummary>): Response {
        logger("🔄 CHECK-IN CONTINUE: Generating conversational response with direct state access", StatusColor.Default)

        logger("🔄 CHECK-IN CONTINUE: Augmented context. Combined themes count: ${evaluationResult.extractedThemes.size}", StatusColor.Default)

        // Detailed theme logging before moving to response generation
        if (evaluationResult.extractedThemes.isNotEmpty()) {
            logger("🔍 UPDATED THEME DETAILS:", StatusColor.Default)
            evaluationResult.extractedThemes.forEachIndexed { index, theme ->
                logger("  Theme ${index+1}: \"${theme.title}\"", StatusColor.Default)
                theme.observations.forEachIndexed { obsIndex, observation ->
                    logger("    - Observation ${obsIndex+1}: \"$observation\"", StatusColor.Default)
                }
            }
        } else {
            logger("🔍 THEME DETAILS: No active themes detected before response generation", StatusColor.Default)
        }

        // --- Generate Response (Orchestrating Chain 2 and Chain 3) ---
        val response = generateStrategyAndResponse(userWishes)

        // lets do some logging to find out the reasoning of the response
        Log.d("DialogueChain", "🔍 [CHAIN 3 - RESPONSE] Reasoning: ${response.reasoning}")

        logger("🔄 CHECK-IN CONTINUE: Response generation complete.", StatusColor.Go)
        return response
    }

    /**
     * Helper method that generates a response with the given strategy using direct AgentCortex access.
     *
     * @param strategy The selected conversation strategy.
     * @param userWishes User wishes for context.
     * @return The generated response.
     */
    private suspend fun generateResponse(strategy: Strategy, userWishes: List<WishSummary>): Response {
        // Generate the prompt for response generation
        val responsePrompt = buildResponsePrompt(strategy, userWishes)

        // Console logging for debugging
        Log.d("DialogueChain", "🔍 [CHAIN 3 - RESPONSE] Calling brainService.generateCheckInResponse()")
        Log.d("DialogueChain", "🔍 [CHAIN 3 - RESPONSE] Prompt length: ${responsePrompt.length} chars")
        Log.d("DialogueChain", "🔍 [CHAIN 3 - RESPONSE] Prompt: $responsePrompt")
        Log.d("DialogueChain", "🔍 [CHAIN 3 - RESPONSE] =====================================")

        // Get response from brain service
        val responseResult = brainService.generateCheckInResponse(responsePrompt)
        return responseResult.getOrThrow() // Propagate any exceptions
    }


    /**
     * Computes updated metrics based on conversation history and current metrics.
     * Replaces computeMetricsFromContext() to work with direct state access.
     *
     * @param history Current conversation history
     * @param currentMetrics Current engagement metrics
     * @return Updated metrics including latest user response
     */
    private fun computeMetricsFromHistory(
        history: List<ConversationEntry>,
        currentMetrics: UserEngagementMetrics
    ): UserEngagementMetrics {
        val latestUserMessage = history.lastOrNull { it.speaker == Speaker.User }?.content

        // If no user message, return current metrics unchanged
        if (latestUserMessage.isNullOrBlank()) {
            return currentMetrics
        }

        // Calculate updated metrics based on the new user response
        val words = latestUserMessage.trim().split(" ").filter { it.isNotBlank() }.size
        val isShort = words < 10
        val newShortResponseCounter = if (isShort) currentMetrics.shortResponseCounter + 1 else 0

        return currentMetrics.copy(
            responseHistory = currentMetrics.responseHistory + words,
            shortResponseCounter = newShortResponseCounter
        )
    }

    /**
     * Helper method to extract all user messages since the last agent response.
     * This defines the current conversational turn for theme extraction.
     * 
     * @param history The complete conversation history
     * @return List of user messages in the current turn
     */
    private fun getUserMessagesSinceLastAgentResponse(history: List<ConversationEntry>): List<ConversationEntry> {
        val lastAgentIndex = history.indexOfLast { it.speaker == Speaker.Agent }
        return if (lastAgentIndex >= 0) {
            // Extract user messages after the last agent response
            history.drop(lastAgentIndex + 1).filter { it.speaker == Speaker.User }
        } else {
            // No agent messages yet, take all user messages (first turn)
            history.filter { it.speaker == Speaker.User }
        }
    }

    /**
     * Extracts themes from all user messages in the current conversational turn.
     * Implements Chain 1B for theme extraction using direct state access.
     *
     * @param userWishes User wishes for context
     * @return List of extracted themes from the current conversational turn.
     */
    private suspend fun extractThemesFromHistory(
        userWishes: List<WishSummary>
    ): List<ConversationalTheme> {
        // Get all user messages from the current conversational turn using direct access
        val history = agentCortex.conversationHistory.value
        val existingThemes = agentCortex.checkInState.value.activeThemes
        val userMessagesInTurn = getUserMessagesSinceLastAgentResponse(history)

        logger("🧠 THEME EXTRACTION: Processing ${userMessagesInTurn.size} user messages from current turn", StatusColor.Default)
        userMessagesInTurn.forEachIndexed { index, entry ->
            logger("🧠 THEME EXTRACTION: Message ${index + 1}: ${entry.content}", StatusColor.Default)
        }

        if (userMessagesInTurn.isEmpty()) {
            logger("🧠 THEME EXTRACTION: No user messages in current turn to extract themes from", StatusColor.Default)
            return emptyList()
        }

        logger("🧠 THEME EXTRACTION: Including ${userWishes.size} user wishes for context", StatusColor.Default)
        val themeExtractionPrompt = buildThemeExtractionPrompt(userMessagesInTurn, existingThemes, userWishes)

        // Console logging for debugging
        Log.d("DialogueChain", "🔍 [CHAIN 1B - THEMES] Calling brainService.extractThemesFromCurrentUserMessage()")
        Log.d("DialogueChain", "🔍 [CHAIN 1B - THEMES] Prompt length: ${themeExtractionPrompt.length} chars")
        Log.d("DialogueChain", "🔍 [CHAIN 1B - THEMES] =====================================")

        val themeExtractionResult = brainService.extractThemesFromCurrentUserMessage(themeExtractionPrompt)

        // Use getOrThrow() to propagate any exception (NetworkException or others)
        return themeExtractionResult.getOrThrow()
    }

    /**
     * Evaluates whether to transition from the check-in phase using hard rules only (MVP).
     *
     * MVP CONFIGURATION: For stable MVP release, only hard metrics are used for transition decisions:
     * 1. Timer expiry (highest priority)
     * 2. Emergency ejection: 4+ consecutive short responses + minimum exchanges
     *
     * POST-MVP: LLM evaluation code is preserved below (commented out) for future restoration
     * when conversational transition analysis is re-enabled.
     *
     * Returns a TransitionDecision with reasoning.
     */
    private suspend fun evaluateTransitionDecision(metrics: UserEngagementMetrics): TransitionDecision {
        logger("🔄 MVP HARD RULES: Starting hard rules only transition evaluation", StatusColor.Default)

        // 0. Timer check - This is the highest priority transition rule.
        val timerState = agentCortex.checkInState.value.timerState
        if (timerState is TimerState.Expired) {
            logger("🔄 MVP HARD RULES: Timer has expired, forcing transition.", StatusColor.Go)
            return TransitionDecision(true, "The 3-minute check-in period has ended.")
        }

        // 1. Hard Rules Check - Emergency ejection based on engagement metrics
        val history = agentCortex.conversationHistory.value
        val exchanges = history.count { it.speaker == Speaker.User }
        val hardRulesResult = shouldTransitionToNextStage(metrics, exchanges)

        if (hardRulesResult) {
            logger("🔄 MVP HARD RULES: Hard rules triggered transition", StatusColor.Go)
            return TransitionDecision(true, "Emergency ejection: ${metrics.shortResponseCounter} consecutive short responses detected")
        }

        // MVP: Continue check-in if hard rules don't trigger transition
        logger("🔄 MVP HARD RULES: Hard rules didn't trigger, continuing check-in", StatusColor.Default)
        return TransitionDecision(false, "Continuing check-in conversation - timer active, engagement metrics normal")

        /* POST-MVP: LLM Evaluation Code (PRESERVED FOR FUTURE RESTORATION)
        // 2. LLM Evaluation (existing logic)
        logger("🔄 HYBRID: Hard rules didn't trigger, consulting LLM...", StatusColor.Default)

        // Build prompt with direct access to AgentCortex data
        val prompt = buildTransitionPrompt()

        // Console logging for debugging
        Log.d("DialogueChain", "🔍 [CHAIN 1 - TRANSITION] Calling brainService.getCheckInEvaluation()")
        Log.d("DialogueChain", "🔍 [CHAIN 1 - TRANSITION] Prompt length: ${prompt.length} chars")
        Log.d("DialogueChain", "🔍 [CHAIN 1 - TRANSITION] =====================================")

        val result = brainService.getCheckInEvaluation(prompt)

        // Use getOrThrow() to propagate any exception from BrainService.
        return result.getOrThrow()
        */
    }

    /**
     * Determines if the conversation should transition to the next stage based on hard rules.
     * This is a pure function with no side effects.
     *
     * @param metrics The current user engagement metrics.
     * @param exchanges The total number of user exchanges in the conversation.
     * @return True if hard rules for transition are met, false otherwise.
     */
    private fun shouldTransitionToNextStage(metrics: UserEngagementMetrics, exchanges: Int): Boolean {
        logger("🔄 CHECK-IN HELPER: Checking hard transition rules...", StatusColor.Default)
        logger("🔄 CHECK-IN HELPER: Current metrics - shortCount=${metrics.shortResponseCounter}, totalResponses=${metrics.responseHistory.size}", StatusColor.Default)
        logger("🔄 CHECK-IN HELPER: Total exchanges so far: $exchanges", StatusColor.Default)
        
        // PRD Requirements: 4+ consecutive short responses (<10 words) + minimum exchanges
        val emergencyEjectionTriggered = metrics.shortResponseCounter >= 4
        val hasMinimumExchanges = exchanges >= 3
        
        // Hard rules: emergency ejection based on absolute thresholds
        val shouldTransition = emergencyEjectionTriggered && hasMinimumExchanges
        
        logger("🔄 CHECK-IN HELPER: Transition decision: emergencyEjection=$emergencyEjectionTriggered (${metrics.shortResponseCounter}/4 short responses), " +
               "exchanges=$exchanges (minimum=$hasMinimumExchanges), final=$shouldTransition", StatusColor.Pause)
        
        return shouldTransition
    }



    /**
     * Generates a contextually appropriate response incorporating themes.
     * Orchestrates strategy selection (Chain 2) and response generation (Chain 3).
     *
     * @param conversationHistory The conversation history for context
     * @param checkInState The current check-in state with themes
     * @return The generated response, including reasoning and strategy used.
     */
    private suspend fun generateStrategyAndResponse(userWishes: List<WishSummary>): Response {
        logger("🗣️ RESPONSE GEN: Selecting strategy and generating response with themes", StatusColor.Default)

        // Step 1: Strategy Selection (Chain 2)
        val strategySelectionPrompt = buildStrategySelectionPrompt()

        // Console logging for debugging
        Log.d("DialogueChain", "🔍 [CHAIN 2 - STRATEGY] Calling brainService.selectConversationStrategyWithThemes()")
        Log.d("DialogueChain", "🔍 [CHAIN 2 - STRATEGY] Prompt length: ${strategySelectionPrompt.length} chars")
        Log.d("DialogueChain", "🔍 [CHAIN 2 - STRATEGY] =====================================")

        // Use getOrThrow() for strategy selection
        val strategyResult = brainService.selectConversationStrategyWithThemes(strategySelectionPrompt)
        val strategySelection = strategyResult.getOrThrow() // Propagates any exception

        logger("🔍 [CHAIN 2 - STRATEGY] Strategy selected! ${strategySelection.strategy.name}", StatusColor.Go)

        // Step 2: Response Generation (Chain 3)
        val response = generateResponse(strategySelection.strategy, userWishes)

        logger("🗣️ RESPONSE GEN: Full response generation complete. Responding to user.", StatusColor.Go)
        return response
    }

    /**
     * Builds the prompt for strategy selection using direct access to AgentCortex data.
     * @return The prompt string for the LLM.
     */
    private fun buildStrategySelectionPrompt(): String {
        // Construct prompt components
        val systemInstructions = Prompts.systemInstructions

        val conversationHistory = agentCortex.conversationHistory.value
        val checkInState = agentCortex.checkInState.value
        val safeHistory = HistoryTokenManager.getTokenSafeHistory(conversationHistory, tokenLimit = 2000, logger = logger)
        val historyText = HistoryFormatter.format(history = safeHistory)

        val themesText = if (checkInState.activeThemes.isEmpty()) {
            "No themes have been identified yet."
        } else {
            checkInState.activeThemes.joinToString(separator = "\n") { theme ->
                "- ${theme.title}: ${theme.observations.joinToString(", ")}"
            }
        }

        val strategiesList = Strategy.values().joinToString(separator = "\n") { strategy ->
            "- ${strategy.name}: ${strategy.description}"
        }

        val strategySelectionPrompt = """

            STRATEGY SELECTION TASK
            =======================
            Your task is to analyze the conversation context and active themes to select the most appropriate coaching conversation strategy for the assistant's next response.
            Sometimes the user's response will be minimal or uncertain, .e.g. "don't know," "I'm not sure," "not really", "uh, yeah dunno," etc.
            In this situation, it usually indicates that the current topic or line of questioning isn't working.  In this case, you should switch strategy, AND try to pick up on another theme or topic that was mentioned earlier in the conversation.

            RECENT CONVERSATION (most recent first):
            $historyText

            ACTIVE THEMES:
            $themesText

            AVAILABLE CONVERSATION STRATEGIES:
            $strategiesList

            SELECTION CRITERIA:
            1. What is the user's current emotional state based on their message?
            2. What themes are most active or significant in the conversation right now?
            3. What stage of the conversation are we in?
            4. Which strategy would most effectively respond to their current need?
            5. How can we build on themes to deepen understanding and clarity?

            Respond with a JSON object in this format:
            {
            "strategy": "STRATEGY_NAME",
            "reasoning": "Brief explanation in 25 words or less of why you selected this strategy based on context and themes"
            }

            DO NOT include any text before or after the JSON object.
            """.trimIndent()

            val finalPrompt = """
                $systemInstructions
                $strategySelectionPrompt
            """.trimIndent()

        return finalPrompt
    }


    /**
     * Builds the prompt for response generation using direct access to AgentCortex data.
     * @param strategy The selected conversation strategy.
     * @param userWishes User wishes for context.
     * @return The prompt string for the LLM.
     */
    private fun buildResponsePrompt(strategy: Strategy, userWishes: List<WishSummary>): String {
        val systemInstructions = Prompts.systemInstructions

        val conversationHistory = agentCortex.conversationHistory.value
        val checkInState = agentCortex.checkInState.value
        val safeHistory = HistoryTokenManager.getTokenSafeHistory(conversationHistory, tokenLimit = 2000, logger = logger)
        val historyText = HistoryFormatter.format(history = safeHistory)

        val themesText = if (checkInState.activeThemes.isEmpty()) {
            "No themes have been identified yet."
        } else {
            checkInState.activeThemes.joinToString(separator = "\n") { theme ->
                "- ${theme.title}: ${theme.observations.joinToString(", ")}"
            }
        }

        // Format user wishes section
        val userWishesText = if (userWishes.isEmpty()) {
            "No active wishes defined yet."
        } else {
            userWishes.joinToString(separator = "\n") { wish ->
                "- Wish #${wish.id}: ${wish.title}"
            }
        }

        val examplesText = if (strategy == Strategy.CONVERSATION_STARTING) {
            val randomExample = CoachingTranscripts.CONVERSATION_STARTING_EXAMPLES.random()
            "If this is the first question you are asking the user, YOU MUST USE THIS EXACT QUESTION:\n\n${randomExample.content}"
        } else {
            val examples = CoachingTranscripts.getExamplesForStrategy(strategy)
            if (examples.isNotEmpty()) {
                "EXAMPLE COACHING QUESTIONS:\n\n" + examples.joinToString(separator = "\n\n") { ex ->
                    "Example: ${ex.title}\n${ex.content}"
                }
            } else {
                ""
            }
        }

        val responsePrompt = """
            RESPONSE GENERATION TASK
            ========================
            GENERAL GUIDELINE FOR CHECK-IN PHASE: The primary purpose of the check-in is to facilitate voice journaling and identify current themes from the user's spontaneous input. While knowledge of the user's wishes is provided for your contextual understanding, you must avoid steering the conversation towards wish-specific discussions or mentioning wishes directly if the user has not done so. Such discussions are reserved for later phases of the Core Loop after the check-in is complete. Your initial utterances, in particular, should focus on open-ended engagement and establishing rapport, not on the user's pre-defined wishes.

            Generate a coaching-style response to the user's message using the selected conversation strategy and incorporating relevant themes.

            SELECTED STRATEGY: ${strategy.name}
            STRATEGY EXPLANATION: ${strategy.description}

            ACTIVE THEMES:
            $themesText
            
            USER'S ACTIVE WISHES/GOALS:
            $userWishesText

            $examplesText

            YOUR RESPONSE MUST:
            1. Incorporate relevant themes from the conversation
            2. Use the selected strategy as your primary approach
            3. Internally consider the user's active wishes to enrich your understanding and ensure your response is contextually relevant. **However, during this check-in phase, do not explicitly mention or discuss the user's specific wishes unless the user brings them up first.** The focus is on the current check-in conversation and themes emerging from it.
            4. Be focused on helping the user gain clarity through reflection
            5. When asking questions (especially in initial utterances), use open-ended questions that encourage elaboration rather than closed questions. For example:
               - AVOID closed questions like: "How are you feeling?" or "Are you doing well?" (these invite one-word answers)
               - INSTEAD USE open-ended questions like: "What's been on your mind lately?" or "Tell me about what you've been experiencing recently." or "What aspects of [relevant theme] have you been noticing?"
               - When referring to specific themes, frame questions to invite exploration: "What's your experience been with [theme]?" rather than "How do you feel about [theme]?"

            Respond with a JSON object in this format:
            {
                "response": "Your complete response text",
                "reasoning": "Brief explanation in 25 words or less of how your response applies the strategy and incorporates themes",
                "strategy": "${strategy.name}"
            }

            DO NOT include any text before or after the JSON object.
            """.trimIndent()

        val finalPrompt = """
            $systemInstructions
            $historyText
            $responsePrompt
        """.trimIndent()

        return finalPrompt
    }

    /* POST-MVP: LLM Transition Prompt Builder (PRESERVED FOR FUTURE RESTORATION)
     *
     * This method builds the prompt for LLM-based transition evaluation.
     * Currently disabled for MVP to use only hard rules for transition decisions.
     * Will be re-enabled post-MVP when conversational transition analysis is restored.
     *
     * @return The prompt string for the LLM.
     */
    private fun buildTransitionPrompt(): String {
        // let's construct our prompt components first, then we'll assemble the final prompt later
        val systemInstructions = Prompts.systemInstructions

        val history = agentCortex.conversationHistory.value
        val safeHistory = HistoryTokenManager.getTokenSafeHistory(history, tokenLimit = 4000, logger = logger)
        val historyText = HistoryFormatter.format(history = safeHistory)
        Log.d("DialogueChain", "🔍 [CHAIN 1 - TRANSITION] Chat History : \n${historyText}")

        val transitionEvaluationPrompt = """

            TRANSITION EVALUATION TASK
            =========================
            As a manifestation coaching assistant, analyze whether the CHECK_IN phase should transition 
            to specific manifestation work that the user has explicitly requested, or continue the 
            check-in conversation for deeper reflection and clarity building.

            MANIFESTATION CONTEXT:
            The Check-In serves the Clarity Building Phase (Steps 5-8) of the manifestation process:
            "Through regular conversations, a person can become clearer... ongoing descriptions of what 
            one enjoys and what one does not, can give rise to useful insights about what one truly wants."

            Your role is to facilitate this clarity-building through voice journaling until the user 
            explicitly requests to move into structured manifestation work.

            CONVERSATION HISTORY:
            $historyText

            CORE LOOP TRANSITION TARGETS:
            Only transition if user explicitly requests these manifestation functions:

            1. **WISH_COLLECTION** - User wants to "work on wishes", "create goals", "define manifestations", 
               "review my wishes", "what do I want to manifest"

            2. **PRESENT_STATE_EXPLORATION** - User wants to explore "where I am now", "current situation", 
               "present state", "how things are currently"

            3. **DESIRED_STATE_EXPLORATION** - User wants to explore "where I want to be", "desired state", 
               "my ideal future", "how I want things to be"

            4. **CONCEPT_BUILDING** - User wants to work on "present vs desired state", "gap analysis", 
               "contrast between where I am and want to be"

            5. **AFFIRMATION_PROCESS** - User wants "affirmations", "positive statements", "manifestation practice", 
               "help me believe in my goals"

            6. **GENERAL_PROGRESSION** - User says "what's next", "move on", "ready for the next step", 
               "let's work on something specific"

            TRANSITION CRITERIA (ALL must be met):
            ✓ User has explicitly requested one of the above manifestation functions
            ✓ User indicates readiness to move beyond general reflection to structured work
            ✓ The request is clear and specific (not just general conversation)

            DO NOT TRANSITION IF:
            ✗ No conversation has occurred yet (always continue check-in)
            ✗ User is still in voice journaling mode - sharing feelings, experiences, thoughts
            ✗ User is exploring general topics without requesting specific manifestation work
            ✗ The check-in conversation is still providing clarity and connection value
            ✗ User seems to need more space for reflection before structured work

            MANIFESTATION PRINCIPLE:
            "Regular and ongoing discussions" are essential for users to gain clarity on their desires.
            The Check-In should continue until the user naturally expresses readiness for specific 
            manifestation work. Premature transitions interrupt the valuable clarity-building process.

            Respond with a JSON object in this format:
            {
            "shouldTransition": boolean,
            "reasoning": "Explain your decision in 25 words or less - what specific request was detected or why continuing check-in"
            }

            DO NOT include any text before or after the JSON object.

""".trimIndent()

        val finalPrompt = """
            $systemInstructions
            $transitionEvaluationPrompt
        """.trimIndent()
        return finalPrompt
    }

    /**
     * Builds the prompt for theme extraction from all user messages in the current turn.
     * @param userMessagesInTurn List of user messages from the current conversational turn.
     * @param existingThemes List of themes identified in previous turns.
     * @param userWishes List of user wishes to provide context.
     * @return The prompt string for the LLM.
     */
    private fun buildThemeExtractionPrompt(
        userMessagesInTurn: List<ConversationEntry>, 
        existingThemes: List<ConversationalTheme>,
        userWishes: List<WishSummary> = emptyList()
    ): String {
        val systemInstructions = Prompts.systemInstructions
        
        val existingThemesText = if (existingThemes.isEmpty()) {
            "No themes have been identified yet."
        } else {
            existingThemes.joinToString(separator = "\n") { theme ->
                "- ${theme.title}: ${theme.observations.joinToString(", ")}"
            }
        }
        
        // Format user wishes section
        val userWishesText = if (userWishes.isEmpty()) {
            "No active wishes defined yet."
        } else {
            userWishes.joinToString(separator = "\n") { wish ->
                "- Wish #${wish.id}: ${wish.title}"
            }
        }
        
        // Format all user messages in the current turn
        val userMessagesText = if (userMessagesInTurn.isEmpty()) {
            "No user messages in current turn."
        } else {
            userMessagesInTurn.mapIndexed { index, entry ->
                "Message ${index + 1}: \"${entry.content}\""
            }.joinToString(separator = "\n")
        }
        
        val themeExtractionPrompt = """

            THEME EXTRACTION TASK
            ====================
            As a manifestation coaching assistant, identify key conversational themes in the user's messages
            from their current conversational turn. These themes will help maintain context
            and provide insights for personalized responses.

            USER'S MESSAGES IN CURRENT TURN:
            $userMessagesText

            EXISTING THEMES IDENTIFIED SO FAR:
            $existingThemesText
            
            USER'S ACTIVE WISHES/GOALS:
            $userWishesText

            WHAT CONSTITUTES A RELEVANT THEME:
            A relevant theme is a topic, feeling, situation, challenge, aspiration, belief, or value that is *personal to the user and their life experiences, and directly relates to their journey of self-discovery and manifestation*. Themes should reflect the user's inner world or their interaction with their external world in a way that impacts their well-being or goals. These are insights that help the user become clearer about what they want and what obstacles they face, as outlined in the manifestation process.

            MANIFESTATION COACHING CONTEXT:
            Focus on themes that relate to:
            - The user's current situation or challenges (Present State)
            - Their desired outcomes or aspirations (Desired State)
            - Beliefs or emotions that may impact their manifestation journey
            - Areas of their life they want to transform or improve
            - Personal values or priorities emerging in the conversation
            - Any wider theme that the user seems preoccupied or concerned with.
            - Connections to their existing wishes, if any

            WHAT TO AVOID AS THEMES (DO NOT EXTRACT THESE):
            - Meta-comments: Do not extract themes about the application itself, the AI assistant, the check-in process, the conversation structure, or any technical aspects of the interaction. For example, if the user says "This check-in is helpful" or "The app is listening well", these are NOT personal themes for manifestation.
            - Generic conversation fillers: Skip phrases like "okay," "alright," "I see," or simple confirmations unless they are deeply embedded in an expression of personal feeling or experience.
            - Abstract or overly broad concepts unless directly tied to a specific personal experience shared by the user. For example, "philosophy" is not a theme unless the user is discussing their *personal philosophy* and how it impacts their life.
            - Questions posed *by the user* to the assistant, unless the question itself reveals a deep personal concern or area of exploration for the user.

            EXTRACTION INSTRUCTIONS:
            1. For each NEW theme identified in the current message:
               - Create a concise, descriptive title (e.g., "Work-Life Balance", "Sleep", "Relationship-Seeking", etc.)  make it as specific as possible, e.g. use "Need to be proactive with boss at work" rather than "Need to be proactive"
               - Add 1-3 specific observations from the user's message that support this theme.
               - Make the theme observations comprensible by themselves, atomic, and concise.  For example, rather than "The user is feeling overwhelmed by work because they have a lot of work to do", two observations would be better: "The user is feeling overwhelmed by work"; "The user has a lot of work to do".
            
            2. For EXISTING themes that appear again:
               - DO NOT create a duplicate theme
               - Instead, note how the current message relates to this existing theme
            
            3. Focus on substantive themes:
               - Skip pleasantries or conversation mechanics
               - Prioritize themes that appear meaningful to the user's manifestation journey
               - Look for emotional significance, repeated mentions, or emphasis
               - Consider how themes might relate to the user's active wishes
               - Ensure themes are *about the user\'s personal life, experiences, or internal state* and contribute to understanding their manifestation journey. Avoid themes that are about the app, the AI, or the process of conversation itself.

            - When identifying themes, consider how they might relate to the user's existing wishes. This is for your internal understanding and to help you formulate more relevant themes. **However, do not explicitly mention the user's wishes in your theme output or in any conversational turn during this check-in phase unless the user mentions them first.** Your primary goal here is to understand the themes in the user's current message.

            THEME QUALITY CRITERIA:
            - Specific enough to be meaningful (not just "Life" or "Goals")
            - Broad enough to group related concepts (not every detail needs its own theme)
            - Described objectively (capturing what the user actually said)
            - Relevant to the manifestation process and user's journey
            - Connected to active wishes when appropriate
            - Personal Relevance: The theme must be clearly tied to the user\'s own life, feelings, challenges, or aspirations. It should not be an observation about the tool or the interaction process.
            
            Respond with a JSON array in this format (multiple themes are shown in this example to clarify JSON structure for that case):
            {
              "themes": [
                {
                    "title": "Theme Title",
                    "observations": ["Observation 1", "Observation 2"]
                },
                {
                    "title": "Another Theme",
                    "observations": ["Observation 1"]
                }
              ]
            }
            
            If no meaningful themes are present, return: {"themes": []}
            DO NOT include any text before or after the JSON array.

""".trimIndent()

        val finalPrompt = """
            $systemInstructions
            $themeExtractionPrompt
        """.trimIndent()
        return finalPrompt
    }


}
