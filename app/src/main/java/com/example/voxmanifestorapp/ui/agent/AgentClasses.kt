package com.example.voxmanifestorapp.ui.agent

import com.example.voxmanifestorapp.data.ConceptActionState
import com.example.voxmanifestorapp.data.ConceptType
import kotlinx.serialization.Serializable


/* Clever Brain-related process-management storage classes */

// 
data class BrainDecision(
    val toolName: String,
    val parameters: ParametersWrapper,
    val action: ConceptActionState,
    val plan: ConversationPlan
)

// Helper class for JSON parsing
@Serializable
data class BrainResponse(
    val toolName: String,
    val parameters: ParametersWrapper,
    val action: String,
    val plan: ConversationPlan
)

/* Clever Brain-related Conversation Data Structures
*  More general than the state-machine */

// open structure for the brain to plan out the next few steps in the conversation.
@Serializable
data class ConversationPlan(
    val goal: String,
    val steps: List<ConversationPlanStep>,
    val currentStepIndex: Int
)

// an individual utterance within the brain's overall conversation plan
@Serializable
data class ConversationPlanStep(
    val description: String,    // Internal description of this step's purpose
    val intent: String         // What we want to achieve with this step
)

/* The Agent manages the conversation flow above all else */

// A single turn in the conversation for the conversation history
data class ConversationEntry(
    val speaker: Speaker,       // who is speaking
    val content: String,        // what was said
    val timestamp: Long = System.currentTimeMillis(),        // when
    val phase: ConversationPhase,     // which step we were on
    // whether or not to show in the conversation 'chat' display in the UI.
    // we don't show raw prompt data but we do include it in the conversation history as it provides crucial context for the Brain.
    val isVisible: Boolean = true,
    // LLM-only annotations to help track conversation state and strategies
    val metadata: Map<String, String> = emptyMap(),
)

enum class Speaker {
    Agent,
    User,
    SessionName
}

// contains what to say and how to display it
data class ConversationAction(
    val speak: String,
    val expectResponse: Boolean = false,
    val pauseAfter: Long = 1000,
    val displayState: DisplayState = DisplayState.None
)

// orders our conversations into a sequence - used in affirmation tool only.
// todo: integrate this into the same interface as other tools
data class ConversationSequence(
    val actions: List<ConversationAction>,
    val onComplete: suspend () -> Unit = {}
)

interface ConversationTool {
    suspend fun execute(data: Any?): ConversationSequence
}

/* State variables used by Agent to manage Dialogue and Visual UI that accompanies it */

// represents what should be shown on screen during conversations
sealed class DisplayState {
    object None : DisplayState()

    object StartSession : DisplayState()
    object EndSession : DisplayState()

    data class ShowAffirmation(
        val text: String,
        val isRepeating: Boolean = false,
        val repeatCount: Int = 0
    ) : DisplayState()

    data class ShowConversation(
        val conceptType: ConceptType,
        val manifestationTitle: String,
        val conversationHistory: List<ConversationEntry>,
        val isProcessing: Boolean = false
    )
}

// the dialogue state - manages immediate interaction / exchange between genie voice and user
sealed class DialogueState {
    data object Speaking : DialogueState()
    data class ExpectingInput(val inputType: VoxInputType) : DialogueState()
    data object Idle : DialogueState()
    data object Thinking : DialogueState() // When agent is waiting for LLM response
}

/* Simple Reactive State Machine Graphs
*  These are the states that the basic wish selection and input screen uses.
*  Not intelligent, but enough for the highly constrained context of the main screen.
* */

enum class VoxInputType {
    FREEFORM,       // used when inputting long form text or otherwise unclassified inputs
    YES_NO,
    COMMAND_SELECTION,       // used when selecting what process to do
    BRAIN_RESPONSE,         // used when info is to be passed back to brain
}

// lists high level conversation types that are possible
sealed class ConversationType {
    // command mode handles wish collection and selection through internal step management
    data object CommandMode : ConversationType()
    // concept building happens in the concept screen, when we are editing the present and desired state
    data object ConceptBuilding: ConversationType()
    // concept menu happens as soon as we enter the concept screen
    data object ConceptMenu: ConversationType()
    // core loop represents the structured coaching conversation journey
    data object CoreLoop: ConversationType()
    // wish creation handles conversational wish creation with theme-based generation
    data object WishCreation: ConversationType()
    
    // Legacy conversation types - to be phased out
    @Deprecated("Use CommandMode with ConversationStep.WishStep instead")
    data object WishCollection : ConversationType()
    @Deprecated("Use CommandMode with ConversationStep.WishSelectionStep instead")
    data object WishSelection : ConversationType()
}

// states within a specific conversation flow
sealed class ConversationStep {

    /* conversation steps in adding a wish to main screen */
    sealed class WishStep : ConversationStep() {
        // what is the wish?
        data object AskForWish : WishStep()
        // receive wish input string
        data object CaptureWish : WishStep()
        // speak wish string back to user, ask if ok?
        data object CheckWish : WishStep()
        // receive yes-no from user to repeat or quit
        data object CaptureValidation : WishStep()
    }

    /* conversation steps in selecting a wish and the process to conduct mainscreen */
    sealed class WishSelectionStep : ConversationStep() {
        // which wish do you want to select?
        data object SelectWish : WishSelectionStep()
        // receive input wish #
        data object CaptureWish :  WishSelectionStep()
        // give menu option for commands
        data object SelectProcess : WishSelectionStep()
        // receive input process
        data object CaptureProcess : WishSelectionStep()
    }

}
