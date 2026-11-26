package com.example.voxmanifestorapp.ui.agent.voice

import com.example.voxmanifestorapp.ui.agent.ConversationType

enum class VoiceCommandEntity(
    val keyWords: Set<String>,
    var params: String? = null,
    // add a set of conversation types where this command should be processed in BACKGROUND,
    // i.e. where we can process the text outside of the normal command processing
    // normally, when an utterance contains one of these commands, it won't be processed as BACKGROUND as commands should trump any normal speech processing
    val backgroundContext: Set<ConversationType> = emptySet()
) {
    // START(setOf("manifest", "start", "begin", "genie")), // Disabled for MVP
    STOP(setOf("terminate", "stop", "cancel")),
    SELECT(setOf("select", "choose")),
    // DEFINE(
    //     setOf("define"),
    //     // process 'define' as part of BACKGROUND when in wish selection conversation type
    //     backgroundContext = setOf(ConversationType.WishSelection)
    // ), // Disabled for MVP
    // INITIATE(setOf("initiate", "concept")), // Disabled for MVP
    READ(setOf("read", "list", "speak")),
    // AFFIRM(setOf("affirm", "affirmation", "affirmations")), // Disabled for MVP
    HELP(setOf("help", "commands")),
    QUIT(setOf("quit", "close app", "bye")),
    BACKGROUND(setOf());

    companion object {
        fun processCommand(output: String, currentConversation: ConversationType?): VoiceCommandEntity =
            entries.firstOrNull { command ->
                if (currentConversation != null && command.backgroundContext.contains(currentConversation)) {
                    return@firstOrNull false
                }
                command.keyWords.any { word -> output.trim().contains(word, ignoreCase = true) }
            }?.applyParams(output) ?: BACKGROUND

        fun getAllCommands(): List<String> = entries.flatMap { it.keyWords }
    }

    fun isIn(text: String): Boolean =
        keyWords.sumOf { if (text.contains(it)) 1.toInt() else 0 } > 0

    private fun applyParams(text: String): VoiceCommandEntity = apply {
        params = text
        keyWords.forEach {
            params = params?.replace(it, "", ignoreCase = true)
        }
    }

}