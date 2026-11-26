package com.example.voxmanifestorapp.ui.agent

import ManifestationRepository
import android.util.Log
import com.example.voxmanifestorapp.data.ConceptActionState
import com.example.voxmanifestorapp.data.ConceptBuildingContext
import com.example.voxmanifestorapp.data.ConceptRepository
import com.example.voxmanifestorapp.data.ConceptType
import com.example.voxmanifestorapp.data.ConversationRepository
import com.example.voxmanifestorapp.data.DataLoadingState
import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.affirmations.AffirmationManager
import com.example.voxmanifestorapp.ui.agent.checkin.CheckInDialogueChain
import com.example.voxmanifestorapp.ui.agent.checkin.CheckInStage
import com.example.voxmanifestorapp.ui.agent.checkin.ConversationalTheme
import com.example.voxmanifestorapp.ui.agent.checkin.Response
import com.example.voxmanifestorapp.ui.agent.checkin.TransitionActionPlan
import com.example.voxmanifestorapp.ui.agent.checkin.TransitionChain
import com.example.voxmanifestorapp.ui.agent.commands.*
import com.example.voxmanifestorapp.ui.agent.coreloop.CoreLoopManager
import com.example.voxmanifestorapp.ui.agent.navigation.NavigationManager
import com.example.voxmanifestorapp.ui.agent.timer.CheckInTimerManager
import com.example.voxmanifestorapp.ui.agent.timer.TimerIntent
import com.example.voxmanifestorapp.ui.agent.utilities.WishUtilities
import com.example.voxmanifestorapp.ui.agent.utilities.fetchEnhancedWishDataForTransition
import com.example.voxmanifestorapp.ui.agent.utilities.fetchUserWishesForContext
import com.example.voxmanifestorapp.ui.agent.utilities.handleError
import com.example.voxmanifestorapp.ui.agent.voice.*
import com.example.voxmanifestorapp.ui.basevox.TextToSpeechRepository
import com.example.voxmanifestorapp.ui.basevox.VoiceManagedViewModel
import com.example.voxmanifestorapp.ui.concept.ConceptViewModel
import com.example.voxmanifestorapp.ui.main.MainScreenState
import com.example.voxmanifestorapp.ui.statuslog.StatusMessageService
import com.example.voxmanifestorapp.ui.utils.SoundPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/*  This is our Conversational Agent, the "conversation brain" of the app
    - control recog system via VmVm; when to start & stop listening
    - receives raw text from VmVm
    - process that into commands or conversation responses
    - issue conversational prompts, conversational flow management, goal-seeking behaviour
    - decide when to speak and listen
*/

