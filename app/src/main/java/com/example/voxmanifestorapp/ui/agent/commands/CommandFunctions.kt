package com.example.voxmanifestorapp.ui.agent.commands

import ManifestationRepository
import com.example.voxmanifestorapp.data.MAX_WISH_SLOTS
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.AgentCortex
import com.example.voxmanifestorapp.ui.agent.ConversationAgent
import com.example.voxmanifestorapp.ui.agent.ConversationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Handles 'select' or 'choose' command */
suspend fun handleSelectCommand(
        text: String,
        repository: ManifestationRepository,
        agentCortex: AgentCortex,
        logger: (String, StatusColor) -> Unit,
        speak: suspend (String) -> Unit,
        setSingleCommandMode: (Boolean) -> Unit,
        selectWishNumber: suspend (String) -> Unit
) {
  setSingleCommandMode(true)
  agentCortex.updateConversationType(ConversationType.CommandMode)
  selectWishNumber(text)
}

/** Handles 'define' command - opens concept screen for a wish */
suspend fun handleDefineCommand(
        text: String,
        repository: ManifestationRepository,
        agentCortex: AgentCortex,
        speak: suspend (String) -> Unit,
        navigateToConceptScreen: (Int) -> Unit,
        setSingleCommandMode: (Boolean) -> Unit
) {
  setSingleCommandMode(true)

  val number = extractNumber(text)
  if (number == null) {
    speak("Couldn't hear a wish number. Please say define and then a number between one and five")
    return
  }

  // Create array index from human friendly number
  val slot = number - 1

  if (slot < 0 || slot >= MAX_WISH_SLOTS) {
    speak("Please choose a number between one and ${MAX_WISH_SLOTS}")
    return
  }

  // Find the wish in that slot
  val wish = repository.getManifestationBySlot(slot)

  if (wish != null) {
    speak("Opening concept definition for wish ${number} - ${wish.title}")
    // Set conversation type flag - this auto-initiates voice interaction upon entry into concept
    // screen
    agentCortex.updateConversationType(ConversationType.ConceptBuilding)

    // Navigate to concept screen
    withContext(Dispatchers.Main) { navigateToConceptScreen(wish.id) }
  } else {
    speak("There's no wish in slot $number to define")
  }
}

/**
 * Handles 'start' command - initiates conversation loop DISABLED FOR MVP - START command
 * functionality not ready for release
 */
/*
suspend fun handleStartCommand(
    agentCortex: AgentCortex,
    speak: suspend (String) -> Unit,
    setSingleCommandMode: (Boolean) -> Unit,
    enterConversationLoop: suspend () -> Unit
) {
    if (agentCortex.conversationType.value == null) {
        speak("I received your command to start manifesting your wishes...")
        setSingleCommandMode(false)
        enterConversationLoop()
    } else {
        speak("We're already in a conversation. Say 'exit' to start again.")
    }
}
*/

/** Handles 'stop' command */
suspend fun handleStopCommand(
        agentCortex: AgentCortex,
        logger: (String, StatusColor) -> Unit,
        toggleConversation: suspend () -> Unit
) {
  if (agentCortex.conversationType.value == null) {
    logger("Ignoring stop command - no active conversation", StatusColor.Pause)
    return
  }

  logger(
          "Executing stop command for conversation: ${agentCortex.conversationType.value}",
          StatusColor.Pause
  )

  // Use toggleBrainConversation which has proper cleanup logic for all conversation types
  toggleConversation()
}

