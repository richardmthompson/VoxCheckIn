# Commands Module Architecture

## Overview

The Commands module provides clean function-based architecture for handling discrete voice commands and stateful conversation flows in VoxManifestor. This module follows the successful function-based pattern documented in the agent refactoring.

## Module Structure

### Current Files (Clean Architecture)
- **`CommandFunctions.kt`**: Discrete command handlers (select, define, start, etc.)
- **`CommandMode.kt`**: Stateful conversation flows for wish collection/selection  
- **`README.md`**: This documentation

### Architecture Pattern
- **Function-based**: Commands implemented as standalone functions 
- **Class-based state**: `CommandMode` class for complex stateful workflows
- **Direct integration**: ConversationAgent calls functions directly

## Purpose and Scope

### CommandFunctions.kt
Handles discrete voice commands that perform single actions:
- **Administrative Commands**: select, define, read, quit, help
- **Flow Initiators**: start (conversation loop), affirm (affirmation session)
- **Navigation Commands**: Commands that open specific screens or trigger specific actions

### CommandMode.kt  
Manages stateful conversation flows that require multiple turns:
- **Wish Collection**: Guided process for creating new manifestations in empty slots
- **Wish Selection**: Interactive selection and modification of existing wishes
- **State Management**: Deterministic state transitions with clear conversation steps

This dual approach provides both immediate actions (functions) and extended workflows (class), operating in contrast to the Core Loop's LLM-driven conversational approach.

## Core Responsibilities

### 1. Conversation Flow Orchestration
- Determines whether to collect new wishes or select existing ones
- Manages state transitions through predefined conversation steps
- Handles both single-command mode and continuous conversation loops

### 2. Wish Management Operations
- **Creation**: Guides users through wish creation with validation
- **Selection**: Allows users to select wishes by number for further action
- **Modification**: Provides options to edit, delete, or elaborate on existing wishes
- **Validation**: Confirms user input before committing changes

### 3. State Machine Management
- Maintains internal conversation state (`currentStep`, `selectedManifestation`, `tempWishResponse`)
- Coordinates with external state management through `StateManager`
- Provides encapsulated state specific to command-driven workflows

## Architecture

CommandMode is implemented as a class because it maintains significant internal state:

```kotlin
class CommandMode(
    private val repository: ManifestationRepository,
    private val stateManager: StateManager,
    private val utilities: ConversationUtilities,
    private val voiceProcessor: VoiceProcessor,
    private val navigationManager: NavigationManager
)
```

### Internal State Variables
- `currentStep: ConversationStep?` - Tracks position in conversation flow
- `selectedManifestation: Manifestation?` - Currently selected wish for operations
- `tempWishResponse: String?` - Temporarily holds user input during validation
- `isSingleCommandMode: Boolean` - Controls whether to continue or terminate after operations

## Conversation Flows

### 1. Entry Point Logic
```
enterConversationLoop()
├── Check for empty wish slots
├── If empty slots found → WishCollection flow
└── If all slots full → WishSelection flow
```

### 2. Wish Collection Flow
```
AskForWish → CaptureWish → CheckWish → CaptureValidation → ValidateWish
```

### 3. Wish Selection Flow  
```
SelectWish → CaptureWish → SelectProcess → CaptureProcess → HandleProcessSelection
```

## Integration Points

### With ConversationAgent
- `enterConversationLoop()` - Initiated by ConversationAgent for command-driven workflows
- `processBackgroundInput()` - **NOTE**: This function should remain in ConversationAgent as general input routing logic

### With State Management
- Coordinates with `StateManager` for AgentCortex state updates
- Updates `ConversationType` and `DialogueState` as flows progress
- Manages slot selection through `MainScreenState`

### With Voice System
- Uses `VoiceProcessor` for speech synthesis and user prompts
- Processes voice input through structured conversation steps
- Validates user responses through `ConversationUtilities`

## Integration with ConversationAgent

### Function Calls Pattern
```kotlin
// Direct function calls for discrete commands
import com.example.voxmanifestorapp.ui.agent.commands.*

// In ConversationAgent.handleVoiceInput()
when (command) {
    "select" -> handleSelectCommand(text, repository, agentCortex, ...)
    "define" -> handleDefineCommand(text, repository, agentCortex, ...)
    "start" -> handleStartCommand(agentCortex, speak, ...)
}
```

### Class Instantiation for Stateful Flows
```kotlin
// CommandMode instance for conversation flows
private val commandMode = CommandMode(repository, stateManager, utilities, voiceProcessor, navigationManager)

// Usage in conversation flows
commandMode.enterConversationLoop()
commandMode.selectWishNumber(text)
```

## Removed Files (Duplicates)
- ~~`CommandProcessor.kt`~~: Broken class-based duplicate of CommandFunctions
- ~~`StateMachineFlowFunctions.kt`~~: Standalone function duplicate of CommandMode functionality

These files contained complete duplications of functionality with inconsistent implementations and missing dependencies.

## Design Rationale

### Why a Class?
CommandMode maintains complex internal state that spans multiple conversation turns:
- Multi-step validation workflows require persistent state
- Temporary data storage during user confirmation processes  
- State machine variables that track conversation progress

### State Encapsulation
The command-specific state variables are kept within CommandMode rather than AgentCortex because:
- They are specific to command-driven workflows
- They don't need to be observed by UI components
- They represent temporary conversation state, not application state

### Deterministic Flows
Unlike the Core Loop's LLM-driven approach, CommandMode uses predetermined state machines because:
- Administrative operations require precise control and predictable outcomes
- Users expect consistent behavior for basic wish management
- Error handling and validation are more straightforward with defined paths

## Future Evolution

As the Core Loop system matures, CommandMode flows may be gradually replaced by more intelligent LLM-driven phases. However, this system provides essential administrative functionality and serves as a reliable fallback for precise operations.

The encapsulated state design ensures that CommandMode can coexist with the Core Loop system without conflicts, allowing for gradual migration as needed.