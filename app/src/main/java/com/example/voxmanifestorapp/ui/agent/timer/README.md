# Timer Module

## Overview
The Timer module manages the 10-minute timer for the CHECK_IN phase of VoxManifestor's Core Loop. It provides a robust, state-driven approach to timing user sessions while maintaining thread safety and following unidirectional data flow principles.

## Architecture

### Components
- `TimerState`: Sealed class representing the timer's possible states
  - `Inactive`: Timer not started
  - `Active`: Timer running with remaining time
  - `Expired`: Timer completed
- `CheckInTimerManager`: Manages timer lifecycle and state updates

### State Management
The timer follows a unidirectional data flow pattern:
1. State is stored in `AgentCortex.checkInState.timerState`
2. `CheckInTimerManager` reads state and requests updates
3. UI components observe state through `AgentCortex`

## Integration Points

### With AgentCortex
```kotlin
// State observation
val timerState = agentCortex.checkInState.value.timerState

// State updates
agentCortex.updateCheckInState { currentState ->
    currentState.copy(timerState = TimerState.Active(remainingTime))
}
```

### With CHECK_IN Phase
The timer is a critical component of the CHECK_IN phase:
1. Started when user begins check-in
2. Monitored during conversation
3. Triggers transition when expired

### With UI Layer
UI components can:
1. Observe timer state through `AgentCortex`
2. Display remaining time using `TimerState.formatTime()`
3. Control timer through `CheckInTimerManager` methods

## Usage Examples

### Starting the Timer
```kotlin
val timerManager = CheckInTimerManager(agentCortex)
timerManager.start() // Initializes 10-minute timer
```

### Adding/Subtracting Time
```kotlin
// Add one minute (up to 10 minutes max)
timerManager.addTime()

// Subtract one minute (minimum 1 minute)
timerManager.subtractTime()
```

### Observing Timer State
```kotlin
// In a ViewModel or Composable
val timerState by agentCortex.checkInState.collectAsState()
when (timerState.timerState) {
    is TimerState.Active -> {
        val remainingTime = (timerState.timerState as TimerState.Active).remainingTimeMillis
        val formattedTime = TimerState.formatTime(remainingTime)
        // Update UI
    }
    is TimerState.Expired -> {
        // Handle timer completion
    }
    TimerState.Inactive -> {
        // Handle inactive state
    }
}
```

## State Transitions
```
[Inactive] → [Active] → [Expired]
   ↑           ↓
   └───────────┘ (reset)
```

## Thread Safety
- Timer updates run on `Dispatchers.Default`
- State updates are thread-safe through `AgentCortex`
- Coroutine scope is managed by the manager

## Constants
- `DEFAULT_DURATION_MS`: 600,000 (10 minutes)
- `MIN_DURATION_MS`: 60,000 (1 minute)
- `MAX_DURATION_MS`: 600,000 (10 minutes)
- `UPDATE_INTERVAL_MS`: 1,000 (1 second)

## Best Practices
1. Always update timer state through `AgentCortex`
2. Use `TimerState.formatTime()` for display
3. Handle all timer states in UI
4. Clean up resources by calling `stop()`

## Related Modules
- `AgentCortex`: Central state management
- `CheckInSystem`: Phase-specific logic
- `ConversationAgent`: Phase orchestration 