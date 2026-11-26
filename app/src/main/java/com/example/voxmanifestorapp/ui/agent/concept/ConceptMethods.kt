package com.example.voxmanifestorapp.ui.agent.concept

import android.util.Log
import com.example.voxmanifestorapp.data.ConceptActionState
import com.example.voxmanifestorapp.data.ConceptBuildingContext
import com.example.voxmanifestorapp.data.ConceptType
import com.example.voxmanifestorapp.data.DataLoadingState
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.ConceptToolLibrary
import com.example.voxmanifestorapp.ui.agent.ConversationAgent.Screen
import com.example.voxmanifestorapp.ui.agent.ConversationType
import com.example.voxmanifestorapp.ui.agent.DialogueState
import com.example.voxmanifestorapp.ui.agent.Speaker
import com.example.voxmanifestorapp.ui.agent.ToolResult
import com.example.voxmanifestorapp.ui.concept.ConceptViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** We're not using any concept screen stuff for now **
 *
// called from Concept Screen when mounted so agent knows its there and has access to view model
/**
 * todo: currently called from NavHost directly. May need to be mediated by agent cortex in future
 * Observes the loading state of the concept view model to determine when
 * ```
 *      the concept screen is ready for interaction.
 * ```
 * Also sets up collections from the concept screen state API to maintain awareness of user
 * changes in UI on concept screen
*/
fun observeConceptViewModel(viewModel: ConceptViewModel) {
logStatus("Starting to observe ConceptViewModel loading state")

currentConceptViewModel = viewModel

val agentId = System.identityHashCode(this)
val viewModelId = System.identityHashCode(viewModel)
Log.d("AgentDebug", "Agent $agentId observing ConceptViewModel $viewModelId")

/**
 * monitors state of data loading in concept screen so brain conversation can be auto-initiated
*/
scope.launch {
viewModel.dataLoadingState.collect { state ->
when (state) {
DataLoadingState.Ready -> {
logStatus("ConceptViewModel data is ready", StatusColor.Go)

// Get the current context
currentScreen.value = Screen.Concept

// If we were directed to build concepts, initiate that process
if (currentConversation == ConversationType.ConceptBuilding) {
logStatus("Auto-initiating concept building")
speak("Data loaded.")
// Auto-start concept building conversation
scope.launch { initiateConceptBuilding() }
}
}

DataLoadingState.Error -> {
logStatus("Error loading concept data", StatusColor.Stop)
// Optionally handle the error case
speak("I'm having trouble loading the concept data. Let's try again later.")
}

DataLoadingState.Loading -> {
logStatus("ConceptViewModel is still loading data")
// Just wait, no action needed
}
}
}
} // data loading viewmodel.scope
}

// Called when leaving ConceptScreen
suspend fun onConceptScreenExit() {
logStatus(
"onConceptScreenExit called - terminating conversation and navigating away",
StatusColor.Pause
)
terminateConversation()
speak("Concept screen closed.")
currentScreen.value = Screen.Main
logStatus("Exiting concept screen")
mainScreenState.clearSelection()

agentCortex.updateConversationPlan(null)
agentCortex.updateCurrentAction(ConceptActionState.INITIATE)
}

private fun createToolLibrary(): ConceptToolLibrary {
return currentConceptViewModel?.let {
ConceptToolLibrary(
agent = this,
conceptViewModel = it,
conceptRepository = conceptRepository
)
}
?: throw IllegalStateException("Cannot create tool library: concept view model is null")
}

