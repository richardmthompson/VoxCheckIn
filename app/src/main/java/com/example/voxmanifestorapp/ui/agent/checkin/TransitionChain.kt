package com.example.voxmanifestorapp.ui.agent.checkin

import android.util.Log
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.BrainService
import com.example.voxmanifestorapp.ui.agent.ConversationPhase
import com.example.voxmanifestorapp.ui.agent.commands.CommandMode
import com.example.voxmanifestorapp.ui.agent.utilities.EnhancedWishSummary
import com.example.voxmanifestorapp.ui.agent.utilities.WishSummary

/**
 * Focused transition processor that handles theme presentation and action suggestions. Called
 * directly by DialogueChain when transition is needed.
 */
class TransitionChain(
        private val brainService: BrainService,
        private val commandMode: CommandMode,
        private val logger: (String, StatusColor) -> Unit
) {

  /**
   * Processes the transition from check-in to the next conversation phase. This method orchestrates
   * the phase suggestion and message crafting with focused single-responsibility chains.
   *
   * @param themes The extracted themes from the current conversation
   * @param enhancedWishes Enhanced wish data with present/desired state items
   * @return TransitionActionPlan with structured routing information
   */
  suspend fun processTransition(
          themes: List<ConversationalTheme>,
          enhancedWishes: List<EnhancedWishSummary>
  ): TransitionActionPlan {

    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Starting restructured transition processing")
    logger("🔄 TRANSITION CHAIN: Starting restructured transition processing with ${themes.size} themes and ${enhancedWishes.size} wishes", StatusColor.Go)

    // Step 1: Select all themes for comprehensive coverage
    val allThemes = selectAllThemes(themes)
    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Selected ${allThemes.size} themes for comprehensive analysis from ${themes.size} total")

    // Step 2: Chain A - Phase Suggestion (NEW)
    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Starting Chain A - Phase Suggestion")
    val phaseSuggestion = makePhaseSuggestion(allThemes, enhancedWishes)

    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Chain A complete - Phase: ${phaseSuggestion.suggestedPhase}, Wish: ${phaseSuggestion.targetWishId}")
    logger("🔄 TRANSITION CHAIN: Chain A complete - suggested phase: ${phaseSuggestion.suggestedPhase}", StatusColor.Default)

    // Step 3: Chain B - Message Crafting (NEW)
    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Starting Chain B - Message Crafting")
    val transitionMessage =
            craftTransitionMessage(
                    themes = allThemes,
                    phaseSuggestion = phaseSuggestion,
                    enhancedWishes = enhancedWishes
            )

    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Chain B complete - Message length: ${transitionMessage.message.length} chars")
    logger("🔄 TRANSITION CHAIN: Chain B complete - message crafted", StatusColor.Default)

    // Step 4: Create command mode text and combine with theme summary
      /** not doing this in MVP!!
    val commandModeText = "You can now use voice commands to manage your wishes. Try saying 'Add wish' to create a new goal, or 'List wishes' to review your current ones. Say 'Help' to get started with all available commands."
    val finalMessage = "${transitionMessage.message} $commandModeText"

    // Log full message construction details
    logger("🔗 FULL MESSAGE: Combined theme summary (${transitionMessage.message.length} chars) + command text (${commandModeText.length} chars) = ${finalMessage.length} total chars", StatusColor.Default)

       */

    // Step 5: Combine results into TransitionActionPlan
    val finalActionPlan =
            TransitionActionPlan(
                // we're not making an actionSuggestion for MVP!! just finishing the conversation.
                    actionSuggestion = "",  // Full message with command mode text
                    themeSummary = transitionMessage.message,  // Pure theme summary for playback
                    //proposedPhase = phaseSuggestion.suggestedPhase,
                    //targetWishId = phaseSuggestion.targetWishId,
                    //reasoning = phaseSuggestion.reasoning, // Preserve phase suggestion reasoning
                    themes = allThemes // Include the processed themes
            )

    // Comprehensive logging of final result
    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Final TransitionActionPlan created")
    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Action suggestion: ${finalActionPlan.actionSuggestion}")
/**    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Proposed phase: ${finalActionPlan.proposedPhase}")
    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Target wish ID: ${finalActionPlan.targetWishId}")
    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Reasoning: ${finalActionPlan.reasoning}")
*/

    // Step 6: Generate session name based on themes and transition context
    val sessionName = generateSessionName(themes, phaseSuggestion, enhancedWishes)
    Log.d("TransitionChain", "🔄 TRANSITION CHAIN: Generated session name: '$sessionName'")
    logger("🔄 TRANSITION CHAIN: Generated session name: '$sessionName'", StatusColor.Default)

    logger("🔄 TRANSITION CHAIN: Complete - returning structured action plan", StatusColor.Go)
    return finalActionPlan.copy(sessionName = sessionName)
  }

  /**
   * Selects all themes for comprehensive coverage and narrative synthesis. Provides complete theme
   * analysis rather than limiting to top themes.
   */
  private fun selectAllThemes(themes: List<ConversationalTheme>): List<ConversationalTheme> {

    if (themes.isEmpty()) {
      return emptyList()
    }

    // Include all themes for comprehensive coverage
    val allThemes = themes.sortedByDescending { it.observations.size }

    logger("🔄 THEME SELECTION: All themes by observations: ${allThemes.map { "${it.title}(${it.observations.size})" }}", StatusColor.Default)
    logger("🔄 THEME SELECTION: Including all ${allThemes.size} themes for comprehensive analysis", StatusColor.Default)

    return allThemes
  }

  /**
   * Returns the valid conversation phases that TransitionChain can suggest. Excludes CHECK_IN,
   * LOOP_DECISION, and CONVERSATION_END as these are not user-selectable transitions.
   */
  private fun getValidTransitionPhases(): List<ConversationPhase> {
    return listOf()
  }

  /**
   * Chain A: Makes phase suggestion based on themes and wishes. Focused single-responsibility
   * method for analytical phase selection.
   */
  private suspend fun makePhaseSuggestion(
          themes: List<ConversationalTheme>,
          enhancedWishes: List<EnhancedWishSummary>
  ): PhaseSuggestionResult {

    Log.d("TransitionChain", "🔄 CHAIN A: Building phase suggestion prompt")
    logger("🔄 CHAIN A: Analyzing themes and wishes for phase suggestion", StatusColor.Default)

    // MVP IMPLEMENTATION: Bypass LLM call and directly return CONVERSATION_END
    // This disables WISH_COLLECTION feature for stable MVP release
    
    // Build focused phase suggestion prompt (kept for message crafting chain)
    //val prompt = buildPhaseSuggestionPrompt(themes, enhancedWishes)

    Log.d("TransitionChain", "🔄 CHAIN A: MVP - Bypassing LLM call, returning CONVERSATION_END")
    logger("🔄 CHAIN A: MVP - Forcing CONVERSATION_END for CommandMode transition", StatusColor.Go)

    // TODO: Re-enable LLM call for post-MVP when WISH_COLLECTION bugs are fixed
    // val result = brainService.makePhaseSuggestion(prompt)
    // val phaseSuggestion = result.getOrThrow()

    // Direct return for MVP: Always transition to CONVERSATION_END
    return PhaseSuggestionResult(
        suggestedPhase = "CONVERSATION_END",
        targetWishId = null,
        reasoning = "Simply terminate the conversation because the MVP finishes after theme analysis"
        //reasoning = "Transitioning to CommandMode for wish management after theme analysis"
    )
  }

  /**
   * Chain B: Crafts transition message incorporating themes and selected phase. Focused
   * single-responsibility method for creative message generation.
   */
  private suspend fun craftTransitionMessage(
          themes: List<ConversationalTheme>,
          phaseSuggestion: PhaseSuggestionResult,
          enhancedWishes: List<EnhancedWishSummary>
  ): TransitionMessageResponse {

    Log.d("TransitionChain", "🔄 CHAIN B: Building message crafting prompt")
    logger("🔄 CHAIN B: Crafting transition message with selected phase context", StatusColor.Default)

    // Build message crafting prompt with phase context
    val prompt = buildMessageCraftingPrompt(themes, phaseSuggestion, enhancedWishes)

    Log.d("TransitionChain", "🔄 CHAIN B: Prompt built, length: ${prompt.length} chars")
    Log.d("TransitionChain", "🔄 CHAIN B: Calling brainService.craftTransitionMessage()")

    // Call BrainService and let NetworkException propagate to handleError()
    val result = brainService.craftTransitionMessage(prompt)
    val transitionMessage = result.getOrThrow() // Let NetworkException propagate

    Log.d("TransitionChain", "🔄 CHAIN B: Successfully received transition message from brain service")
    logger("🔄 CHAIN B: Successfully crafted transition message", StatusColor.Go)

    // Log theme summary content for monitoring
    val summaryPreview = if (transitionMessage.message.length > 150) {
      "${transitionMessage.message.substring(0, 150)}..."
    } else {
      transitionMessage.message
    }
    logger("🎯 THEME SUMMARY: Generated (${transitionMessage.message.length} chars): $summaryPreview", StatusColor.Default)

    return transitionMessage
  }

  /**
   * NEW: Builds prompt for phase suggestion (Chain A). Focused on analytical decision-making for
   * optimal phase selection.
   */
  private suspend fun buildPhaseSuggestionPrompt(
          themes: List<ConversationalTheme>,
          enhancedWishes: List<EnhancedWishSummary>
  ): String {
    val systemInstructions = Prompts.systemInstructions

    val themesText =
            if (themes.isEmpty()) {
              "No themes identified."
            } else {
              themes.joinToString(separator = "\n") { theme ->
                "- ${theme.title}: ${theme.observations.joinToString(", ")}"
              }
            }

    // Convert enhanced wishes to basic wishes for prompt compatibility
    val userWishes = enhancedWishes.map { WishSummary(it.id, it.title) }
    val wishesText =
            if (userWishes.isEmpty()) {
              "No active wishes defined yet."
            } else {
              userWishes.joinToString(separator = "\n") { wish ->
                "- Wish #${wish.id}: ${wish.title}"
              }
            }

    // MVP ENHANCEMENT: Get slot availability from CommandMode (reuses existing logic)
    val slotAvailabilityText = commandMode.getSlotAvailabilityInfo()

    val phaseSuggestionPrompt =
            """
            PHASE SUGGESTION TASK
            ====================
            Analyze the conversation themes and user's existing wishes to suggest the most appropriate next conversation phase.
            Your task is purely analytical - determine which phase would be most beneficial based on the evidence.

            CONVERSATION THEMES:
            $themesText

            USER'S EXISTING WISHES:
            $wishesText

            WISH SLOT AVAILABILITY:
            $slotAvailabilityText

            PHASE SELECTION CRITERIA:
            Choose the most appropriate phase based on theme-wish correlation:

            1. **WISH_COLLECTION** - Use when (MVP PRIORITY):
               - User has empty wish slots available (PRIORITIZE THIS FOR MVP)
               - Themes suggest new focus areas not covered by existing wishes
               - User expresses new aspirations or goals not related to current wishes
               - No existing wishes, or themes don't relate to current wishes
               - IMPORTANT: For MVP, strongly prefer wish creation when slots are available
               - Set targetWishId: null

            2. **AFFIRMATION_PROCESS** - Use when:
               - Themes show doubt, discouragement, or need for motivation
               - User expresses limiting beliefs or lack of confidence about their wishes
               - Need to reinforce commitment and belief in their goals
               - Set targetWishId: [relevant wish ID]

            AVAILABLE PHASES:
            ${getValidTransitionPhases().joinToString(", ") { it.name }}

            ANALYSIS INSTRUCTIONS:
            1. FIRST: Check slot availability - if slots are available, strongly consider WISH_COLLECTION
            2. Examine each theme and determine if it relates to existing wishes
            3. Identify the strongest theme-wish correlation
            4. For MVP: Prioritize wish creation when slots are available and themes suggest new focus areas
            5. Select the phase that would most effectively address the identified correlation
            6. Provide clear reasoning for your selection

            Respond with a JSON object:
            {
                "suggestedPhase": "PHASE_NAME_FROM_ABOVE_LIST",
                "targetWishId": 2,
                "reasoning": "Brief explanation of why this phase is optimal based on theme analysis in 25 words or less"
            }

            REQUIREMENTS:
            - suggestedPhase must be one of: ${getValidTransitionPhases().joinToString(", ") { it.name }}
            - targetWishId should be null for WISH_COLLECTION, wish ID number for others
            - For MVP: Strongly prefer WISH_COLLECTION when slots are available and themes suggest new areas
            - Choose phase based on strongest theme-wish correlation and slot availability

            DO NOT include any text before or after the JSON object.
        """.trimIndent()

    return """
            $systemInstructions
            $phaseSuggestionPrompt
        """.trimIndent()
  }

  /**
   * NEW: Builds prompt for message crafting (Chain B). Focused on creative message generation
   * incorporating themes and selected phase.
   */
  private suspend fun buildMessageCraftingPrompt(
          themes: List<ConversationalTheme>,
          phaseSuggestion: PhaseSuggestionResult,
          enhancedWishes: List<EnhancedWishSummary>
  ): String {
    val systemInstructions = Prompts.systemInstructions

    val themesText =
            if (themes.isEmpty()) {
              "No themes identified."
            } else {
              themes.joinToString(separator = "\n") { theme ->
                "- ${theme.title}: ${theme.observations.joinToString(", ")}"
              }
            }

    // Convert enhanced wishes to basic wishes for prompt compatibility
    val userWishes = enhancedWishes.map { WishSummary(it.id, it.title) }
    val wishesText =
            if (userWishes.isEmpty()) {
              "No active wishes defined yet."
            } else {
              userWishes.joinToString(separator = "\n") { wish ->
                "- Wish #${wish.id}: ${wish.title}"
              }
            }

    // Get phase information for context
      // we're not doing this in MVP!
    //val selectedPhase = ConversationPhase.valueOf(phaseSuggestion.suggestedPhase)

    // MVP ENHANCEMENT: Get slot availability from CommandMode (reuses existing logic)
      // we're not doing this in MVP!
    //val slotAvailabilityText = commandMode.getSlotAvailabilityInfo()

      /** Took these out from the messageCraftingPrompt, as they're not used in MVP!
       *
      USER'S EXISTING WISHES (for context):
      $wishesText

      WISH SLOT AVAILABILITY:
      $slotAvailabilityText

      SELECTED NEXT PHASE:
      ${phaseSuggestion.suggestedPhase}: ${selectedPhase.description}
      Purpose: ${selectedPhase.purpose}
      Phase Selection Reasoning: ${phaseSuggestion.reasoning}

       *
       */

    val messageCraftingPrompt =
            """
            TRANSITION MESSAGE CRAFTING TASK
            ===============================
            Create a natural, conversational message that provides detailed analysis of the user's themes,
            and terminates the conversation naturally by saying something like - "remember to check in again soon!"
            This message will be spoken directly to the user.

            CONVERSATION THEMES (provide detailed analysis):
            $themesText
            
            MESSAGE CRAFTING GUIDELINES:
            1. Provide comprehensive theme analysis for ALL themes (not just top 3)
               - Analyze and present ALL themes that were identified (could be 3-6+ themes)
               - For each theme, synthesize and summarize the multiple observations
               - Reference specific examples, feelings, or situations they mentioned
               - Use phrases like "I noticed you mentioned...", "You shared that...", "You expressed..."
               - Make it clear you've been listening carefully to their specific concerns
               - IMPORTANT: Do NOT use markdown formatting (no **bold**, *italic*, or other formatting)
               - Write in plain text that flows naturally when spoken aloud

            2. Create narrative synthesis across ALL themes
               - Don't just list themes individually - weave them into a coherent narrative
               - Show how themes connect and influence each other
               - Identify patterns and relationships between different themes
               - Create a story that explains their overall situation holistically
               - Example: "These themes seem interconnected - your work stress is affecting your health, which impacts your career confidence..."

            3. Structure the theme analysis with comprehensive synthesis
               - Present each theme with clear synthesis (e.g., "Work Stress Theme:")
               - Synthesize multiple observations into 2-3 key points per theme
               - Combine related observations and highlight the most significant aspects
               - Show understanding of the depth and complexity of their situation
               - If there are many observations, summarize the main patterns and concerns

            4. Handle observation synthesis intelligently
               - When there are many observations (up to 10 per theme), synthesize them
               - Group related observations together (e.g., "You mentioned several work-related stressors...")
               - Highlight the most significant or recurring concerns
               - Show patterns and connections between different observations
               - Avoid listing every single observation - focus on the most important insights



            5. Keep it conversational and comprehensive
               - Sound like you're speaking to a friend who wants to understand your thinking
               - Show your reasoning process in a natural way
               - Avoid clinical or robotic language
               - Make it flow naturally when spoken aloud
               - Provide enough detail to show you truly understand their situation
               - IMPORTANT: Write in plain text only - no markdown formatting, bold, italic, or special characters



            6. CONVERSATION_END SPECIFIC MESSAGING (when phase is CONVERSATION_END):
               - Focus ONLY on providing the comprehensive theme summary
               - Do NOT include any voice command instructions or guidance
               - End naturally after the theme analysis and reasoning
               - The message should conclude with the theme summary and phase reasoning only

            EXAMPLE STRUCTURE (for comprehensive theme coverage with narrative synthesis):
            "I've analyzed our conversation and identified several important themes that seem to be interconnected in your current situation:

            One theme was around Work Stress: I noticed you mentioned feeling overwhelmed by your current workload, specifically mentioning the tight deadlines on the Johnson project and your concerns about work-life balance. You also expressed frustration about not having enough time for personal development, which suggests this isn't just about one project but a broader pattern of workload management. You mentioned feeling like you're constantly playing catch-up, which indicates this stress is affecting your overall work satisfaction and potentially your confidence in your role.

            Another theme was around Career Growth: You shared your desire to advance in your career, mentioning your interest in leadership roles and your recent completion of the management certification. You also expressed uncertainty about how to balance growth opportunities with your current responsibilities, which shows you're thinking strategically about your career path but feeling stuck between your current demands and future aspirations. I noticed you mentioned feeling like your current role might be holding you back from the leadership opportunities you're ready for.

            A further theme that came up was about Health Concerns: You briefly mentioned feeling tired and not getting enough sleep, which seems to be affecting your energy levels during the day. While this came up less frequently than the other themes, it's important because it connects to both your work stress and career growth concerns - when you're exhausted, it's harder to perform at your best and pursue those leadership opportunities you're interested in.

            You also mentioned some Financial Planning concerns, particularly around saving for future goals while managing current expenses. This connects to your career growth theme because you're thinking about how your current salary relates to your long-term financial security and the lifestyle you want to build.

            And I noticed some Relationship Dynamics themes, specifically around balancing work commitments with family time. This ties into your work stress and health concerns - when work demands more time, it affects your relationships, which can create additional stress and impact your overall well-being.

            These themes seem deeply interconnected - your work stress is affecting your health and relationships, which impacts your career confidence and financial planning. The pattern suggests that addressing your work situation comprehensively could have positive ripple effects across all these areas of your life."

            Respond with a JSON object:
            {
                "message": "Complete transition message ready to be spoken to the user, including detailed theme analysis and natural explanation of reasoning",
                "reasoning": "Brief explanation of how detailed theme analysis was incorporated in 20 words or less"
            }

            DO NOT include any text before or after the JSON object.
        """.trimIndent()

    return """
            $systemInstructions
            $messageCraftingPrompt
        """.trimIndent()
  }

  /**
   * Generates a concise session name based on themes and transition context. Called during
   * transition to create a meaningful name for the conversation session.
   *
   * @param themes The extracted themes from the conversation
   * @param phaseSuggestion The selected phase and reasoning
   * @param enhancedWishes The user's wishes for context
   * @return A concise session name (typically 2-4 words)
   */
  private suspend fun generateSessionName(
          themes: List<ConversationalTheme>,
          phaseSuggestion: PhaseSuggestionResult,
          enhancedWishes: List<EnhancedWishSummary>
  ): String {
    Log.d("TransitionChain", "Generating session name from ${themes.size} themes and phase: ${phaseSuggestion.suggestedPhase}")
    themes.forEach { theme ->
      Log.d("TransitionChain", "  - ${theme.title} (${theme.observations.size} observations)")
    }

    // If no themes, use a simple generic name
    if (themes.isEmpty()) {
      return "Check-In Session"
    }

    // Create a simple name based on the most prominent theme only
    val primaryTheme = themes.maxByOrNull { it.observations.size }
    val themeTitle = primaryTheme?.title?.replace("_", " ")?.lowercase()?.capitalize() ?: "General"

    // Keep it simple - just the theme title
    val sessionName = themeTitle
    Log.d("TransitionChain", "Generated session name: '$sessionName'")
    return sessionName
  }

  /*
   * The following backup methods are preserved for post-MVP restoration.
   * They contain the original concept-focused prompt logic for phase suggestion and message crafting.
   * These are NOT used in the MVP code path and are commented out to avoid confusion.
   * Restore and update as needed for future concept screen integration.
   */

  /*
  // BACKUP: Concept-focused version of buildPhaseSuggestionPrompt for post-MVP
  private fun buildPhaseSuggestionPrompt_ConceptFocused(
      themes: List<ConversationalTheme>,
      enhancedWishes: List<EnhancedWishSummary>
  ): String {
      val systemInstructions = Prompts.systemInstructions

      val themesText = if (themes.isEmpty()) {
          "No themes identified."
      } else {
          themes.joinToString(separator = "\n") { theme ->
              "- ${theme.title}: ${theme.observations.joinToString(", ")}"
          }
      }

      // Convert enhanced wishes to basic wishes for prompt compatibility
      val userWishes = enhancedWishes.map { WishSummary(it.id, it.title) }
      val wishesText = if (userWishes.isEmpty()) {
          "No active wishes defined yet."
      } else {
          userWishes.joinToString(separator = "\n") { wish ->
              "- Wish #${wish.id}: ${wish.title}"
          }
      }

      val phaseSuggestionPrompt = """
          PHASE SUGGESTION TASK
          ====================
          Analyze the conversation themes and user's existing wishes to suggest the most appropriate next conversation phase.
          Your task is purely analytical - determine which phase would be most beneficial based on the evidence.

          CONVERSATION THEMES:
          $themesText

          USER'S EXISTING WISHES:
          $wishesText

          PHASE SELECTION CRITERIA:
          Choose the most appropriate phase based on theme-wish correlation:

          1. **WISH_COLLECTION** - Use when:
             - Themes suggest new focus areas not covered by existing wishes
             - User expresses new aspirations or goals not related to current wishes
             - No existing wishes, or themes don't relate to current wishes
             - Set targetWishId: null

          2. **PRESENT_STATE_EXPLORATION** - Use when:
             - Themes relate to current challenges/situations in existing wish areas
             - User describes current problems or circumstances that connect to their wishes
             - Themes show need to understand "where they are now" regarding their goals
             - Set targetWishId: [relevant wish ID]

          3. **DESIRED_STATE_EXPLORATION** - Use when:
             - Themes suggest expanding or clarifying vision for existing wishes
             - User mentions aspirations that connect to existing wishes but need more detail
             - Themes show need to define "where they want to be" more clearly
             - Set targetWishId: [relevant wish ID]

          4. **CONTRAST_ANALYSIS** - Use when:
             - Themes highlight gaps between current and desired states
             - User expresses frustration about lack of progress on existing wishes
             - Both present and desired states are clear, need pathway analysis
             - Set targetWishId: [relevant wish ID]

          5. **AFFIRMATION_PROCESS** - Use when:
             - Themes show doubt, discouragement, or need for motivation
             - User expresses limiting beliefs or lack of confidence about their wishes
             - Need to reinforce commitment and belief in their goals
             - Set targetWishId: [relevant wish ID]

          AVAILABLE PHASES:
          ${getValidTransitionPhases().joinToString(", ") { it.name }}

          ANALYSIS INSTRUCTIONS:
          1. Examine each theme and determine if it relates to existing wishes
          2. Identify the strongest theme-wish correlation
          3. Select the phase that would most effectively address the identified correlation
          4. Provide clear reasoning for your selection

          Respond with a JSON object:
          {
              "suggestedPhase": "PHASE_NAME_FROM_ABOVE_LIST",
              "targetWishId": 2,
              "reasoning": "Brief explanation of why this phase is optimal based on theme analysis in 25 words or less"
          }

          REQUIREMENTS:
          - suggestedPhase must be one of: ${getValidTransitionPhases().joinToString(", ") { it.name }}
          - targetWishId should be null for WISH_COLLECTION, wish ID number for others
          - Choose phase based on strongest theme-wish correlation

          DO NOT include any text before or after the JSON object.
      """.trimIndent()

      return """
          $systemInstructions
          $phaseSuggestionPrompt
      """.trimIndent()
  }

  // BACKUP: Concept-focused version of buildMessageCraftingPrompt for post-MVP
  private fun buildMessageCraftingPrompt_ConceptFocused(
      themes: List<ConversationalTheme>,
      phaseSuggestion: PhaseSuggestionResult,
      enhancedWishes: List<EnhancedWishSummary>
  ): String {
      val systemInstructions = Prompts.systemInstructions

      val themesText = if (themes.isEmpty()) {
          "No themes identified."
      } else {
          themes.joinToString(separator = "\n") { theme ->
              "- ${theme.title}: ${theme.observations.joinToString(", ")}"
          }
      }

      // Convert enhanced wishes to basic wishes for prompt compatibility
      val userWishes = enhancedWishes.map { WishSummary(it.id, it.title) }
      val wishesText = if (userWishes.isEmpty()) {
          "No active wishes defined yet."
      } else {
          userWishes.joinToString(separator = "\n") { wish ->
              "- Wish #${wish.id}: ${wish.title}"
          }
      }

      // Get phase information for context
      val selectedPhase = ConversationPhase.valueOf(phaseSuggestion.suggestedPhase)

      val messageCraftingPrompt = """
          TRANSITION MESSAGE CRAFTING TASK
          ===============================
          Create a natural, conversational transition message that provides detailed analysis of the user's themes,
          explains the reasoning for the suggested phase, and introduces the next step.
          This message will be spoken directly to the user.

          CONVERSATION THEMES (provide detailed analysis):
          $themesText

          USER'S EXISTING WISHES (for context):
          $wishesText

          SELECTED NEXT PHASE:
          ${phaseSuggestion.suggestedPhase}: ${selectedPhase.description}
          Purpose: ${selectedPhase.purpose}
          Phase Selection Reasoning: ${phaseSuggestion.reasoning}

          MESSAGE CRAFTING GUIDELINES:
          1. Provide comprehensive theme analysis for ALL themes (not just top 3)
             - Analyze and present ALL themes that were identified (could be 3-6+ themes)
             - For each theme, synthesize and summarize the multiple observations
             - Reference specific examples, feelings, or situations they mentioned
             - Use phrases like "I noticed you mentioned...", "You shared that...", "You expressed..."
             - Make it clear you've been listening carefully to their specific concerns
             - IMPORTANT: Do NOT use markdown formatting (no **bold**, *italic*, or other formatting)
             - Write in plain text that flows naturally when spoken aloud

          2. Create narrative synthesis across ALL themes
             - Don't just list themes individually - weave them into a coherent narrative
             - Show how themes connect and influence each other
             - Identify patterns and relationships between different themes
             - Create a story that explains their overall situation holistically
             - Example: "These themes seem interconnected - your work stress is affecting your health, which impacts your career confidence..."

          3. Structure the theme analysis with comprehensive synthesis
             - Present each theme with clear synthesis (e.g., "Work Stress Theme:")
             - Synthesize multiple observations into 2-3 key points per theme
             - Combine related observations and highlight the most significant aspects
             - Show understanding of the depth and complexity of their situation
             - If there are many observations, summarize the main patterns and concerns

          4. Handle observation synthesis intelligently
             - When there are many observations (up to 10 per theme), synthesize them
             - Group related observations together (e.g., "You mentioned several work-related stressors...")
             - Highlight the most significant or recurring concerns
             - Show patterns and connections between different observations
             - Avoid listing every single observation - focus on the most important insights

          5. Incorporate the phase selection reasoning naturally
             - Explain WHY this phase makes sense based on the comprehensive theme analysis
             - Use the reasoning from the phase selection to show your thought process
             - Make it sound like natural explanation, not robotic analysis
             - Example: "Based on these interconnected themes, I suggest we focus on [phase purpose] because [reasoning]"

          6. Introduce the suggested phase and its value
             - Connect the detailed themes to the phase's purpose
             - Explain what this phase will help them accomplish
             - Make it sound beneficial and relevant to their specific situation

          7. Keep it conversational and comprehensive
             - Sound like you're speaking to a friend who wants to understand your thinking
             - Show your reasoning process in a natural way
             - Avoid clinical or robotic language
             - Make it flow naturally when spoken aloud
             - Provide enough detail to show you truly understand their situation
             - IMPORTANT: Write in plain text only - no markdown formatting, bold, italic, or special characters

          EXAMPLE STRUCTURE (for comprehensive theme coverage with narrative synthesis):
          "I've analyzed our conversation and identified several important themes that seem to be interconnected in your current situation:

          One theme was around Work Stress: I noticed you mentioned feeling overwhelmed by your current workload, specifically mentioning the tight deadlines on the Johnson project and your concerns about work-life balance. You also expressed frustration about not having enough time for personal development, which suggests this isn't just about one project but a broader pattern of workload management. You mentioned feeling like you're constantly playing catch-up, which indicates this stress is affecting your overall work satisfaction and potentially your confidence in your role.

          Another theme was around Career Growth: You shared your desire to advance in your career, mentioning your interest in leadership roles and your recent completion of the management certification. You also expressed uncertainty about how to balance growth opportunities with your current responsibilities, which shows you're thinking strategically about your career path but feeling stuck between your current demands and future aspirations. I noticed you mentioned feeling like your current role might be holding you back from the leadership opportunities you're ready for.

          A further theme that came up was about Health Concerns: You briefly mentioned feeling tired and not getting enough sleep, which seems to be affecting your energy levels during the day. While this came up less frequently than the other themes, it's important because it connects to both your work stress and career growth concerns - when you're exhausted, it's harder to perform at your best and pursue those leadership opportunities you're interested in.

          You also mentioned some Financial Planning concerns, particularly around saving for future goals while managing current expenses. This connects to your career growth theme because you're thinking about how your current salary relates to your long-term financial security and the lifestyle you want to build.

          And I noticed some Relationship Dynamics themes, specifically around balancing work commitments with family time. This ties into your work stress and health concerns - when work demands more time, it affects your relationships, which can create additional stress and impact your overall well-being.

          These themes seem deeply interconnected - your work stress is affecting your health and relationships, which impacts your career confidence and financial planning. The pattern suggests that addressing your work situation comprehensively could have positive ripple effects across all these areas of your life.

          Based on these interconnected themes, I suggest we focus on exploring your current work situation in more detail because there's a clear pattern of workload stress that's impacting multiple areas of your life. This will help us understand the specific challenges you're facing and create a plan that addresses both your immediate stress and your longer-term goals across work, health, relationships, and finances."

          Respond with a JSON object:
          {
              "message": "Complete transition message ready to be spoken to the user, including detailed theme analysis and natural explanation of reasoning",
              "reasoning": "Brief explanation of how detailed theme analysis was incorporated in 20 words or less"
          }

          DO NOT include any text before or after the JSON object.
      """.trimIndent()

      return """
          $systemInstructions
          $messageCraftingPrompt
      """.trimIndent()
  }
  */
}

