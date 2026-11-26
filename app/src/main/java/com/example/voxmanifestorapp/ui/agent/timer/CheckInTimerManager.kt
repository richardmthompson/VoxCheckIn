package com.example.voxmanifestorapp.ui.agent.timer

import com.example.voxmanifestorapp.ui.agent.AgentCortex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Manages the timer for the CHECK_IN phase.
 * 
 * This class is responsible for:
 * - Managing the timer lifecycle (start, stop, add/subtract time)
 * - Updating timer state through AgentCortex
 * - Running background timer monitoring
 * - Ensuring thread-safe state updates
 * 
 * State Management:
 * - Uses AgentCortex as the single source of truth
 * - Follows unidirectional data flow pattern
 * - All state updates go through AgentCortex.updateCheckInState()
 * 
 * Thread Safety:
 * - Timer updates run on Dispatchers.Default
 * - State updates are synchronized through AgentCortex
 * - Coroutine scope is managed by the manager
 */
class CheckInTimerManager(
    private val agentCortex: AgentCortex,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val TAG = "TIMER: MANAGER:"

    /**
     * Background job that monitors the timer state.
     * Cancelled when timer is stopped or expires.
     */
    private var timerJob: Job? = null

    /**
     * Starts the timer if it's not already running.
     * 
     * Implementation:
     * 1. Checks if timer is already running
     * 2. Updates state to Active with default duration
     * 3. Starts background timer monitoring
     * 
     * State Flow:
     * Inactive -> Active(remainingTimeMillis)
     */
    fun start() {
        if (timerJob != null) {
            Log.d(TAG, "Timer already running")
            return
        }

        coroutineScope.launch {
            agentCortex.updateCheckInState { currentState ->
                currentState.copy(
                    timerState = when (currentState.timerState) {
                        is TimerState.Inactive -> TimerState.Active(TimerState.DEFAULT_DURATION_MS)
                        else -> {
                            Log.d(TAG, "Timer already started with state: ${currentState.timerState}")
                            currentState.timerState
                        }
                    }
                )
            }
            startTimerWatcher()
        }
    }

    /**
     * Stops the timer and cleans up resources.
     * 
     * Implementation:
     * 1. Cancels the background timer job
     * 2. Clears the job reference
     * 3. Logs the stop event
     * 
     * Note: Does not update state to Inactive, as this is handled by the caller
     * if needed (e.g., when transitioning to a new phase).
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
        Log.d(TAG, "Timer stopped")
    }

    /**
     * Adds one minute to the timer duration, up to the maximum allowed duration.
     * 
     * Implementation:
     * 1. Only affects Active state
     * 2. Validates new duration against MAX_DURATION_MS
     * 3. Updates state with new duration
     * 
     * State Flow:
     * Active(remainingTimeMillis) -> Active(newRemainingTimeMillis)
     */
    fun addTime() {
        coroutineScope.launch {
            agentCortex.updateCheckInState { currentState ->
                currentState.copy(
                    timerState = when (currentState.timerState) {
                        is TimerState.Active -> {
                            val newDuration = TimerState.validateDuration(
                                currentState.timerState.remainingTimeMillis + TimerState.MIN_DURATION_MS
                            )
                            Log.d(TAG, "Adding time. New duration: ${TimerState.formatTime(newDuration)}")
                            TimerState.Active(newDuration)
                        }
                        else -> currentState.timerState
                    }
                )
            }
        }
    }

    /**
     * Subtracts one minute from the timer duration, down to the minimum allowed duration.
     * 
     * Implementation:
     * 1. Only affects Active state
     * 2. Validates new duration against MIN_DURATION_MS
     * 3. Updates state with new duration
     * 
     * State Flow:
     * Active(remainingTimeMillis) -> Active(newRemainingTimeMillis)
     */
    fun subtractTime() {
        coroutineScope.launch {
            agentCortex.updateCheckInState { currentState ->
                currentState.copy(
                    timerState = when (currentState.timerState) {
                        is TimerState.Active -> {
                            val newDuration = TimerState.validateDuration(
                                currentState.timerState.remainingTimeMillis - TimerState.MIN_DURATION_MS
                            )
                            Log.d(TAG, "Subtracting time. New duration: ${TimerState.formatTime(newDuration)}")
                            TimerState.Active(newDuration)
                        }
                        else -> currentState.timerState
                    }
                )
            }
        }
    }

    /**
     * Forces the timer to expire, triggering a transition to the next phase.
     * 
     * Implementation:
     * 1. Updates state to Expired
     * 2. Stops the timer
     * 3. Logs the forced transition
     * 
     * State Flow:
     * Any -> Expired
     */
    fun forceTransition() {
        coroutineScope.launch {
            // First stop the timer to prevent further updates
            Log.d(TAG, "FORCING TRANSITION: Stopping timer")
            stop()
            
            // Then update state to Expired
            agentCortex.updateCheckInState { currentState ->
                currentState.copy(timerState = TimerState.Expired)
            }
            
            Log.d(TAG, "Timer transition forced")
        }
    }

    /**
     * Starts a background coroutine to monitor the timer state.
     * 
     * Implementation:
     * 1. Runs on Dispatchers.Default for performance
     * 2. Updates every UPDATE_INTERVAL_MS
     * 3. Handles state transitions:
     *    - Active -> Expired when time runs out
     *    - Stops monitoring for non-Active states
     * 
     * State Flow:
     * Active(remainingTimeMillis) -> Active(newRemainingTimeMillis)
     * Active(0) -> Expired
     */
    private fun startTimerWatcher() {
        Log.d(TAG, "Starting timer watcher / creating timerJob")
        timerJob = coroutineScope.launch {
            while (true) {
                delay(TimerState.UPDATE_INTERVAL_MS)
                withContext(Dispatchers.Default) {
                    val currentState = agentCortex.checkInState.value
                    when (currentState.timerState) {
                        is TimerState.Active -> {
                            val remainingTime = (currentState.timerState.remainingTimeMillis - TimerState.UPDATE_INTERVAL_MS)
                                .coerceAtLeast(0L)
                            
                            if (remainingTime <= 0) {
                                Log.d(TAG, "Timer expired")
                                // First stop the timer to prevent further updates
                                stop()
                                
                                // Then update state to Expired
                                agentCortex.updateCheckInState { state ->
                                    state.copy(timerState = TimerState.Expired)
                                }
                            } else {
                                agentCortex.updateCheckInState { state ->
                                    state.copy(timerState = TimerState.Active(remainingTime))
                                }
                            }
                        }
                        else -> stop()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "CheckInTimerManager"
    }
} 