class ConversationAgent(
    private val repository: ManifestationRepository,
    private val ttsRepo: TextToSpeechRepository,
    private val brainService: BrainService,
    private val scope: CoroutineScope,
    private val statusService: StatusMessageService,
    private val conceptRepository: ConceptRepository,
    private val mainScreenState: MainScreenState,
    private val agentCortex: AgentCortex,
    private val conversationRepository: ConversationRepository,
    private val soundPlayer: SoundPlayer
) {
    companion object {
        /**
         * Master switch for the ambient voice command-listening paradigm.
         *
         * When false (default), the always-on keyword listener is gated off: idle command
         * keywords (STOP / HELP / etc.) no longer fire actions or speech app-wide. Active-
         * conversation content routing (CoreLoop / ConceptBuilding / check-in) is unaffected —
         * the gate sits after those branches in [handleVoiceInput].
         *
         * Command-handling code (CommandMode, CommandFunctions, VoiceCommandEntity, routing)
         * is retained intact: flipping this to true restores the legacy ambient routing exactly.
         * Expected to return later behind an explicit press/hold-to-activate button (see
         * docs/DESIGN-BRIEF-voice-command-mode.md, RD-2), at which point this const becomes
         * runtime state. Bead: PAI-kx0.2.1.
         */
        const val COMMAND_LISTENING_ENABLED = false
    }

    // Instance identification for debugging
    private val agentInstanceId = System.identityHashCode(this)
    private val TAG = "genie"

    // Add flag to track first response
    private var isFirstResponse = true
    val sessionId = agentCortex.currentSessionId

    // control voice input and output (Text to Speech and Speech to Text)
    private var voiceManager: VoiceManagedViewModel? = null

    /** Agent Cortex plug-in : states that will change based on user interactions */
    val dialogueState =
        agentCortex.dialogueState // manage back and forth dialogue states between genie and user
    val currentDisplayState = agentCortex.displayState // Display state for affirmations UI
    val conversationHistory =
        agentCortex.conversationHistory // conversation history for brain conversations in concept

    // screen
    val conversationPlan = agentCortex.conversationPlan // receive brain's plan for the conversation
    val currentAction =
        agentCortex.currentAction // track where the brain is at with its conversations
    val checkInState =
        agentCortex.checkInState // state info for the check-in stage of the core loop.

    // get the current slot index from the main screen's API for interacting with wishes
    private val currentSlotIndex = mainScreenState.selectedSlot

    val navigationManager = NavigationManager() // Navigation management

    private val checkInTimerManager =
        CheckInTimerManager(
            // check in timer manager for timing check in
            agentCortex = agentCortex,
            coroutineScope = scope,
        )

    val affirmationManager =
        AffirmationManager( // offload affirmations functions to affirmation mgr.
            brainService = brainService,
            agentCortex = agentCortex,
            logger = ::logStatus,
            scope = scope
        )

    private val voiceProcessor =
        VoiceProcessor(
            ttsRepo = ttsRepo,
            agentCortex = agentCortex,
            logger = ::logStatus,
            scope = scope
        )

    // WishUtilities instance for centralized repository access
    private val wishUtilities = WishUtilities(repository)

    private val commandMode =
        CommandMode(
            wishUtilities = wishUtilities,
            agentCortex = agentCortex,
            mainScreenState = mainScreenState,
            logger = ::logStatus,
            voiceProcessor = voiceProcessor,
            navigationManager = navigationManager,
            speak = ::speak,
            toggleConversation = ::toggleBrainConversation
        )

    /** agent's working memory array (internal state management) */
    // provides the most recently spoken command, as filtered through the voice command entity
    // commandState

    /** BRAIN: conversation management variables */
    // most important conversation management variable: scope variable allows cancelling existing
    // conversation at any point
    private var conversationScope: CoroutineScope? = null

    // Use AgentCortex as single source of truth for conversation type
    private val currentConversation: ConversationType?
        get() = agentCortex.conversationType.value

    // Legacy state variables removed - now handled by CommandMode

    /*      listeningjob deprecated for now in favour of manual button press.
    // manage pauses in the user's responses
    private var lastUtteranceTime = MutableStateFlow<Long>(0L)
    private var listeningJob: Job? = null
    private val PAUSE_THRESHOLD = 10000L         // 10 seconds pause
     */

    // track which screen the app is on
    val currentScreen = MutableStateFlow<Screen>(Screen.Main)

    enum class Screen {
        Main,
        Concept
    }

    // agent needs access to concept view model so it can control the visual interface
    private var currentConceptViewModel: ConceptViewModel? = null

    // set up the flow of manifestations from the repository into this viewmodel
    val allManifestations: Flow<List<Manifestation>> = repository.getAllManifestations()

    // the data type of the argument navigate is a lambda function receiving the manifestation id and
    // producing the navigation function
    fun setConceptNavigation(navigate: (Int) -> Unit) {
        navigationManager.setConceptNavigation(navigate)
    }

    // todo: integrate this tool with the other brain tools at some point?
    /* agent brain variables */

    // flag to prevent multiple conversation toggles at once
    private val isToggleInProgress = AtomicBoolean(false)

    /** Setting up DialogueChain and TransitionChain singletons **/

    // DialogueChain for check-in conversation management
    private val checkInDialogueChain =
        CheckInDialogueChain(
            brainService = brainService,
            agentCortex = agentCortex,
            logger = { msg, color -> logStatus(msg, color) }
        )

    // TransitionChain for generating intelligent transition suggestions
    private val transitionChain =
        TransitionChain(
            brainService = brainService,
            commandMode = commandMode,
            logger = { msg, color -> logStatus(msg, color) }
        )

    // logger
    private fun logStatus(message: String, color: StatusColor = StatusColor.Default) {
        Log.d(TAG, message)
        statusService.addStatusMessage("[" + TAG + "] " + message, color)
    }

    init {
        Log.d("AgentInit", "New ConversationAgent instance created with hash: ${hashCode()}")

        val agentId = System.identityHashCode(this)
        Log.d("AgentDebug", "Agent instance created with ID: $agentId")

        // Setup monitoring of UI state changes
        monitorUiIntents()

        // set up sessionId first thing
        // we set it up from here because agentCortex doesn't have access to repository nor should it
        // conversationAgent is the Agent, so it should set up agentCortex accordingly
        scope.launch {
            agentCortex.updateSessionId(
                conversationRepository.startNewSession()
            )
        }

        /** We're not using this in MVP: wishes and wish slots aren't visible for the check-in **/
        // Monitor slot selection from MainScreenState
        //monitorSlotSelection()
    }

    private fun monitorUiIntents() {
        // Monitor interrupt requests
        /**
         * monitor interrupt request so we can halt agent's speech instantly this is a stop-gap solution
         * instead of being able to actually interrupt the agent with speech, which will be a later
         * feature
         */
        scope.launch {
            agentCortex.uiIntentFlow.collect { intent ->
                Log.d(TAG, "Agent received UI Intent: ${intent::class.simpleName}")
                when (intent) {
                    is AgentCortex.UiIntent.RequestInterrupt -> {
                        logStatus("UI Intent: Interrupt speech requested", StatusColor.Pause)
                        interruptSpeech()
                        resetConversation()
                    }

                    is AgentCortex.UiIntent.RequestToggleConversation -> {
                        logStatus(
                            "UI Intent: Toggle conversation requested (current state: ${currentConversation})",
                            StatusColor.Pause
                        )
                        toggleBrainConversation()
                    }

                    is AgentCortex.UiIntent.RequestTerminateConversation -> {
                        logStatus("UI Intent: Terminate conversation requested", StatusColor.Pause)
                        terminateConversation()
                    }

                    is AgentCortex.UiIntent.SendResponse -> {
                        logStatus("UI Intent: Send response requested", StatusColor.Go)
                        conversationScope?.launch { processUserResponse() }
                    }

                    is AgentCortex.UiIntent.NotifyConceptScreenExit -> {
                        logStatus(
                            "UI Intent: Concept screen exit notification received",
                            StatusColor.Pause
                        )
                        /** Removed concept screen for now
                        scope.launch { // Use launch if onConceptScreenExit is suspend
                        onConceptScreenExit()
                        }
                         */
                    }

                    is AgentCortex.UiIntent.InitiateCoreLoop -> {
                        logStatus("UI Intent: Core Loop initiation requested", StatusColor.Go)
                        handleCoreLoopIntent(intent.startPhase)
                    }

                    is AgentCortex.UiIntent.TimerControl -> {
                        logStatus("UI Intent: Timer control requested", StatusColor.Go)
                        conversationScope?.launch { handleTimerControl(intent.timerIntent) }
                    }
                }
            }
        }
    }

    private suspend fun toggleBrainConversation(isNetworkError: Boolean = false) {
        logStatus(
            "TBC: Entered toggleBrainConversation. isToggleInProgress: ${isToggleInProgress.get()}, conversationScope active: ${conversationScope != null}, currentConversation: $currentConversation",
            StatusColor.Default
        )
        // Ensure only one execution at a time
        if (!isToggleInProgress.compareAndSet(false, true)) {
            logStatus(
                "TBC: Toggle already in progress, ignoring duplicate request",
                StatusColor.Pause
            )
            return
        }

        val initialState = dialogueState.value
        val initialConversation = currentConversation
        // Modified: Check both conversationScope and currentConversation
        val hasActiveConversation = conversationScope != null || currentConversation != null

        logStatus(
            "toggleBrainConversation called. DialogueState: $initialState, ConversationType: $initialConversation, Active conversation: $hasActiveConversation",
            StatusColor.Pause
        )

        try {
            if (hasActiveConversation) {
                // We have an active conversation - end it
                logStatus("Ending active conversation", StatusColor.Pause)

                // Stop the timer if it's running
                checkInTimerManager.stop()

                // Stop TTS and reset display state first
                interruptSpeech()
                agentCortex.updateDisplayState(DisplayState.None)

                // Only attempt to speak confirmation if not handling a network error
                if (!isNetworkError) {
                    // Speak confirmation message BEFORE further cleanup
                    // Direct use of TTS repo to avoid complex state management
                    try {
                        speak("Conversation ended.")
                        logStatus("TBC: Successfully spoke confirmation message", StatusColor.Pause)
                    } catch (e: Exception) {
                        logStatus(
                            "TBC: Error speaking confirmation message: ${e.message}",
                            StatusColor.Stop
                        )
                    }
                } else {
                    logStatus(
                        "TBC: Skipping confirmation message due to network error",
                        StatusColor.Stop
                    )
                }

                // Explicitly set state to Idle after speaking confirmation
                updateDialogueState(DialogueState.Idle)

                // Type-specific cleanup based on conversation type
                when (initialConversation) {
                    ConversationType.ConceptBuilding -> {
                        // Concept Building specific cleanup
                        agentCortex.updateConversationPlan(null) // Only reset plan for Concept Building
                        logStatus("Concept Building conversation ended", StatusColor.Pause)
                    }

                    ConversationType.CoreLoop -> {
                        // Core Loop specific cleanup
                        val currentCoreLoopState = agentCortex.coreLoopState.value
                        if (currentCoreLoopState.currentPhase != ConversationPhase.CHECK_IN) {
                            // Keep the phase completion history but reset current phase to initial
                            agentCortex.updateCoreLoopState(
                                phase = ConversationPhase.CHECK_IN,
                                understanding = "" // Clear the current understanding
                            )
                            logStatus(
                                "Core Loop state reset to initial assessment",
                                StatusColor.Pause
                            )
                        }
                    }

                    else -> {
                        logStatus(
                            "Unknown conversation type: $initialConversation",
                            StatusColor.Stop
                        )
                    }
                }

                // Cancel the scope AFTER all other operations
                conversationScope?.cancel()
                conversationScope = null

                // Final shared cleanup
                updateConversationState(null)
                // State already set to Idle above

                logStatus("Brain conversation stopped, now in command mode", StatusColor.Pause)

            /** Toggling to START conversation **/
            } else {

                // Start a new conversation

                // Determine which type of conversation to start based on current screen
                if (currentScreen.value == Screen.Main) {
                    // Start Core Loop conversation
                    logStatus("Starting Core Loop conversation", StatusColor.Go)

                    // Important: Update conversation type BEFORE updating dialogue state
                    updateConversationState(ConversationType.CoreLoop)

                    // Now launch the conversation
                    handleCoreLoopIntent()

                } else {
                    /** Removed concept building for now
                     *
                    // Start concept building conversation
                    logStatus("Starting concept building conversation", StatusColor.Go)

                    // Important: Update conversation type BEFORE updating dialogue state
                    updateConversationState(ConversationType.ConceptBuilding)

                    // Update dialogue state to ExpectingInput BEFORE launching the conversation
                    updateDialogueState(DialogueState.ExpectingInput(VoxInputType.BRAIN_RESPONSE))

                    // Now launch the conversation
                    scope.launch { initiateConceptBuilding() }
                     */
                }
            }
        } finally {
            // Important: Always reset the flag when done
            isToggleInProgress.set(false)
        }
    }

    private fun interruptSpeech() {
        // 'hardware' speech interrupt:
        interruptSpeech(ttsRepo, ::logStatus)

        // Update dialogue state - i.e. there is no ongoing conversation. no one is speaking.
        agentCortex.updateDialogueState(DialogueState.Idle)

    }

    /** terminate the conversation with the brain */
    private fun terminateConversation() {
        logStatus(
            "TC: Entered terminateConversation. conversationScope active: ${conversationScope != null}",
            StatusColor.Default
        )
        // Cancel the entire conversation scope - this immediately stops all child coroutines
        conversationScope?.cancel()
        conversationScope = null

        // delete the current plan of action.  when we start a new conversation, this will be created
        // again.
        agentCortex.updateConversationPlan(null)

        // reset shared conversation variables
        resetConversation()

        // Reset states and release resources
        interruptSpeech()

        agentCortex.updateDisplayState(DisplayState.None)

        logStatus("Conversation terminated", StatusColor.Pause)
    }


    // this is triggered in the NavHost, just after the vm is created, then passed here
    // we can't receive it as an argument because agent is created earlier than vm (in app container)
    fun setVoiceManager(vm: VoiceManagedViewModel) {
        voiceManager = setVoiceManager(vm, agentCortex, scope, ::logStatus, ::handleVoiceInput)
    }


    /**
     * Central command routing function with conversationScope coordination
     */
    private suspend fun handleCommandMode(command: VoiceCommandEntity, text: String) {
        // Create conversationScope for command coordination
        conversationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        updateConversationState(ConversationType.CommandMode)

        conversationScope?.launch {
            try {
                // All commands go through CommandMode - single coordinated thread
                when (command) {
                    VoiceCommandEntity.SELECT -> {
                        commandMode.setSingleCommandMode(true)
                        commandMode.selectWishNumber(text)
                    }

                    VoiceCommandEntity.READ -> commandMode.handleReadCommand()
                    VoiceCommandEntity.HELP -> commandMode.handleHelpCommand(text)
                    VoiceCommandEntity.QUIT -> commandMode.handleQuitCommand()
                    else -> logStatus("Unhandled command: $command", StatusColor.Default)
                }
            } catch (e: CancellationException) {
                logStatus("Command mode cancelled", StatusColor.Pause)
            } catch (e: Exception) {
                logStatus("Error in command mode: ${e.message}", StatusColor.Stop)
            }
            // Note: Scope cleanup happens naturally when conversation completes
            // or via toggleBrainConversation() when STOP is called
        }
    }

    private suspend fun handleVoiceInput(
        command: VoiceCommandEntity,
        text: String,
        timestamp: Long
    ) {
        // this only triggers when dialogue state is ExpectingInput
        // logStatus("(handleVoiceInput) [${timestamp}] Voice Input")
        // logStatus("(handleVoiceInput) START - currentConversation=${currentConversation}")

        // compares incoming speech with previous agent utterance so it doesn't enter a self response
        // loop
        if (shouldIgnoreSpeech(text)) {
            logStatus(
                "🔇 ECHO PREVENTION: Ignoring speech that contains agent's last utterance",
                StatusColor.Pause
            )
            logStatus("🔇 Input: '$text'", StatusColor.Pause)
            return
        } else {
            logStatus("🎤 VOICE INPUT: '$text' (timestamp: $timestamp)", StatusColor.Default)
        }

        /**
         * take precedence if we're in a brain / concept conversation filter input straight to brain so
         * that we don't interrupt natural speech with command keywords (actually, add text to
         * conversation history to buffer in preparation for sending to brain
         */
        if (currentConversation == ConversationType.ConceptBuilding ||
            currentConversation == ConversationType.CoreLoop
        ) {
            // When in brain conversation and expecting input, feed everything directly to the brain
            handleUserResponse(text)
            return
        } else if (currentConversation == ConversationType.CommandMode) {
            // Route CommandMode voice input to processBackgroundInput for proper handling
            processBackgroundInput(text)
            return
        }

        // Ambient command-listening gate (PAI-kx0.2.1). Everything above this point is echo
        // prevention + active-conversation content routing and always runs. Everything below
        // (ambient STOP handler + idle keyword routing) is the always-on command paradigm,
        // which is disabled by default. Flip COMMAND_LISTENING_ENABLED to restore it exactly.
        if (!COMMAND_LISTENING_ENABLED) {
            logStatus(
                "🚫 Ambient command-listening disabled — ignoring idle keyword routing",
                StatusColor.Pause
            )
            return
        }

        // Handle STOP command (can work in both idle and active states)
        if (command == VoiceCommandEntity.STOP) {
            commandMode.handleStopCommand()
            return
        }

        // Route other commands when no active conversation
        if (currentConversation == null) {
            when (command) {
                VoiceCommandEntity.BACKGROUND -> processBackgroundInput(text)
                // All other commands go through conversationScope coordination
                else -> handleCommandMode(command, text)
            }
        }
    }

    private suspend fun processBackgroundInput(text: String) {
        logStatus("Received voice input: $text")
        when (dialogueState.value) {
            // if agent is speaking, just ignore anything the user says! >:D
            // actually, this is to prevent the agent listening and responding to itself in a loop.
            is DialogueState.Speaking -> return

            // if we're expecting input, let's process it according to some rules
            is DialogueState.ExpectingInput -> {
                // conversation type is the primary classifier for handling input
                // either collecting a wish, or selecting actions to do with a wish, or concept building

                when (currentConversation) {
                    is ConversationType.CommandMode -> {
                        // Route all CommandMode conversations to the CommandMode class
                        commandMode.processInput(text, dialogueState.value)
                    }

                    else ->
                        logStatus(
                            "Trying to process input on some type of conversation I don't know about"
                        )
                }
            }

            else -> {
                // logStatus("Unexpected dialogue state: ${dialogueState.value}")
            }
        }
    }

    suspend fun speak(message: String) {
        // store existing so we can return back to previous state once we've said something!
        val currentDialogueState: DialogueState = dialogueState.value

        // change the dialogue state to speaking when the agent is about to speak!
        updateDialogueState(DialogueState.Speaking)
        // Update latest utterance so UI shows text immediately
        agentCortex.updateLatestUtterance(message)

        speak(message, ttsRepo, ::logStatus)

        // Play completion sound to indicate control has returned to user
        try {
            soundPlayer.playSound(com.example.voxmanifestorapp.R.raw.button_click)
        } catch (e: Exception) {
            Log.w(TAG, "Could not play speech completion sound: ${e.message}")
        }
    }

    private suspend fun enterConversationLoop() {
        // Delegate to CommandMode for all conversation loop logic
        logStatus("Delegating conversation loop to CommandMode")
        commandMode.enterConversationLoop()
    }

    // this is called by the voice recog process when we're in a concept building conversation
    // all we need to do is to add the latest response to the conversation history,
    private fun handleUserResponse(text: String) {
        // Add to conversation history - the voice processor no longer handles this
        val currentPhase = agentCortex.coreLoopState.value.currentPhase
        addToHistory(Speaker.User, text, currentPhase)
    }

    private suspend fun processUserResponse() {
        val rawText =
            processUserResponse(
                voiceManager,
                agentCortex,
                ::logStatus
            )
                ?: return

        // Handle differently based on conversation type
        when (currentConversation) {
            ConversationType.CoreLoop -> {
                // For Core Loop, use the dedicated handler
                handleCoreLoopResponse(rawText)
            }

            ConversationType.ConceptBuilding -> {
                /** not doing this now */
            }
            /** Removed concept building for now
             *
            // For Concept Building, use the existing concept screen logic
            currentConceptViewModel?.getConceptBuildingContext()?.let { context ->
            // Add to conversation history first
            handleUserResponse(rawText)
            // Then get next decision from brain
            getNextBrainDecision(context, createToolLibrary())
            }
            ?: run { logStatus("Failed to get concept context", StatusColor.Stop) }
            }
             */

            else -> {
                logStatus("Conversation type not handled: $currentConversation", StatusColor.Stop)
            }
        }
    }


    suspend fun askAndListen(question: String) {

        speak(question)
        // add the question to the conversation history
        addToHistory(Speaker.Agent, question, agentCortex.coreLoopState.value.currentPhase)

        // change dialogue state within agent cortex
        updateDialogueState(DialogueState.ExpectingInput(VoxInputType.BRAIN_RESPONSE))
    }

    // reflexive agent (fixed state-machine) conversation functions
    private fun updateConversationState(newState: ConversationType?) {
        agentCortex.updateConversationType(newState)
        logStatus("Conversation state updated to $newState")
    }

    /** Now turns mic off when agent speaking, back on when expecting input from user.
     * Simples.
      */
    fun updateDialogueState(newState: DialogueState) {
        logStatus("🔥 UPDATE STATE FUNCTION CALLED with: $newState")
        logStatus("Updating dialogue state to $newState")
        if (newState is DialogueState.Speaking) {
            logStatus("🔥 SPEAKING BRANCH - voiceManager is: ${voiceManager != null}")
            voiceManager?.stopVoiceRecognition()
            logStatus("Nulling mic while agent chats shiz")
        } else if (newState is DialogueState.ExpectingInput) {
            logStatus("🔥 EXPECTING INPUT BRANCH - voiceManager is: ${voiceManager != null}")
            voiceManager?.startVoiceRecognition()
            logStatus("Activating mic for user Inputsss")
        }

        agentCortex.updateDialogueState(newState)
        // logStatus("Current dialogue state is $newStatus")
    }

    private fun resetConversation() {
        // Stop timer and reset first response flag
        checkInTimerManager.stop()
        isFirstResponse = true

        // Use AgentCortex for conversation reset
        agentCortex.resetConversationState()

        // reset internal conversation state vars
        updateConversationState(null)
        updateDialogueState(DialogueState.Idle)

        // clear the main screen's current slot index through the API, if mainscreen active.
        if (currentScreen.value == Screen.Main) {
            mainScreenState.clearSelection()
        }
    }

    private suspend fun handleTimerControl(intent: TimerIntent) {
        Log.d(TAG, "Handling timer control: $intent")
        when (intent) {
            TimerIntent.AddTime -> {
                Log.d(TAG, "Adding time to timer")
                checkInTimerManager.addTime()
            }

            TimerIntent.SubtractTime -> {
                Log.d(TAG, "Subtracting time from timer")
                checkInTimerManager.subtractTime()
            }

            TimerIntent.Stop -> {
                Log.d(TAG, "Stopping timer")
                checkInTimerManager.forceTransition()
                progressCheckIn()
            }
        }
    }

    /**
     * Adds a conversation entry to both in-memory history and persistent storage. Now includes
     * metadata support for theme persistence.
     * Metadata about themes is added into the conversation history entry so it can be restored
     * and displayed in UI.
     */
    private fun addToHistory(
        speaker: Speaker,
        content: String,
        phase: ConversationPhase,
        isVisible: Boolean = true,
        metadata: Map<String, JsonElement> = emptyMap()
    ) {
        // Check if this is a duplicate user message
        if (speaker == Speaker.User) {
            // Get the last few entries (enough to cover potential duplicates)
            val recentEntries = agentCortex.conversationHistory.value.takeLast(5)

            // Check if any recent entry from the user has identical content
            val isDuplicate =
                recentEntries.any { entry ->
                    entry.speaker == Speaker.User && entry.content == content
                }

            // Skip adding if it's a duplicate
            if (isDuplicate) {
                logStatus(
                    "Skipping duplicate user message: ${content.take(20)}${if (content.length > 20) "..." else ""}",
                    StatusColor.Default
                )
                return
            }
        }

        // Special tracking for agent responses
        if (speaker == Speaker.Agent) {
            // --- ENHANCED LOGGING: Agent response metadata tracking ---
            Log.d("AddToHistory", "=== AGENT RESPONSE METADATA TRACKING ===")
            Log.d("AddToHistory", "SessionId: $sessionId, Phase: $phase, Speaker: $speaker")
            Log.d("AddToHistory", "Content length: ${content.length} chars")
            Log.d("AddToHistory", "Metadata keys: ${metadata.keys.joinToString(", ")}")
            Log.d("AddToHistory", "Full metadata: $metadata")

            Log.d("AddToHistory", "🔍 AGENT RESPONSE DETECTED - ANALYZING METADATA:")
            Log.d("AddToHistory", "  - Has themes: ${metadata.containsKey("themes")}")
            Log.d("AddToHistory", "  - Has strategy: ${metadata.containsKey("strategy")}")
            Log.d("AddToHistory", "  - Has reasoning: ${metadata.containsKey("reasoning")}")
            Log.d(
                "AddToHistory",
                "  - Has sessionContext: ${metadata.containsKey("sessionContext")}"
            )

            metadata["themes"]?.let { themesJsonElement ->
                Log.d("AddToHistory", "🎯 THEMES FOUND IN AGENT RESPONSE:")
                Log.d("AddToHistory", "  - Themes JsonElement: $themesJsonElement")
                try {
                    val json = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        coerceInputValues = true
                    }
                    val themes = json.decodeFromJsonElement(
                        serializer<List<ConversationalTheme>>(),
                        themesJsonElement
                    )
                    Log.d("AddToHistory", "  - Successfully parsed ${themes.size} themes:")
                    themes.forEachIndexed { index, theme ->
                        Log.d(
                            "AddToHistory",
                            "    ${index + 1}. '${theme.title}' (${theme.observations.size} observations)"
                        )
                        theme.observations.take(3).forEach { observation ->
                            Log.d("AddToHistory", "      - ${observation.take(50)}...")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AddToHistory", "❌ Failed to parse themes JsonElement: ${e.message}")
                    Log.w("AddToHistory", "❌ Raw themes JsonElement: $themesJsonElement")
                }
            }
                ?: Log.d("AddToHistory", "❌ NO THEMES FOUND IN AGENT RESPONSE METADATA")
        }
        Log.d("AddToHistory", "=== END AGENT RESPONSE METADATA TRACKING ===")
        // --- END ENHANCED LOGGING ---

        // Create in-memory conversation entry for UI/current session
        // Convert JsonElement metadata to String metadata for ConversationEntry
        val stringMetadata = metadata.mapValues { (_, jsonElement) ->
            when (jsonElement) {
                is JsonPrimitive -> jsonElement.content
                else -> jsonElement.toString() // For JsonArray, JsonObject, etc.
            }
        }

        val newEntry =
            ConversationEntry(
                speaker = speaker,
                content = content,
                phase = phase,
                isVisible = isVisible,
                metadata = stringMetadata
            )

        // Add to in-memory history via AgentCortex
        agentCortex.addConversationEntry(newEntry)

        // Log to persistent storage if available
        // Using a non-blocking launch to avoid delays in the UI
        scope.launch {
            try {
                // Get current wishId from coreLoopState. Phase is now passed in.

                // not sending currentwishId because we're not using wishes in MVP
                //val currentWishId = agentCortex.coreLoopState.value.currentWishIndex

                // Log to repository with metadata
                conversationRepository.logConversation(
                    currentSessionId = sessionId,
                    speaker = speaker,
                    content = content,
                    //wishId = currentWishId,
                    phase = phase,
                    isVisible = isVisible,
                    metadata = metadata // ← PASS METADATA TO DATABASE
                )
            } catch (e: Exception) {
                logStatus("Failed to log conversation: ${e.message}", StatusColor.Stop)
            }
        }
    }
    // Legacy functions removed - now handled by CommandMode

    // handleProcessSelection removed - now handled by CommandMode

    // askForWish, captureWish, checkWish removed - now handled by CommandMode

    // selectWishNumber and selectWishBySlot removed - now handled by CommandMode

    // Utility functions removed - now handled by CommandMode and ConversationUtilities

    /** CORE LOOP CODE BEGINS */

    /**
     * Handles Core Loop intent requests from the UI. This determines whether to initiate the loop for
     * the first time or resume an existing conversation.
     * Actually, this function is the initiator of the conversationScope.
     */

    // todo: manage the 'startphase' argument - depending on initiate or resume.

    private fun handleCoreLoopIntent(
        startPhase: ConversationPhase = ConversationPhase.CHECK_IN
    ) {
        logStatus("Core Loop intent received for phase: $startPhase", StatusColor.Go)

        // Set conversation state
        updateConversationState(ConversationType.CoreLoop)

        // Set up conversation scope for proper management and cancellation
        conversationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Launch the progression within the conversation scope
        conversationScope?.launch {
            try {

                /** Begin the core loop */
                progressCoreLoop()

            } catch (e: CancellationException) {
                // Handle cancellation of the Core Loop
                logStatus("Core Loop conversation was cancelled", StatusColor.Pause)
            } catch (e: Exception) {
                // Handle other exceptions
                logStatus("Exception in Core Loop: ${e.message}", StatusColor.Stop)
            }
        }
    }

    /**
     * Main progression function for the Core Loop. Examines current state, determines next steps, and
     * advances the loop.
     */
    private suspend fun progressCoreLoop() {
        // Get current state
        val currentState = agentCortex.coreLoopState.value
        val currentPhase = currentState.currentPhase

        logStatus("Processing Core Loop phase: $currentPhase", StatusColor.Default)

        if (currentPhase == ConversationPhase.CHECK_IN) {
            // Check-In orchestration remains in ConversationAgent (proven pattern)
            progressCheckIn()

        } else {
            // Delegate Core Loop processing to CoreLoopManager
            // Keep the existing behavior - just move the functions
            updateDialogueState(DialogueState.Thinking)

            try {
                // Create prompt and process with LLM via CoreLoopManager
                val prompt = coreLoopManager.createCoreLoopPrompt(currentPhase)
                val result = brainService.getCoreLoopGuidance(prompt)

                result.fold(
                    onSuccess = { response ->
                        // Speak the LLM response directly
                        speak(response)
                        updateDialogueState(DialogueState.ExpectingInput(VoxInputType.BRAIN_RESPONSE))
                    },
                    onFailure = { error ->
                        logStatus(
                            "Error getting Core Loop guidance: ${error.message}",
                            StatusColor.Stop
                        )
                        speak(
                            "I'm having difficulty processing our conversation. Let's try again in a moment."
                        )
                        updateDialogueState(DialogueState.Idle)
                        delay(2000)
                        progressCoreLoop()
                    }
                )
            } catch (e: Exception) {
                logStatus("Exception in Core Loop processing: ${e.message}", StatusColor.Stop)
                updateDialogueState(DialogueState.Idle)
                speak("I'm experiencing a technical issue. Let's take a moment and try again.")
            }
        }
    }

    /** Main orchestration method for check-in phase - Using direct exception handling */
    private suspend fun progressCheckIn() {
        logStatus("🔄 CHECK-IN AGENT: Initiating progressCheckIn()", StatusColor.Default)
        updateDialogueState(DialogueState.Thinking)

        /** This is the beginning of a new conversation, so we have to set some things up:
         * The Check-in Timer.
         * The Session ID.
         *
         * >> We need to decide what to do in the following circumstances:
         * 1. Completely new launch.  Obvious - start the clock and a new session.
         * 2. After loaded existing session but in 'Idle.'
         *      > The check-in may have completed already,
         *          * In this case, we should assume the user wants to continue discussing things.
         *          * We could prevent this (only allowing one completed check-in per session)
         *          * Or we could allow it (but we would have to accrue themes and history, so may not be a good idea)
         *      > Or the check-in did not complete yet.
         *          * Whether the session is loaded from memory or currently ongoing is not relevant.
         *          * The user may have made some comments, and then pressed 'stop' button for any reason.
         *          * The session should be able to continue to completion.
         */
        if (isFirstResponse) {
            logStatus(
                "🔄 CHECK-IN AGENT: First response detected, starting timer, initiating session.",
                StatusColor.Default
            )
            /** how do we initiate our variable? */
            // sessionId
            checkInTimerManager.start()
            isFirstResponse = false
        }

        // Use direct try-catch instead of the adapter pattern
        val turnProcessedSuccessfully =
            try {
                // Step 1: Evaluate transition with basic wish data
                /** This is not something that should be occuring, right now
                 * as we don't have any wish access.
                val basicWishes = fetchUserWishesForContext(repository, ::logStatus)
                 */

                /**
                 * we receive a TransitionEvaluationResult back that we need for later on
                 * shouldTransition: Boolean, extractedThemes: List<ConversationalTheme>,
                 * updatedMetrics: UserEngagementMetrics, transitionReasoning: String = ""
                 */

                //val evaluationResult = checkInDialogueChain.evaluateTransition(basicWishes)
                val evaluationResult = checkInDialogueChain.evaluateTransition()

                // SHARED OPERATION: Update central state with new themes and metrics
                /**
                 * conversation agent must update central state with themes and metrics from current
                 * turn
                 */
                agentCortex.updateActiveThemes(evaluationResult.extractedThemes)
                agentCortex.updateEngagementMetrics(evaluationResult.updatedMetrics)
                logStatus(
                    "🔄 CHECK-IN AGENT: Updated active themes and engagement metrics.",
                    StatusColor.Default
                )

                /** BEGIN TRANSITION AWAY FROM CHECK-IN */
                if (evaluationResult.shouldTransition) {
                    // Step 2a: Transition path - fetch enhanced wish data and call TransitionChain
                    logStatus(
                        "🔄 CHECK-IN AGENT: Transitioning - fetching enhanced wish data",
                        StatusColor.Go
                    )
                    val enhancedWishes =
                        fetchEnhancedWishDataForTransition(
                            repository,
                            conceptRepository,
                            ::logStatus
                        )

                    // Call TransitionChain with enhanced data and direct state access
                    logStatus(
                        "🔄 CHECK-IN AGENT: Calling TransitionChain with enhanced wishes and direct parameters",
                        StatusColor.Go
                    )
                    Log.d(
                        TAG,
                        "🔄 CHECK-IN AGENT: About to call transitionChain.processTransition()"
                    )
                    Log.d(
                        TAG,
                        "🔄 CHECK-IN AGENT: Input themes count: ${evaluationResult.extractedThemes.size}"
                    )
                    Log.d(
                        TAG,
                        "🔄 CHECK-IN AGENT: Input enhanced wishes count: ${enhancedWishes.size}"
                    )

                    val transitionActionPlan =
                        transitionChain.processTransition(
                            themes = evaluationResult.extractedThemes,
                            enhancedWishes = enhancedWishes
                        )

                    // Comprehensive logging of received TransitionActionPlan
                    Log.d(
                        TAG,
                        "🔄 CHECK-IN AGENT: ✅ RECEIVED TransitionActionPlan from TransitionChain"
                    )
                    Log.d(
                        TAG,
                        "🔄 CHECK-IN AGENT: Action suggestion length: ${transitionActionPlan.actionSuggestion.length} chars"
                    )
                    Log.d(
                        TAG,
                        "🔄 CHECK-IN AGENT: Action suggestion: ${transitionActionPlan.actionSuggestion}"
                    )
                    /** not in mvp!
                    Log.d(
                        TAG,
                        "🔄 CHECK-IN AGENT: Proposed phase: ${transitionActionPlan.proposedPhase}"
                    )
                    Log.d(
                        TAG,
                        "🔄 CHECK-IN AGENT: Target wish ID: ${transitionActionPlan.targetWishId}"
                    )
                    Log.d(TAG, "🔄 CHECK-IN AGENT: Reasoning: ${transitionActionPlan.reasoning}")
                    */
                    Log.d(TAG, "🔄 CHECK-IN AGENT: =====================================")

                    logStatus(
                        "🔄 CHECK-IN AGENT: ✅ Successfully received TransitionActionPlan from TransitionChain",
                        StatusColor.Go
                    )

                    /**
                     * We've received the proposed action plan from the Transition Chain. We need to
                     * feed it back to the user and see if they want to follow our suggestions.
                     */
                    handleStructuredTransition(transitionActionPlan)
                } else {

                    /** CONTINUE CHECK-IN CONVERSATION */

                    // Step 2b: Continue conversation path
                    logStatus("🔄 CHECK-IN AGENT: Continuing conversation", StatusColor.Default)
                    //val response = checkInDialogueChain.continueCheckIn(evaluationResult, basicWishes)

                    // sending response without the basicWishes variable because pre-MVP we have no 'wishes'
                    val response = checkInDialogueChain.continueCheckIn(evaluationResult)

                    // Handle continue conversation using existing method
                    handleContinueCheckIn(response)
                }

                // If we get here, processing was successful
                true
            } catch (e: Exception) {
                // Use centralized error handling as documented in checkin_error_handling.md
                logStatus(
                    "🔄 CHECK-IN AGENT: Exception caught during dialogue turn: ${e.message}",
                    StatusColor.Stop
                )

                // Delegate to centralized error handling
                handleError(
                    error = e,
                    onFailure = null,
                    toggleConversation = ::toggleBrainConversation,
                    speak = ::speak,
                    updateDialogueState = { state -> updateDialogueState(state) },
                    agentCortex = agentCortex,
                    logger = ::logStatus
                )

                // Processing was not successful
                false
            }

        if (!turnProcessedSuccessfully) {
            logStatus(
                "🔄 CHECK-IN AGENT: Dialogue turn processing was not successful (error handled).",
                StatusColor.Stop
            )
        }
    }

    // Handler for continue check-in conversation
    private suspend fun handleContinueCheckIn(response: Response) {
        logStatus("🔄 CHECK-IN AGENT: Continuing check-in conversation.", StatusColor.Default)
        try {
            // Get current themes from AgentCortex
            val currentThemes = agentCortex.checkInState.value.activeThemes

            // Log theme information before storage
            Log.d(TAG, "🔄 CHECK-IN AGENT: Storing themes in metadata for agent response")
            Log.d(TAG, "🔄 CHECK-IN AGENT: Current themes count: ${currentThemes.size}")
            currentThemes.forEachIndexed { index, theme ->
                Log.d(
                    TAG,
                    "🔄 CHECK-IN AGENT: Theme ${index + 1}: '${theme.title}' with ${theme.observations.size} observations"
                )
            }

            // Store themes in metadata - serialize once here, avoid double serialization
            Log.d(TAG, "🔄 CHECK-IN AGENT: Storing ${currentThemes.size} themes in metadata")
            currentThemes.forEachIndexed { index, theme ->
                Log.d(
                    TAG,
                    "🔄 CHECK-IN AGENT: Theme ${index + 1}: '${theme.title}' with ${theme.observations.size} observations"
                )
            }

            // Serialize themes to JsonElement for storage in metadata
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
            val themesJsonElement =
                json.encodeToJsonElement(serializer<List<ConversationalTheme>>(), currentThemes)

            Log.d(TAG, "🔄 CHECK-IN AGENT: Themes JsonElement: $themesJsonElement")

            // Store the metadata using JsonElement
            val metadata =
                mapOf(
                    "themes" to themesJsonElement,
                    "strategy" to JsonPrimitive(response.strategy.name),
                    "reasoning" to JsonPrimitive(response.reasoning),
                    "sessionContext" to JsonPrimitive("check_in_continuation")
                )

            Log.d(TAG, "🔄 CHECK-IN AGENT: Adding agent response to history with metadata")
            Log.d(TAG, "🔄 CHECK-IN AGENT: Metadata keys: ${metadata.keys.joinToString(", ")}")
            Log.d(TAG, "🔄 CHECK-IN AGENT: Strategy: ${response.strategy.name}")
            Log.d(TAG, "🔄 CHECK-IN AGENT: Response length: ${response.response.length} chars")

            addToHistory(
                Speaker.Agent,
                response.response,
                phase = agentCortex.coreLoopState.value.currentPhase,
                metadata = metadata
            )

            Log.d(
                TAG,
                "🔄 CHECK-IN AGENT: ✅ Successfully added agent response to history with themes in metadata"
            )
            speak(response.response)

            updateDialogueState(DialogueState.ExpectingInput(VoxInputType.BRAIN_RESPONSE))

        } catch (e: Exception) {
            logStatus(
                "🔄 CHECK-IN AGENT: ❌ Error while handling continue conversation: ${e.message}",
                StatusColor.Stop
            )
            e.printStackTrace()
            speak("I had a problem with that last step. Let's try again.")
            updateDialogueState(DialogueState.Idle) // Fallback to Idle
        }
    }

    /**
     * Standardized function for all core loop phase transitions. All state changes flow through here
     * to ensure consistency and narrative continuity.
     * not in MVP!
    private suspend fun navigateCoreLoopPhase(
        actionPlan: TransitionActionPlan
    ) {
        val targetPhase = actionPlan.getConversationPhase()

        if (targetPhase != null) {
            logStatus("🔄 CORE LOOP: Navigating to phase: ${targetPhase.name}", StatusColor.Go)

            // 1. Update core loop state (THE centralized state change point)
            agentCortex.updateCoreLoopState(
                phase = targetPhase,
                wishIndex = actionPlan.targetWishId ?: -1,
                understanding = actionPlan.reasoning
            )

            // 2. Log transition for narrative continuity
            addToHistory(
                Speaker.Agent,
                "Transitioning to ${targetPhase.description}",
                phase = targetPhase,
                metadata =
                    mapOf(
                        "transitionReasoning" to JsonPrimitive(actionPlan.reasoning),
                        "targetWishId" to JsonPrimitive(
                            actionPlan.targetWishId?.toString() ?: "null"
                        ),
                        "proposedPhase" to JsonPrimitive(actionPlan.proposedPhase),
                        "actionSuggestion" to JsonPrimitive(actionPlan.actionSuggestion),
                        "navigationDecision" to JsonPrimitive("core_loop_phase_transition")
                    )
            )

            // 3. Route to appropriate implementation
            when (targetPhase) {
                ConversationPhase.PRESENT_STATE_EXPLORATION,
                ConversationPhase.DESIRED_STATE_EXPLORATION -> {
                    // commented to avoid errors as this function is currently unused.
//                    launchConceptScreenForPhase(actionPlan.targetWishId, targetPhase)
                }

                ConversationPhase.CONVERSATION_END -> {
                    // End the Core Loop conversation and return to idle CommandMode state
                    logStatus(
                        "🔄 CORE LOOP: Ending conversation and returning to CommandMode",
                        StatusColor.Go
                    )
                    toggleBrainConversation()
                }

                else -> {
                    // Continue with main conversation flow for other phases
                    updateDialogueState(DialogueState.ExpectingInput(VoxInputType.BRAIN_RESPONSE))
                }
            }
        } else {
            // Invalid phase - throw exception for centralized error handling
            throw IllegalStateException(
        //        "Invalid phase in TransitionActionPlan: ${actionPlan.proposedPhase}"
            )
        }
    }
    */

    /**
     * Handles structured transition using TransitionActionPlan for intelligent routing. Uses the
     * structured data to route to the correct phase with proper context.
     */
    private suspend fun handleStructuredTransition(actionPlan: TransitionActionPlan) {
        /**
        logStatus(
            "🔄 CHECK-IN AGENT: Structured transition complete. Target phase: ${actionPlan.proposedPhase}, Target wish: ${actionPlan.targetWishId}",
            StatusColor.Go
        )
        */

        // Stop the timer as we are leaving the check-in phase
        checkInTimerManager.stop()

        // Update AgentCortex with themes from TransitionActionPlan
        if (actionPlan.themes.isNotEmpty()) {
            agentCortex.updateActiveThemes(actionPlan.themes)
            Log.d(
                TAG,
                "🔄 CHECK-IN AGENT: Updated AgentCortex with ${actionPlan.themes.size} themes from TransitionActionPlan"
            )
            logStatus(
                "🔄 CHECK-IN AGENT: Updated AgentCortex with themes from transition",
                StatusColor.Default
            )
        }

        // Speak the transition message
        if (actionPlan.actionSuggestion.isNotBlank()) {
            speak(actionPlan.actionSuggestion)

            // Add to history with phase reasoning preserved in metadata
            val currentThemes = agentCortex.checkInState.value.activeThemes
            val themesJsonElement = if (currentThemes.isNotEmpty()) {
                val json = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                }
                json.encodeToJsonElement(serializer<List<ConversationalTheme>>(), currentThemes)
            } else null

            val transitionMetadata = mutableMapOf<String, JsonElement>()
            /** not for mvp!
            transitionMetadata["phaseReasoning"] = JsonPrimitive(actionPlan.reasoning)
            transitionMetadata["suggestedPhase"] = JsonPrimitive(actionPlan.proposedPhase)
            transitionMetadata["targetWishId"] =
                JsonPrimitive(actionPlan.targetWishId?.toString() ?: "null")
            */
            transitionMetadata["transitionType"] = JsonPrimitive("structured_transition")

            // Add themes to metadata if available
            themesJsonElement?.let { themes ->
                transitionMetadata["themes"] = themes
                Log.d(
                    TAG,
                    "🔄 CHECK-IN AGENT: Added ${currentThemes.size} themes to transition metadata"
                )
            }

            Log.d(TAG, "🔄 CHECK-IN AGENT: Adding transition message to history with metadata")
            Log.d(
                TAG,
                "🔄 CHECK-IN AGENT: Transition metadata keys: ${
                    transitionMetadata.keys.joinToString(
                        ", "
                    )
                }"
            )
            Log.d(
                TAG,
                "🔄 CHECK-IN AGENT: Action suggestion length: ${actionPlan.actionSuggestion.length} chars"
            )

            // Add full transition message to history
            addToHistory(
                Speaker.Agent,
                actionPlan.actionSuggestion,
                phase = agentCortex.coreLoopState.value.currentPhase,
                metadata = transitionMetadata
            )

            // Add pure theme summary for playback (not visible in UI)
            addToHistory(
                Speaker.Agent,
                actionPlan.themeSummary,
                phase = agentCortex.coreLoopState.value.currentPhase,
                isVisible = false,
                metadata = mapOf("messageType" to JsonPrimitive("themeSummary"))
            )

            // Log the metadata preservation
            Log.d(
                TAG,
                "🔄 CHECK-IN AGENT: ✅ Successfully added transition message to history with metadata"
            )
            /** not for mvp!
            Log.d(TAG, "🔄 CHECK-IN AGENT: Metadata - phaseReasoning: ${actionPlan.reasoning}")
            Log.d(TAG, "🔄 CHECK-IN AGENT: Metadata - suggestedPhase: ${actionPlan.proposedPhase}")
            Log.d(TAG, "🔄 CHECK-IN AGENT: Metadata - targetWishId: ${actionPlan.targetWishId}")
            */
        }

        // Update session name if provided by TransitionChain
        actionPlan.sessionName?.let { sessionName ->
            try {
                val currentSessionId = agentCortex.currentSessionId
                conversationRepository.updateSessionName(currentSessionId, sessionName)
                Log.d(TAG, "🔄 CHECK-IN AGENT: Updated session name to: '$sessionName'")
                logStatus("🔄 CHECK-IN AGENT: Session named: '$sessionName'", StatusColor.Default)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating session name: ${e.message}", e)
                logStatus("⚠️ Failed to update session name: ${e.message}", StatusColor.Pause)
            }
        }

        // Use the standardized core loop navigation function
//        navigateCoreLoopPhase(actionPlan)
    }

    /** Creates context information about why a wish was selected */
    private suspend fun createWishSelectionContext(wishIndex: Int, reasonCode: String): String {
        return buildString {
            try {
                val manifestation = repository.getManifestationBySlot(wishIndex)
                if (manifestation != null) {
                    append("\n>>> IMPORTANT: THIS WISH WAS JUST SELECTED <<<\n")

                    // Format the date of last update
                    val lastUpdatedDate =
                        manifestation.lastDiscussedTimestamp?.let { timestamp ->
                            val date =
                                SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(timestamp))
                            "last updated on $date"
                        }
                            ?: "has never been updated since creation"

                    val selectionReason =
                        when (reasonCode) {
                            "empty_slot" ->
                                "This is an empty wish slot that needs to be filled with a new wish."

                            "missing_present_state" ->
                                "This wish needs exploration of the present state, which hasn't been defined yet."

                            "missing_desired_state" ->
                                "This wish has a defined present state but needs exploration of the desired outcome."

                            "ready_for_contrast" ->
                                "This wish has both present and desired states defined and is ready for contrast analysis."

                            "least_recently_updated" ->
                                "This wish was selected because it $lastUpdatedDate, longer than other wishes."

                            else -> "This wish was selected based on the system's prioritization algorithm."
                        }

                    append("- Selection reason: $selectionReason\n")
                    append(
                        "- When introducing this wish to the user, explain why it was chosen and guide them into the appropriate phase.\n"
                    )
                    append(">>> END IMPORTANT SECTION <<<\n")
                }
            } catch (e: Exception) {
                append("- Error retrieving wish selection details: ${e.message}\n")
            }
        }
    }

    /** Updates the lastDiscussedTimestamp for a wish when it's selected in the core loop */
    private suspend fun updateWishTimestamp(slot: Int) {
        try {
            // Get the manifestation for this slot
            val manifestation = repository.getManifestationBySlot(slot)

            // Update the timestamp if the manifestation exists
            manifestation?.let {
                repository.updateLastDiscussedTimestamp(it.id)
                logStatus("Updated lastDiscussedTimestamp for wish in slot $slot (ID: ${it.id})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating wish timestamp: ${e.message}", e)
        }
    }

    /**
     * CoreLoopManager instance for handling Core Loop processing. Following the "frontal cortex"
     * pattern where ConversationAgent orchestrates and CoreLoopManager handles internal processing.
     */
    private val coreLoopManager: CoreLoopManager by lazy {
        CoreLoopManager(agentCortex = agentCortex, logStatus = ::logStatus)
    }

    /** Handles user responses during the Core Loop conversation. */
    private suspend fun handleCoreLoopResponse(response: String) {
        logStatus(
            "🔄 CORE_LOOP: Processing user response: \"${response.take(50)}${if (response.length > 50) "..." else ""}\"",
            StatusColor.Default
        )

        // this looks like a duplicate.  we shouldn't need to add to history because everything's
        // already been added while the agent is listening.
        // Add user response to conversation history
        // addToHistory(Speaker.User, response, currentAction.value)

        // Change dialogue state back to thinking while we process
        logStatus("🔄 CORE_LOOP: Changing dialogue state to Thinking", StatusColor.Default)
        updateDialogueState(DialogueState.Thinking)

        // Get current phase from CoreLoopState
        val currentPhase = agentCortex.coreLoopState.value.currentPhase
        val checkInState = agentCortex.checkInState.value

        logStatus("🔄 CORE_LOOP: Current phase is $currentPhase", StatusColor.Default)

        // Special handling for CHECK_IN phase
        if (currentPhase == ConversationPhase.CHECK_IN) {
            // Check if we're in the TRANSITION stage
            if (checkInState.currentStage == CheckInStage.TRANSITION) {
                logStatus("🔄 CORE_LOOP: Handling transition response", StatusColor.Default)
                //                handleTransitionResponse(response)
            } else {
                logStatus(
                    "🔄 CORE_LOOP: Handling CHECK_IN phase response in OPENING stage",
                    StatusColor.Default
                )
                // Process CHECK_IN response
                progressCheckIn()
            }
        } else {
            logStatus(
                "🔄 CORE_LOOP: Processing response for $currentPhase phase",
                StatusColor.Default
            )
            // For other phases, continue with standard processing
            progressCoreLoop()
        }
    }

    /**
     * Plays back the theme summary from the current session
     */
    suspend fun playThemeSummary() {
        try {
            Log.d(TAG, "🎵 THEME PLAYBACK: Attempting to retrieve theme summary")
            logStatus(
                "🎵 THEME PLAYBACK: User pressed play button - retrieving stored theme summary",
                StatusColor.Go
            )

            val themeSummary =
                conversationRepository.getThemeSummaryForCurrentSession(agentCortex.currentSessionId)

            if (themeSummary != null) {
                Log.d(TAG, "🎵 THEME PLAYBACK: Found theme summary, speaking...")

                // Log playback content for monitoring
                val summaryPreview = if (themeSummary.length > 100) {
                    "${themeSummary.substring(0, 100)}..."
                } else {
                    themeSummary
                }
                logStatus(
                    "🎵 PLAYING: Theme summary (${themeSummary.length} chars): $summaryPreview",
                    StatusColor.Default
                )

                speak(themeSummary)
                // Return to idle state after speaking (theme playback is not a conversation)
                updateDialogueState(DialogueState.Idle)
            } else {
                Log.w(TAG, "🎵 THEME PLAYBACK: No theme summary found for current session")
                logStatus(
                    "🎵 THEME PLAYBACK: No theme summary found for current session",
                    StatusColor.Stop
                )
                speak("I don't have a theme summary available for this session.")
                // Return to idle state after speaking
                updateDialogueState(DialogueState.Idle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "🎵 THEME PLAYBACK: Error retrieving theme summary: ${e.message}", e)
            logStatus(
                "🎵 THEME PLAYBACK: Error retrieving theme summary: ${e.message}",
                StatusColor.Stop
            )
            speak("I'm having trouble accessing your theme summary right now.")
            // Return to idle state after speaking
            updateDialogueState(DialogueState.Idle)
        }
    }
}

