package com.example.voxmanifestorapp.ui.agent

/**
 * Defines the main phases of the conversation journey for the Core Conversation Loop.
 * These phases represent the structured coaching sequence that guides users through defining and refining their wishes.
 * Each phase includes LLM-friendly descriptions for intelligent transition decisions.
 */
enum class ConversationPhase(
    val description: String,
    val purpose: String,
    val example: String
) {
    CHECK_IN(
        "Voice journaling conversation about current thoughts and experiences",
        "Facilitate open reflection and extract themes from user's current state",
        "Tell me what's on your mind today and how you're feeling"
    ),
    WISH_COLLECTION(
        "Capturing a new wish or goal",
        "Help user articulate and define a new manifestation goal",
        "Let's create a new wish for your career growth"
    ),
    PRESENT_STATE_EXPLORATION(
        "Exploring current reality related to the wish",
        "Build detailed understanding of user's current situation",
        "Tell me about your current work situation and challenges"
    ),
    DESIRED_STATE_EXPLORATION(
        "Defining the desired outcome of the wish",
        "Clarify the user's vision for their ideal future state",
        "Describe what your ideal career situation would look like"
    ),
    CONTRAST_ANALYSIS(
        "Analyzing the gap between present and desired states",
        "Identify transformation pathways and action steps",
        "Let's explore what needs to change to bridge this gap"
    ),
    AFFIRMATION_PROCESS(
        "Providing affirmations to reinforce commitment",
        "Strengthen user's belief and motivation for their goals",
        "Let me share some affirmations about your career growth journey"
    ),
    LOOP_DECISION(
        "Deciding whether to continue with another wish or end session",
        "Determine next steps for the conversation flow",
        "Would you like to work on another wish or wrap up for today?"
    ),
    CONVERSATION_END(
        "Ending the current Core Loop session",
        "Graceful conclusion with summary and encouragement",
        "Great work today! Let's check in again soon"
    )
}



/**
 * Core data structure representing the state of the overall coaching conversation loop.
 * Tracks which wish is currently being worked on, what phase of the conversation is active,
 * and the completion status of phases across all wishes.
 */
data class CoreLoopState(
    val currentPhase: ConversationPhase = ConversationPhase.CHECK_IN,
    val currentWishIndex: Int = -1, // -1 indicates no wish selected yet
    val loopCount: Int = 1, // Tracks how many times we've cycled through the coaching loop
    val currentUnderstanding: String = "" // Stores the agent's current understanding of the conversation
)
