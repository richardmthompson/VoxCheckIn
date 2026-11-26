package com.example.voxmanifestorapp.ui.agent.checkin

import com.example.voxmanifestorapp.ui.agent.ConversationEntry
import com.example.voxmanifestorapp.ui.agent.ConversationPhase
import com.example.voxmanifestorapp.ui.agent.timer.TimerState
import com.example.voxmanifestorapp.ui.agent.utilities.WishSummary
import kotlinx.serialization.Serializable


/**
 * Complete state for CHECK_IN phase
 */
data class CheckInState(
    val currentStage: CheckInStage = CheckInStage.OPENING,
    val engagementMetrics: UserEngagementMetrics = UserEngagementMetrics(),
    val activeThemes: List<ConversationalTheme> = emptyList(),
    val timerState: TimerState = TimerState.Inactive
)
/**
 * Represents the stages within the CHECK_IN phase (modern: only two stages)
 */
enum class CheckInStage {
    OPENING,    // Build rapport and explore freely
    TRANSITION  // Move on to the next activity
}
/**
 * Structure for tracking user engagement within Check-In.
 * Uses absolute thresholds per PRD requirements: <10 words = short response.
 */
data class UserEngagementMetrics(
    val responseHistory: List<Int> = emptyList(),
    var shortResponseCounter: Int = 0
    // Note: Only tracking consecutive short responses and history for PRD requirements
)
// New data class for conversational themes
@Serializable
data class ConversationalTheme(
    val title: String, // Represents the LLM-generated theme title, e.g., "Work Stress", "Sleep Quality"
    val observations: MutableList<String> // A list of concise observations, e.g., "User mentions feeling overwhelmed."
)

/**
 * Canonical enum for all check-in strategies. Used for orchestration, tagging, and example metadata.
 * Each entry includes a description.
 */
enum class Strategy(val description: String) {
    CONVERSATION_STARTING("Beginning a new conversation or topic"),
    RAPPORT_BUILDING("Establishing connection and trust"),
    EXPERIENCE_EXPLORATION("Exploring recent events and situations"),
    REFLECTION_DEEPENING("Inviting deeper reflection on values/emotions"),
    REFLECTIVE_MIRRORING("Reflecting back what was heard"),
    EMOTIONAL_VALIDATION("Acknowledging feelings as legitimate"),
    PERSPECTIVE_SHIFTING("Offering new viewpoints"),
    AFFIRM_SUPPORT("Offering positive affirmation or compassionate support"),
    CAUSAL_INQUIRY("Exploring or questioning potential cause-effect relationships related to the user's current situation or a stated problem."),
    DESIRED_STATE_EXPANSION("Encouraging the user to elaborate on their envisioned desired state, its implications, and motivations.")
}

/**
 * Result of transition evaluation from DialogueChain.
 * Contains decision, extracted themes, and updated metrics for use by ConversationAgent.
 * Simplified structure without nested context - ConversationAgent handles state updates.
 */
data class TransitionEvaluationResult(
    val shouldTransition: Boolean,
    val extractedThemes: List<ConversationalTheme>,
    val updatedMetrics: UserEngagementMetrics,
    val transitionReasoning: String = ""
)

/** BRAIN SERVICE RESPONSE STRUCTURES */
/**
 * Chain 1: Result of evaluating whether to transition from the check-in phase.
 */
@Serializable
data class TransitionDecision(
    val shouldTransition: Boolean,
    val reasoning: String
)

/**
 * Chain 2: Result of selecting a conversational strategy for the next response.
 */
@Serializable
data class StrategySelection(
    val strategy: Strategy,
    val reasoning: String
)

/**
 * Chain 3: Result of generating a response/question for the user.
 */
@Serializable
data class Response(
    val response: String,
    val reasoning: String,
    val strategy: Strategy
)

/**
 * Wrapper class for parsing themes when they come in a 'themes' field.
 * This is needed because sometimes the LLM returns themes in a JSON object with a themes array.
 */
@Serializable
data class ThemesWrapper(val themes: List<ConversationalTheme>)

/**
 * Response from theme reinterpretation LLM call (Chain A).
 */
@Serializable
data class ThemeReinterpretationResponse(
    val themeInterpretation: String,  // Natural, conversational summary
    val reasoning: String             // LLM's reasoning for interpretation choices
)

/**
 * Response from action suggestion LLM call (Chain B).
 */
@Serializable
data class ActionSuggestionResponse(
    val actionSuggestion: String,     // Personalized action recommendation
    val reasoning: String             // LLM's reasoning for suggestion
)

/**
 * Result of phase suggestion analysis (Chain A output).
 */
@Serializable
data class PhaseSuggestionResult(
    val suggestedPhase: String,       // ConversationPhase name as string
    val targetWishId: Int?,          // Wish ID (if phase requires existing wish)
    val reasoning: String            // LLM's reasoning for phase selection
)

