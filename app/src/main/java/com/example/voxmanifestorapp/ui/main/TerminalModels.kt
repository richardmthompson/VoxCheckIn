package com.example.voxmanifestorapp.ui.main

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TerminalEntryType {
    AgentSpeech,
    AgentStatus,
    UserSpeech,
    UserStatus
}

data class TerminalEntry (
    val entryType: TerminalEntryType,
    val content: String,
    // will set timestamp automatically when entry is created.
    val timestamp: Long = System.currentTimeMillis()
)

// Extension function to format timestamp as HH:mm
fun TerminalEntry.formattedTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}