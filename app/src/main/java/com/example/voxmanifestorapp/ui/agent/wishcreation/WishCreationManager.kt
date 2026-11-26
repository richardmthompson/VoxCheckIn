package com.example.voxmanifestorapp.ui.agent.wishcreation

import com.example.voxmanifestorapp.ui.agent.BrainService
import com.example.voxmanifestorapp.ui.agent.checkin.ConversationalTheme
import com.example.voxmanifestorapp.ui.agent.utilities.WishUtilities

/**
 * WishCreationManager - Conversational wish creation with LLM integration
 * 
 * Handles the complete flow of creating wishes through natural conversation:
 * 1. Generate initial wish from check-in themes
 * 2. Allow conversational refinement through voice feedback  
 * 3. Save completed wish to database slot
 * 4. Return to core loop when complete
 * 
 * State Machine: "generating" → "refining" → "validating"
 */
data class WishCreationResult(
    val speechResponse: String,
    val shouldContinue: Boolean,
    val isComplete: Boolean = false
)

data class WishCreationState(
    val isActive: Boolean = false,
    val currentWish: String? = null,
    val stage: String = "generating"
)

class WishCreationManager(
    private val wishUtilities: WishUtilities,
    private val brainService: BrainService
) {
    /**
     * Main orchestration method for wish creation conversation.
     * Handles state machine transitions and LLM integration.
     */
    suspend fun processWishCreation(
        themes: List<ConversationalTheme>, 
        userInput: String?, 
        currentState: WishCreationState
    ): WishCreationResult {
        return when (currentState.stage) {
            "generating" -> {
                val initialWish = generateInitialWish(themes)
                WishCreationResult(
                    speechResponse = "I've created a wish based on our conversation: '$initialWish'. What do you think?",
                    shouldContinue = true,
                    isComplete = false
                )
            }
            "refining" -> {
                val refinedWish = refineWish(currentState.currentWish ?: "", userInput ?: "")
                WishCreationResult(
                    speechResponse = "Here's the refined wish: '$refinedWish'. Is this what you're looking for?",
                    shouldContinue = true,
                    isComplete = false
                )
            }
            "validating" -> {
                // Handle yes/no confirmation
                if (userInput?.contains("yes", ignoreCase = true) == true) {
                    // Save wish to database
                    val slot = wishUtilities.findNextEmptyWishSlot()
                    wishUtilities.saveWishToSlot(currentState.currentWish ?: "", slot)
                    WishCreationResult(
                        speechResponse = "Perfect! Your wish has been saved. Let's continue with our conversation.",
                        shouldContinue = false,
                        isComplete = true
                    )
                } else {
                    // Continue refining
                    WishCreationResult(
                        speechResponse = "Let me refine it further. What would you like to change?",
                        shouldContinue = true,
                        isComplete = false
                    )
                }
            }
            else -> {
                WishCreationResult(
                    speechResponse = "I'm not sure what to do. Let's start over.",
                    shouldContinue = false,
                    isComplete = false
                )
            }
        }
    }
    
    /**
     * Generates initial wish from check-in themes using LLM.
     * Analyzes conversation themes and creates a specific, present-tense wish.
     */
    private suspend fun generateInitialWish(themes: List<ConversationalTheme>): String {
        val themeText = themes.joinToString("\n") { "- ${it.title}: ${it.observations.joinToString(", ")}" }
        
        val prompt = """
            Analyze these conversation themes and create a specific, present-tense wish:
            
            $themeText
            
            Requirements:
            - Present tense, positive language
            - Specific and actionable
            - Emotionally resonant
            - Based on the themes provided
            
            Return only the wish text, no additional explanation.
        """.trimIndent()
        
        return brainService.generateSimpleText(prompt).getOrElse { 
            "I want to create positive change in my life" 
        }
    }
    
    /**
     * Refines existing wish based on user feedback using LLM.
     * Maintains core intention while improving specificity and clarity.
     */
    private suspend fun refineWish(currentWish: String, userFeedback: String): String {
        val prompt = """
            Current wish: $currentWish
            User feedback: $userFeedback
            
            Refine the wish based on feedback while maintaining:
            - Present tense, positive language
            - Specificity and clarity
            - Core intention from original wish
            
            Return only the refined wish text, no additional explanation.
        """.trimIndent()
        
        return brainService.generateSimpleText(prompt).getOrElse { currentWish }
    }
} 