/**
 * Response from transition message crafting (Chain B output).
 */
@Serializable
data class TransitionMessageResponse(
    val message: String,             // Complete transition message
    val reasoning: String            // LLM's reasoning for message crafting
)



/**
 * Structured response from TransitionChain containing phase suggestion and routing information.
 */
@Serializable
data class TransitionActionPlan(
    val actionSuggestion: String,      // Complete message to speak to user (theme + command mode)
    val themeSummary: String,          // Pure LLM theme summary only (for playback)
    // not for MVP!!
//    val proposedPhase: String,         // ConversationPhase name (serialized as string)
//    val targetWishId: Int?,           // Wish ID (if phase requires existing wish)
//    val reasoning: String,            // Brief explanation of phase choice
    val sessionName: String? = null,  // Generated session name for conversation history
    val themes: List<ConversationalTheme> = emptyList()  // Themes processed during transition
) {
    /**
     * Converts the proposedPhase string back to ConversationPhase enum.
     * NOT FOR MVP!
    fun getConversationPhase(): ConversationPhase? {
        return try {
            ConversationPhase.valueOf(proposedPhase)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
     */
}

/**
 * Data class returned by DialogueChain.processTurn(), containing all necessary information
 * for the ConversationAgent to process the agent's turn.
 */
data class DialogueChainResponse(
    val text: String,                            // The text to be spoken by the agent.
    val reasoning: String,                       // The LLM's reasoning for the response/decision.
    val strategy: Strategy,                      // The conversational strategy employed.
    val themesForThisTurn: List<ConversationalTheme>, // Themes active after this turn's processing.
    val transitionDecision: TransitionDecision?      // Decision on transitioning, if applicable.
)



object Prompts {
    /**
        * Enhanced system instructions integrating manifestation theory and Core Loop context.
        */
    val systemInstructions = """
        # ROLE & IDENTITY
        You are Genie – a manifestation coaching assistant and trusted advisor in the VoxManifestor app. 
        You serve as both a supportive conversational partner and an intelligent scribe, helping users 
        navigate their manifestation journey through structured reflection and goal clarification.

        # MANIFESTATION FRAMEWORK CONTEXT
        You operate within a 14-step manifestation process where users:
        - Establish detailed representations of their desired states (Steps 1-4: Discovery Phase)
        - Engage in regular conversations for clarity building (Steps 5-8: Clarity Building Phase)
        - Chart pathways from present to desired states (Steps 9-10: Pathway Creation)
        - Transform limiting beliefs and visualize success (Steps 11-13: Transformation Phase)
        - Integrate learnings through ongoing practice (Step 14: Integration Phase)

        The Check-In specifically serves Steps 5-8: "regular and ongoing discussions" that help users 
        gain clarity on their Present and Desired States across various life contexts.

        # CORE LOOP AWARENESS
        The Check-In is the "INITIALIZE" phase of the Core Loop - the daily briefing that:
        - Re-engages users and creates continuity from previous sessions
        - Provides space for voice journaling and reflection on current experiences
        - Identifies what needs attention in their manifestation journey
        - Leads to collaborative focus selection for deeper work

        As the conversation progresses, you help users transition from general reflection to 
        specific manifestation work (wishes, concepts, affirmations) when they explicitly request it.

        # CHECK-IN PURPOSE & GOALS
        During Check-In, you facilitate "assisted voice journaling" by:
        - Creating a safe space for users to express current thoughts, feelings, and experiences
        - Helping them reflect on recent events and their relationship to their manifestation goals
        - Identifying themes, patterns, and insights that emerge from their sharing
        - Flagging important points for potential deeper exploration in subsequent Core Loop phases
        - Building rapport and connection that supports their ongoing manifestation journey

        # CONVERSATION PRINCIPLES
        • Ask ONE genuinely curious question per reply
        • Keep questions short (<20 words) and open-ended
        • Listen deeply and build on what the user shares
        • Try to obtain actual information from the user about their thought processes. What happened?  What do you want to do about it?  What do you think caused that? etc.
        • Mirror back what you hear to encourage deeper reflection
        • Maintain warm, supportive energy while being professionally insightful
        • Allow natural conversation flow - don't rush toward transition
        • Only suggest moving to specific manifestation work when user explicitly requests it

        # CHECK-IN STAGES
        1. OPENING – Free-flow exploration through reflective questions that help users express 
           their current state, recent experiences, and top-of-mind concerns related to their goals
        2. TRANSITION – When user explicitly requests specific manifestation work (wishes, concepts, 
           affirmations) or asks "what's next", provide a bridging response that honors their request

        Remember: You are both a conversational partner AND an intelligent manifestation scribe, 
        helping users clarify their path toward their desired reality through structured reflection.
    """.trimIndent()

}

