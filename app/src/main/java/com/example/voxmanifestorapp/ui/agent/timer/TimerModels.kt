/**
 * Timer module for managing time-based state in the CHECK_IN phase.
 * 
 * This module provides:
 * - [TimerState]: Sealed class representing timer states and utilities
 * - [TimerIntent]: Sealed class defining timer control actions
 * - [CheckInTimerManager]: Manager class for timer operations
 * 
 * Integration:
 * - Uses AgentCortex for state management
 * - Follows unidirectional data flow pattern
 * - Emits state updates through StateFlow
 * 
 * See README.md in this package for detailed documentation.
 */
package com.example.voxmanifestorapp.ui.agent.timer

/**
 * Represents the state of the timer in the CHECK_IN phase.
 * Follows the unidirectional data flow pattern through AgentCortex.
 */
sealed class TimerState {
    /**
     * Timer is not running and has not been started.
     */
    object Inactive : TimerState()

    /**
     * Timer is actively running with the specified remaining time.
     * @param remainingTimeMillis The remaining time in milliseconds
     */
    data class Active(val remainingTimeMillis: Long) : TimerState()

    /**
     * Timer has completed its duration.
     */
    object Expired : TimerState()

    companion object {
        const val DEFAULT_DURATION_MS = 600_000L // 10 minutes
        const val MIN_DURATION_MS = 60_000L // 1 minute
        const val MAX_DURATION_MS = 600_000L // 10 minutes
        const val UPDATE_INTERVAL_MS = 1000L // 1 second

        /**
         * Formats the remaining time in milliseconds to a human-readable string.
         * @param remainingTimeMillis The remaining time in milliseconds
         * @return A string in the format "MM:SS"
         */
        fun formatTime(remainingTimeMillis: Long): String {
            val totalSeconds = remainingTimeMillis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

        /**
         * Validates and adjusts the timer duration to be within allowed bounds.
         * @param durationMillis The desired duration in milliseconds
         * @return A duration clamped between MIN_DURATION_MS and MAX_DURATION_MS
         */
        fun validateDuration(durationMillis: Long): Long {
            return durationMillis.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
        }
    }
}

/**
 * Defines all possible timer control actions that can be initiated from the UI.
 * These intents are processed by the CheckInTimerManager through AgentCortex.
 */
sealed class TimerIntent {
    /**
     * Add one minute to the current timer duration.
     * The actual duration will be capped at MAX_DURATION_MS.
     */
    object AddTime : TimerIntent()

    /**
     * Subtract one minute from the current timer duration.
     * The actual duration will be capped at MIN_DURATION_MS.
     */
    object SubtractTime : TimerIntent()

    /**
     * Stop the timer and force transition to the next phase.
     */
    object Stop : TimerIntent()
} 