/**
 * Initiates the concept building process, sets up initial prompt & conversation history Normally
 * loaded from observeConceptViewModel() -> triggered when concept screen is loaded. Sets up state
 * variables, launches the conversation scope & loop between agent <-> brain service
 * initiateConceptBuilding(): leads to -->
 * ```
 *      getNextBrainDecision() - Makes API calls to the LLM
 *      Tool execution - e.g., QuestionTool.execute()
 *      Listening for user responses
 *      Processing responses and making further decisions
 * ```
 * ::: All should be cancellable by toggleConversation() ^^ by quitting the conversation scope set
 * up here.
*/
suspend fun initiateConceptBuilding() {
val agentId = System.identityHashCode(this)
Log.d("AgentDebug", "Agent $agentId initiating concept building")

/** Reset variables for concept building */
agentCortex.updateConversationPlan(null)

// Only create new scope if none exists (preserve existing core loop scope)
if (conversationScope == null) {
conversationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
logStatus("INITIATING CONCEPT BUILDING>>>")

/** launch of conversation scope process for management and interruptions of conversations */
conversationScope?.launch {
try {
currentConceptViewModel?.let { viewModel ->
val toolLibrary = createToolLibrary()

Log.d("CONTEXT", "About to gather context..")

// get information from the concept nexus about current wish and concepts to be worked
// with.
val context = viewModel.getConceptBuildingContext()
Log.d("CONTEXT", "Context should have been gathered from viewmodel now.")

if (context == null) {
speak("I'm sorry, I can't establish adequate context to initiate this process.")
logStatus("ERROR: Context variable couldn't initialise.")
return@launch
}

// get initial setup from brain
val initialPrompt = getInitialPrompt(context, toolLibrary)

// add this prompt to very beginning of conversation history, so brain always starts with
// adequate context.
addToHistory(
speaker = Speaker.Agent,
content = initialPrompt,
phase = agentCortex.coreLoopState.value.currentPhase,
isVisible = false // hide system prompts from UI
)

/*  let's not show this now as its clogging up the logs.
logStatus(
"*** Crafted initial prompt to brain & front-loaded into conversation history: \n" +
"${initialPrompt}"
)

*/

// send initial prompt to brain and process response.
// brain needs: context, toolLibrary, currentPlan...
// todo: do we need currentPlan to be state variable?!?
getNextBrainDecision(context, toolLibrary)
}
?: run {
logStatus("Couldn't initiateConceptBuilding(): concept view model not found")
return@launch
}
} catch (e: CancellationException) {
/** execute here when the conversation scope process is cancelled */
logStatus("Concept building was cancelled", StatusColor.Pause)
} catch (e: Exception) {
logStatus("getNextBrainDecision() failed: Exception $e")
}
} // conversation scope close bracket
}

/**
 * Collects and delivers information about the current state of the concept screen to the Brain.
 * Request initial guidance from brain service Begins a conversation with the user based on the
 * brain's decision
*/
private suspend fun getNextBrainDecision(
context: ConceptBuildingContext,
toolLibrary: ConceptToolLibrary,
) {

// first we should craft the working prompt
val prompt = getWorkingPrompt(context, toolLibrary)

// Get initial decision from brain (send context and tool library from here)
// Update to Thinking state while waiting for the LLM
agentCortex.updateDialogueState(DialogueState.Thinking)
var brainDecision = brainService.getNextConceptAction(prompt)
// Return to appropriate state after getting response
agentCortex.updateDialogueState(DialogueState.Speaking)

logStatus("Obtained a brainDecision!!! \n${brainDecision}")

// analyses the brain's result based on whether we were successful or not
brainDecision.fold(
onSuccess = { decision ->
// BrainDecision(toolname, parameters, action) returned from brain service
agentCortex.updateConversationPlan(decision.plan)

logStatus(
buildString {
append(
"""
Updated conversation plan: Goal: ${decision.plan.goal}
Steps:
${
decision.plan.steps.mapIndexed { index, step ->
"""
Step ${index + 1}:
Intent: ${step.intent}
Description: ${step.description}""".trimIndent()
}.joinToString("\n")
}
Current step: ${decision.plan.currentStepIndex + 1}/${decision.plan.steps.size}
""".trimIndent()
)
}
)

// Execute the chosen tool
val tool =
toolLibrary.getTool(decision.toolName)
?: run {
logStatus("Tool ${decision.toolName} not found", StatusColor.Stop)
speak("I encountered an error with my tools. Let's try again.")
return@fold
}

// Execute tool and handle result
when (val result = toolLibrary.executeTool(decision)) {
is ToolResult.Success -> {
// Update our brain conversation state
agentCortex.updateCurrentAction(decision.action)

/**
 * looks like we need to simply feed whatever reply the llm supplies us with and
 * wait for the user to communicate back to the llm. the llm can then decide if it
 * wants to do an action while maintaining the conversation.
*/
}

is ToolResult.Error -> {
logStatus("Tool execution failed: ${result.message}", StatusColor.Stop)
speak("I encountered an error. Let's try a different approach.")
// Could add retry logic here if needed
resetConversation()
}
}
},
onFailure = { error ->
logStatus("Brain decision error: ${error.message}", StatusColor.Stop)
speak("I'm sorry.  My brain isn't working.  Let's try again in a moment.")
resetConversation()
}
)
}

/**
 * initial prompt sets up everything for the brain: how to communicate, working through stages in
 * the conversation, planning the conversation, using tools, etc.
*/
fun getInitialPrompt(context: ConceptBuildingContext, toolLibrary: ConceptToolLibrary) =
"""
 * Overall Background & Context. *

You are a manifestation genie inside the 'vox manifestor app'
You help the user manifest their wishes using the methods and communication style
of an expert coach and consultant to help users develop their wishes.

By helping the user think through these component ideas and store them in this app, we are helping
them mentally bring themselves closer to the achievement of their wishes.

Your communication style reflects the precision and insight of a highly skilled coach:
- Clear, professional language that draws out specific insights
- Questions that help users articulate vague wishes into concrete concepts
- Systematic approach to understanding the process of achieving goals

The user has just entered a 'concept screen' within the app.  On this screen you will help the user edit
different concepts.  Here is a summary of the current concept you are working on:

 * Current Concepts You Are Helping User With *

State Transformation:
Description: ${ConceptType.StateTransformation.description}
Purpose: ${ConceptType.StateTransformation.purpose}
Guidance: ${ConceptType.StateTransformation.guidance}

Component States:

1. Present State (Current Reality):
Description: ${ConceptType.Present.description}
Purpose: ${ConceptType.Present.purpose}
Guidance: ${ConceptType.Present.guidance}
Required Items: ${ConceptType.Present.requiredItems}

2. Desired State (Future Vision):
Description: ${ConceptType.Desired.description}
Purpose: ${ConceptType.Desired.purpose}
Guidance: ${ConceptType.Desired.guidance}
Required Items: ${ConceptType.Desired.requiredItems}

Concepts in this app are made up of a number of text descriptions (usually 3, but may vary) by the user that
describe the relevant component of the current manifestation.

We want to establish a single sentence or AT MOST two sentences for each item within a concept.
Don't try to extract too much detail when gathering these sentences.

 * Planning and executing a goal-oriented conversation: *

These are the potential concept action states.  The idea is you move sequentially through these, and use
them as a framework to guide your conversation with the user.

${ConceptActionState.getActionDescriptions()}

You should also inform the agent what action decision you are making and set the current state with the
action key.

Conversationally, try to be concise, and tend toward multi-turn conversations rather than long diatribes.
Do not ask more than one question within a single conversational 'turn', or try to address more than one
topic at once.

If you want to make multiple points, make them one
step at a time, while awaiting user feedback / confirmation / response after each one.

You can plan up to 5 steps in the current conversation.  This can help you keep track of where you're at in a
relatively complex, multi-turn conversation.

Each of your responses must include a complete conversation plan, either:
- The existing plan with updated current step index
- A new plan if none exists or current plan needs revision

The goal reminds you the overall outcome youre trying to achieve.
The steps are how to get there, each step usually being a single relatively simple utterance.
The description describes what communication you want to do, and the intent explains the purpose of that message.
Remember the user will be hearing your words translated into a voice, so keep it short and to the point.
You should update the currentStepIndex as you work through the conversation plan.

Once you complete the final step in this sequence, you can start another conversation sequence or simply go idle,
informing the user that this is what you are doing.

 * Using tools to help you achieve your goal with the user *

You have a list of tools you can use to help the user fill these out.  If possible, when querying the user,
aim to have the user list out the elements of the concept in one chunk, which you can then analyse and
break down into the requisite number of items.  If, however the user has not presented enough information,
or you think they need some more clarity in what they are saying, feel free to ask them to continue
telling you about the item, and use the conversation history below to save as detailed concept info as you can.

Try your best to separate different issues / concerns out into different concept items.  Sometimes you will have to discard
something that has been mentioned in order to ensure you only represent one idea within one concept item.  You will have
to decide which 3 ideas seem most important and discard the rest.

Do not make your questions too long if possible.  Be succinct and gather information over multiple questioning rounds
rather than trying to do everything all at once.

Available tools:
${toolLibrary.getToolDescriptions()}

 *** Response format ***
This is the response format we require, including the planning steps and tool details, every time.
Even if the plan doesn't change, you should still send your latest understanding of the plan each time.

{
"plan": {
"goal": string,
"steps": [
{
"description": string,
"intent": string
}
],
"currentStepIndex": number,
}
toolName: [TOOL_NAME]
parameters: {
param1: value1
param2: value2
},
action: [CONCEPT_ACTION_STATE]

}

 * Additional guidance on working through conversational stages *

When all concept items are filled (Present State has ${ConceptType.Present.requiredItems} items and
Desired State has ${ConceptType.Desired.requiredItems} items), proceed directly to analyzing the relationship
between the concepts. Use the questionTool to present your analysis and ask the user if they want to make any
adjustments based on the relationships you've identified.

Remember: Always use the questionTool when expecting a response from the user, even during analysis.
Your analysis should focus on:
1. How well each Present State item corresponds to a Desired State item
2. Whether the transformations between states are clear and achievable
3. Any potential gaps or unclear relationships that might need refinement

Only ask for clarification when you yourself have obtained some kind of insight.  Don't ask the user for additional
insights about the relationship between present and desired state.  This is your job to figure out.
Look at the 3 analysis points above and determine any obvious problems with the user's present or desired state definitions.
If it looks reasonable, don't be afraid to end the conversation by saying something like

"This looks good.  Let me know if you need anything else."

Example analysis structure:
{
"plan": { ... <plan json details not shown here> ... }

"toolName": "askQuestion",
"parameters": {
"question": "I've analyzed the relationships between your states: [brief analysis]. I'd like to suggest [your suggestion (only 1 at once)"
},
"action": "ANALYZE_CONFIRM"
}

 * Current Status of Concepts and their Contents, related to Current Wish: (${context.manifestationTitle}) *
${
context.conceptData.entries.joinToString("\n") { (type, items) ->
val required = type.requiredItems ?: 0
buildString {
append("${type::class.simpleName}: ${items.size}/$required items\n")
// Add completion status
val isComplete = items.size >= required
append("Status: ${if (isComplete) "(COMPLETE)" else "(INCOMPLETE)"}\n")
items.forEachIndexed { index, item ->
append("  ${index + 1}. ${item.content}\n")
}
}
}
}

Do NOT add any quotes or text before the opening bracket { or after the closing bracket } at the beginning and end of your response!

""".trimIndent()

/**
 * the working prompt is sent each time we prompt the brain, as it provides valuable updates to
 * the app's existing state
*/
private fun getWorkingPrompt(context: ConceptBuildingContext, toolLibrary: ConceptToolLibrary) =
"""
Current Concept Status for wish '${context.manifestationTitle}':
${
context.conceptData.entries.joinToString("\n") { (type, items) ->
buildString {
append("${type::class.simpleName}: ${items.size}/${type.requiredItems} items\n")
items.forEachIndexed { index, item ->
append("  ${index + 1}. ${item.content}\n")
}
}
}
}

Current Action State: $currentAction

Current Conversation Plan:
${
conversationPlan.value?.let { plan ->
"""
Goal: ${plan.goal}
Progress: Step ${plan.steps + 1} of ${plan.steps.size}

Steps:
${
plan.steps.mapIndexed { index, step ->
"""
Step ${index + 1}: ${if (index == plan.currentStepIndex) ">>> CURRENT <<<" else ""}
Description: ${step.description}
Intent: ${step.intent}
""".trimIndent()
}.joinToString("\n\n")
}
"""
} ?: "No active conversation plan"
}

 * Conversation History *
${
buildString {
if (conversationHistory.value.isNotEmpty()) {
append("\nConversation History:\n")
conversationHistory.value.forEach { entry ->
append("${entry.speaker}: ${entry.content}\n")
append("(State: ${entry.phase})\n")
}
}
}
}

}
""".trimIndent()

suspend fun closeConceptScreen() {
onConceptScreenExit()

withContext(Dispatchers.Main) { navigationManager.navigateBack() }
}

/** Launches concept screen as implementation of specific core loop phases */
private suspend fun launchConceptScreenForPhase(wishId: Int?, phase: ConversationPhase) {
if (wishId != null) {
val wish = repository.getManifestationById(wishId)
if (wish != null) {
// Set conversation type for concept building
agentCortex.updateConversationType(ConversationType.ConceptBuilding)

speak("Let's work on ${phase.description.lowercase()} for ${wish.title}")

withContext(Dispatchers.Main) { navigationManager.navigateToConceptScreen(wish.id) }
// Concept screen will auto-initiate and access phase context via CoreLoopState
} else {
logStatus(
"⚠️ Wish with ID $wishId not found, continuing with core loop",
StatusColor.Pause
)
changeDialogueState(DialogueState.ExpectingInput(VoxInputType.BRAIN_RESPONSE))
}
} else {
logStatus("⚠️ No wish ID provided for concept screen phase", StatusColor.Pause)
changeDialogueState(DialogueState.ExpectingInput(VoxInputType.BRAIN_RESPONSE))
}
}

 */