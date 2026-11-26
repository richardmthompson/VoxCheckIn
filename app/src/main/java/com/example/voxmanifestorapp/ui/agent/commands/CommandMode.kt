package com.example.voxmanifestorapp.ui.agent.commands

import com.example.voxmanifestorapp.data.MAX_WISH_SLOTS
import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.AgentCortex
import com.example.voxmanifestorapp.ui.agent.ConversationStep
import com.example.voxmanifestorapp.ui.agent.ConversationType
import com.example.voxmanifestorapp.ui.agent.DialogueState
import com.example.voxmanifestorapp.ui.agent.VoxInputType
import com.example.voxmanifestorapp.ui.agent.navigation.NavigationManager
import com.example.voxmanifestorapp.ui.agent.utilities.WishUtilities
import com.example.voxmanifestorapp.ui.agent.utilities.extractNumber
import com.example.voxmanifestorapp.ui.agent.utilities.getOrdinal
import com.example.voxmanifestorapp.ui.agent.voice.VoiceProcessor
import com.example.voxmanifestorapp.ui.main.MainScreenState

/**
 * Manages command-driven conversation flows for wish collection and selection. These flows operate
 * in administrative/command mode with deterministic state transitions. See README.md in this
 * package for detailed documentation.
 */
class CommandMode(
        private val wishUtilities: WishUtilities,
        private val agentCortex: AgentCortex,
        private val mainScreenState: MainScreenState,
        private val logger: (String, StatusColor) -> Unit,
        private val voiceProcessor: VoiceProcessor,
        private val navigationManager: NavigationManager,
        private val speak: suspend (String) -> Unit,
        private val toggleConversation: suspend () -> Unit
) {

  // State machine variables
  private var currentStep: ConversationStep? = null
  private var selectedManifestation: Manifestation? = null
  private var tempWishResponse: String? = null
  private var isSingleCommandMode: Boolean = false

  /** Sets single command mode flag */
  fun setSingleCommandMode(value: Boolean) {
    isSingleCommandMode = value
  }

  /**
   * Gets slot availability information for TransitionChain use Returns a descriptive string about
   * available wish slots
   */
  suspend fun getSlotAvailabilityInfo(): String {
    val allManifestations = wishUtilities.getAllManifestations()
    val totalSlots = MAX_WISH_SLOTS
    val filledSlots = allManifestations.size
    val availableSlots = totalSlots - filledSlots

    return when {
      availableSlots == 0 -> "All 5 wish slots are filled"
      availableSlots == 1 -> "1 wish slot available"
      else -> "$availableSlots wish slots available"
    }
  }

  /** Entry point for conversation loop - determines whether to collect or select wishes */
  suspend fun enterConversationLoop() {
    // Get all manifestations to check for empty slots
    val allManifestations = wishUtilities.getAllManifestations()
    val emptyWishIndex = wishUtilities.findNextEmptyWishSlot()

    logger("findNextEmptyWishSlot returned $emptyWishIndex", StatusColor.Default)

    if (emptyWishIndex != -1) {
      // If we have an empty wish slot, work with filling that slot
      agentCortex.updateConversationType(ConversationType.CommandMode)
      mainScreenState.setSelectedSlot(emptyWishIndex)
      proceedToNextWishStep(ConversationStep.WishStep.AskForWish)
    } else {
      // If slots are full, we need to run a different process
      agentCortex.updateConversationType(ConversationType.CommandMode)
      proceedToNextSelectionStep(ConversationStep.WishSelectionStep.SelectWish)
    }
  }

  /**
   * Processes input for command mode conversations Routes based on current conversation step to
   * appropriate flow
   */
  suspend fun processInput(text: String, dialogueState: DialogueState) {
    logger("CommandMode received input: $text", StatusColor.Default)

    when (dialogueState) {
      // If agent is speaking, ignore user input
      is DialogueState.Speaking -> return

      // If we're expecting input, process according to current step
      is DialogueState.ExpectingInput -> {
        when (currentStep) {
          // Wish Collection Flow
          is ConversationStep.WishStep.CaptureWish -> {
            captureWish(text)
          }
          is ConversationStep.WishStep.CaptureValidation -> {
            resolveYesNo(text)?.let { yesNo ->
              validateWish(yesNo)
            }
          }

          // Wish Selection Flow
          is ConversationStep.WishSelectionStep.CaptureWish -> {
            selectWishNumber(text)
          }
          is ConversationStep.WishSelectionStep.CaptureProcess -> {
            handleProcessSelection(text)
          }
          else -> logger("CommandMode: Unexpected step: $currentStep", StatusColor.Default)
        }
      }
      else -> {
        // Unexpected dialogue state
      }
    }
  }

  /** Proceeds to the next step in wish collection flow */
  private suspend fun proceedToNextWishStep(step: ConversationStep.WishStep) {
    updateCurrentStep(step)
    when (step) {
      ConversationStep.WishStep.AskForWish -> {
        logger("<wishstep.askforwish>", StatusColor.Default)
        askForWish()
      }
      ConversationStep.WishStep.CaptureWish -> {
        agentCortex.updateDialogueState(DialogueState.ExpectingInput(VoxInputType.FREEFORM))
        logger("Listening for your wish...", StatusColor.Default)
      }
      ConversationStep.WishStep.CheckWish -> {
        logger("<wishstep.checkwish>", StatusColor.Default)
        checkWish()
      }
      ConversationStep.WishStep.CaptureValidation -> {
        agentCortex.updateDialogueState(DialogueState.ExpectingInput(VoxInputType.YES_NO))
      }
      else -> logger("Unexpected wish step: $step", StatusColor.Default)
    }
  }

  /** Proceeds to the next step in wish selection flow */
  private suspend fun proceedToNextSelectionStep(step: ConversationStep.WishSelectionStep) {
    updateCurrentStep(step)
    when (step) {
      is ConversationStep.WishSelectionStep.SelectWish -> {
        speak("Which wish do you want to edit?")
        proceedToNextSelectionStep(ConversationStep.WishSelectionStep.CaptureWish)
        return
      }
      is ConversationStep.WishSelectionStep.CaptureWish -> {
        logger("Waiting for wish selection", StatusColor.Default)
        agentCortex.updateDialogueState(DialogueState.ExpectingInput(VoxInputType.FREEFORM))
        return
      }
      is ConversationStep.WishSelectionStep.SelectProcess -> {
        speak("Would you like to change, or delete this wish?")
        proceedToNextSelectionStep(ConversationStep.WishSelectionStep.CaptureProcess)
        return
      }
      is ConversationStep.WishSelectionStep.CaptureProcess -> {
        agentCortex.updateDialogueState(
                DialogueState.ExpectingInput(VoxInputType.COMMAND_SELECTION)
        )
        return
      }
      else -> logger("Unexpected selection step: $step", StatusColor.Default)
    }
  }

  /** Asks for a wish based on current slot */
  private suspend fun askForWish() {
    val wishNumber = mainScreenState.selectedSlot.value + 1
    val prompt = "What is your ${getOrdinal(wishNumber)} wish?"

    speak(prompt)
    proceedToNextWishStep(ConversationStep.WishStep.CaptureWish)
  }

  /** Captures wish text from user */
  private suspend fun captureWish(text: String) {
    tempWishResponse = text
    proceedToNextWishStep(ConversationStep.WishStep.CheckWish)
  }

  /** Checks captured wish with user */
  private suspend fun checkWish() {
    if (tempWishResponse != null) {
      speak("I've entered your wish as this... ${tempWishResponse}... is that ok?")
      proceedToNextWishStep(ConversationStep.WishStep.CaptureValidation)
    } else {
      speak("I'm sorry, something's gone wrong with retrieving your wish.")
    }
  }

  /** Validates and saves the wish */
  private suspend fun validateWish(yesNo: Boolean?) {
    if (yesNo == true) {
      val currentSlot = mainScreenState.selectedSlot.value
      val wishText = tempWishResponse ?: ""

      // Use WishUtilities to save the wish
      val success = wishUtilities.saveWishToSlot(wishText, currentSlot)

      if (success) {
        speak("New manifestation created!")
        logger("Manifestation: \"$wishText\"", StatusColor.Go)
      } else {
        speak("I'm sorry, I had trouble saving that wish.")
        logger("Failed to save wish to slot $currentSlot", StatusColor.Stop)
      }

      if (isSingleCommandMode) resetConversation() else enterConversationLoop()
    } else {
      speak("Let's get that wish from you again...")
      proceedToNextWishStep(ConversationStep.WishStep.CaptureWish)
    }
  }

  /** Selects wish by number from speech */
  suspend fun selectWishNumber(text: String) {
    val number = extractNumber(text)

    if (number == null) {
      speak("Couldn't hear a wish number. Please say a number between one and five")
      proceedToNextSelectionStep(ConversationStep.WishSelectionStep.CaptureWish)
      return
    }

    // Create array index from human friendly number
    val slot = number - 1

    if (!wishUtilities.isValidWishSlot(slot)) {
      speak("Please choose a number between one and $MAX_WISH_SLOTS")
      proceedToNextSelectionStep(ConversationStep.WishSelectionStep.CaptureWish)
      return
    }

    // Error checking done, now run the selection function
    selectWishBySlot(slot, fromAgent = true)
  }

  /** Selects wish by slot index */
  suspend fun selectWishBySlot(slot: Int, fromAgent: Boolean = false) {
    val getWish = wishUtilities.getWishBySlot(slot)
    val number = slot + 1

    if (getWish != null) {
      // Set slot index if called from voice command
      if (fromAgent) mainScreenState.setSelectedSlot(slot)

      speak("Selected wish number $number - ${getWish.title}")
      selectedManifestation = getWish
      proceedToNextSelectionStep(ConversationStep.WishSelectionStep.SelectProcess)
    } else {
      speak("Selected empty slot $number")
      proceedToNextWishStep(ConversationStep.WishStep.AskForWish)
    }
  }

  /** Handles process selection for selected wish */
  private suspend fun handleProcessSelection(text: String) {
    when {
      text.contains("delete") || text.contains("remove") -> {
        selectedManifestation?.let { wish ->
          val success = wishUtilities.deleteWishFromSlot(wish.slot)
          if (success) {
            speak("Wish deleted")
            if (isSingleCommandMode) resetConversation() else enterConversationLoop()
          } else {
            speak("Sorry, I had trouble deleting that wish")
          }
        }
                ?: speak("Sorry, I couldn't find that wish to delete")
      }
      text.contains("change") || text.contains("edit") || text.contains("add") -> {
        speak("Let's set the wish for this slot.")
        agentCortex.updateConversationType(ConversationType.CommandMode)
        proceedToNextWishStep(ConversationStep.WishStep.AskForWish)
      }
      /*
 * text.contains("present") || text.contains("desired") ||
            text.contains("concept") || text.contains("define") -> {
                val currentSlot = mainScreenState.selectedSlot.value
                speak("Elaborating wish ${currentSlot+1}")
                agentCortex.updateConversationType(ConversationType.ConceptBuilding)

                // Open concept screen
                selectedManifestation?.let { manifestation ->
                    withContext(Dispatchers.Main) {
                        navigationManager.navigateToConceptScreen(manifestation.id)
                    }
                }
            }
  */ text.contains("cancel") || text.contains("no") || text.contains("never mind") -> {
        speak("Okay, canceling the operation")
        resetConversation()
      }
      else -> {
        speak("Please say either 'change', 'delete', or 'cancel' to stop")
        proceedToNextSelectionStep(ConversationStep.WishSelectionStep.CaptureProcess)
      }
    }
  }

  /** Updates current conversation step */
  private fun updateCurrentStep(newStep: ConversationStep?) {
    currentStep = newStep
  }

  /** Resets conversation state */
  private fun resetConversation() {
    agentCortex.resetConversationState()
    updateCurrentStep(null)
    agentCortex.updateDialogueState(DialogueState.Idle)
    selectedManifestation = null
    tempWishResponse = null
  }
  
  /** Handles READ command - reads all wishes */
  suspend fun handleReadCommand() {
    val manifestations = wishUtilities.getAllManifestations()
    if (manifestations.isEmpty()) {
      speak("You haven't made any wishes yet. Use the 'select' command to choose a slot and create a new wish.")
      return
    }
    
    // Build read text
    val readText = buildString {
      append("Here are your current wishes. ")
      for (i in 0 until MAX_WISH_SLOTS) {
        val wish = manifestations.find { it.slot == i }
        if (wish != null) {
          append("${i+1}. ${wish.title}. ")
        } else {
          append("Slot ${i + 1} is empty.")
        }
      }
    }
    
    speak(readText)
  }
  
  /** Handles HELP command - provides guidance on available commands */
  suspend fun handleHelpCommand(text: String) {
    // Check if help is requested for a specific command
    val specificCommand =
            when {
              text.contains("select") || text.contains("choose") -> "SELECT"
              text.contains("read") || text.contains("list") -> "READ"
              text.contains("delete") -> "DELETE"
              else -> null
            }

    if (specificCommand != null) {
      // Provide specific command help
      when (specificCommand) {
        "SELECT" ->
                speak(
                        "The SELECT command lets you choose a wish to work with. Say 'select' followed by a number between 1 and 5, " +
                                "or just say 'select' and I'll ask you which wish you want to work with." +
                                "You can use CHOOSE command interchangeably"
                )
        "READ" ->
                speak(
                        "The READ command tells you all your current wishes. Just say 'read' or 'list' and I'll read out " +
                                "what's in each wish slot."
                )
        "DELETE" ->
                speak(
                        "The DELETE command removes a wish and its associated concept information. " +
                                "Say 'delete' followed by a wish number, or just 'delete' and I'll ask which wish to remove."
                )
        "STOP" ->
                speak(
                        "The STOP or EXIT command ends the current conversation or process. " +
                                "You can use it any time to stop what we're doing and return to an idle state. " +
                                "Just say 'stop' or 'cancel'."
                )
      }
    } else {
      // Provide general help
      speak(
              "Here are the main commands you can use: " +
                      "READ lists all your current wishes. " +
                      "SELECT lets you choose a wish to work with. " +
                      "Say STOP to end the current process. " +
                      "QUIT quits the app. " +
                      "For command specific details, say help followed by the command name. " +
                      "And finally, use the FEEDBACK command to provide feedback to vox manifestor developers, " +
                      "about something that doesn't work, or features you'd like to see in the future -- " +
                      "vox manifestor is currently experimental and needs users to grow."
      )
    }
  }
  
  /** Handles QUIT command */
  suspend fun handleQuitCommand() {
    logger("QUIT REQUEST [not yet implemented]", StatusColor.Stop)
    speak("Sorry, this girl don't know how to quit")
  }
  
  /** Handles STOP command - cancels active conversations */
  suspend fun handleStopCommand() {
    if (agentCortex.conversationType.value == null) {
      logger("Ignoring stop command - no active conversation", StatusColor.Pause)
      return
    }
    
    logger("Executing stop command for conversation: ${agentCortex.conversationType.value}", StatusColor.Pause)
    toggleConversation()  // Calls ConversationAgent.toggleBrainConversation()
  }
  


  // ===== TEXT PROCESSING UTILITIES =====
  // These functions are specific to CommandMode voice command processing

  /** Resolves a potentially ambiguous response into a boolean */
  private suspend fun resolveYesNo(text: String): Boolean? {
    logger("Resolving YESNO with $text", StatusColor.Default)
    val lowerText = text.lowercase()
    return when {
      lowerText.contains("yes") || lowerText.contains("yeah") || lowerText.contains("yep") -> true
      lowerText.contains("no") || lowerText.contains("nope") -> false
      else -> {
        speak("I didn't hear a yes or a no. Please say yes or no!")
        return null
      }
    }
  }
}