/** Handles 'read' command - lists all current wishes */
suspend fun handleReadCommand(
        repository: ManifestationRepository,
        speak: suspend (String) -> Unit
) {
  val manifestations = repository.getAllManifestations().first()
  if (manifestations.isEmpty()) {
    speak(
            "You haven't made any wishes yet. Use the 'select' command to choose a slot and create a new wish."
    )
    return
  }

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

/** Handles 'initiate' command - context-dependent action */
suspend fun handleInitiateCommand(
        currentScreen: ConversationAgent.Screen,
        agentCortex: AgentCortex,
        speak: suspend (String) -> Unit,
        initiateConceptBuilding: suspend () -> Unit
) {
  when (currentScreen) {
    ConversationAgent.Screen.Main -> {
      speak("Please open the concept definition screen before initiating concept building.")
    }
    ConversationAgent.Screen.Concept -> {
      agentCortex.updateConversationType(ConversationType.ConceptBuilding)
      initiateConceptBuilding()
    }
  }
}

/** Handles 'quit' command */
suspend fun handleQuitCommand(
        logger: (String, StatusColor) -> Unit,
        speak: suspend (String) -> Unit
) {
  logger("QUIT REQUEST [not yet implemented]", StatusColor.Stop)
  speak("Sorry, this girl don't know how to quit")
}

/** Handles 'help' command with optional specific command help */
suspend fun handleHelpCommand(text: String, speak: suspend (String) -> Unit) {
  // Check if help is requested for a specific command
  val specificCommand =
          when {
            text.contains("select") || text.contains("choose") -> "SELECT"
            // text.contains("define") -> "DEFINE" // Disabled for MVP
            // text.contains("affirm") -> "AFFIRM" // Disabled for MVP
            text.contains("read") || text.contains("list") -> "READ"
            text.contains("delete") -> "DELETE"
            // text.contains("start") || text.contains("begin") -> "START" // Disabled for MVP
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
      // "DEFINE" ->
      //         speak(
      //                 "The DEFINE command opens the concept screen for a wish, where you can
      // describe its present and desired states. " +
      //                         "Say 'define' followed by a wish number, or just 'define' and I'll
      // ask which wish you want to define."
      //         ) // Disabled for MVP
      // "AFFIRM" ->
      //         speak(
      //                 "The AFFIRM command starts an affirmation session for your wishes. You can
      // say 'affirmations' by itself, " +
      //                         "or specify how many affirmations you want for each wish, like
      // 'give me 3 affirmations'."
      //         ) // Disabled for MVP
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
      // "START" -> speak(
      //     "The START command begins our manifestation process. I'll guide you through either
      // creating a new wish " +
      //             "or working with your existing wishes."
      // ) // Disabled for MVP
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
                    // "START starts a manifestation conversation."
                    "READ lists all your current wishes. " +
                    "SELECT lets you choose a wish to work with. " +
                    // "DEFINE opens the concept screen for a wish. " +
                    // "AFFIRM starts an affirmation session. " +
                    "Say STOP to end the current process. " +
                    "QUIT quits the app. " +
                    "For command specific details, say help followed by the command name. " +
                    "And finally, use the FEEDBACK command to provide feedback to vox manifestor developers, " +
                    "about something that doesn't work, or features you'd like to see in the future -- " +
                    "vox manifestor is currently experimental and needs users to grow."
    )
  }
}

/** Extracts numbers from text, trying digits first then word numbers */
private fun extractNumber(text: String): Int? {
  // First try to extract digit numbers
  val digitNumber = extractIntNumber(text)
  if (digitNumber != null) {
    return digitNumber
  }

  // If that fails, try to extract word numbers
  return parseNumberWord(text)
}

/** Extracts integer numbers from text using regex */
private fun extractIntNumber(text: String): Int? {
  val regex = "\\d+".toRegex()
  return regex.find(text)?.value?.toIntOrNull()
}

/** Parses word numbers (one, two, etc.) to integers */
private fun parseNumberWord(text: String): Int? {
  val numberWords =
          mapOf(
                  "one" to 1,
                  "first" to 1,
                  "two" to 2,
                  "second" to 2,
                  "to" to 2,
                  "three" to 3,
                  "third" to 3,
                  "four" to 4,
                  "fourth" to 4,
                  "for" to 4,
                  "five" to 5,
                  "fifth" to 5
          )
  return numberWords.entries.firstOrNull { (word, _) -> text.lowercase().contains(word) }?.value
}
