# Check-In DialogueChain System

## System Context & Architecture Overview

### VoxManifestor's Voice-Driven Architecture

The Check-In DialogueChain operates within VoxManifestor's sophisticated voice-first conversation system. Understanding this broader context is essential for working on the check-in functionality.

**Core Philosophy:** VoxManifestor is designed as a conversational "genie" that helps users manifest their desires through structured voice interactions. The app centers around a Core Loop that guides users through manifestation stages, with Check-In serving as the welcoming entry point for each session.

### Voice Interaction Flow

The complete flow from user interaction to check-in response:

```
UI Layer: AgentToolbar toggle conversation button press
    ↓
ViewModel Layer: AgentViewModel.toggleConversation()
    ↓
Intent Processing: RequestToggleConversation → AgentCortex
    ↓
Agent Logic: ConversationAgent.toggleBrainConversation()
    ↓
Core Loop Management: initiateCoreLoopProcess() → progressCoreLoop()
    ↓
Check-In Phase: progressCheckIn() 
    ↓
    ┌─────────────────────────────────────────────────────┐
    │               DIALOGUECHAIN SYSTEM                  │
    │  ┌─────────────────────────────────────────────┐    │
    │  │  Chain 1: Transition Evaluation             │    │
    │  │  Chain 1B: Theme Extraction                 │    │
    │  │  Chain 2: Strategy Selection (Theme-aware)  │    │
    │  │  Chain 3: Response Generation (Theme-aware) │    │
    │  └─────────────────────────────────────────────┘    │
    └─────────────────────────────────────────────────────┘
    ↓
AI Response: BrainService (Google Gemini) → Text-to-Speech
    ↓
Audio Output to User
    ↓
[User responds via voice]
    ↓
UI Layer: AgentCortex.UiIntent.SendResponse submission
    ↓
[Process repeats through DialogueChain system until transition]
```

### Core Loop Integration

The Check-In is **Phase 1** of VoxManifestor's 7-phase Core Loop (defined in `CoreLoopState.kt`):

1. **CHECK_IN** ← *DialogueChain system operates here*
2. **WISH_COLLECTION** - Capturing new wishes for empty slots
3. **PRESENT_STATE_EXPLORATION** - Exploring current reality 
4. **DESIRED_STATE_EXPLORATION** - Defining desired outcomes
5. **CONTRAST_ANALYSIS** - Analyzing gaps between states
6. **AFFIRMATION_PROCESS** - Reinforcing commitment
7. **LOOP_DECISION** - Continuing or ending session

**Role of Check-In:** Acts as the "conversation starter" that:
- Welcomes users back to their manifestation journey
- Captures top-of-mind thoughts and concerns via voice journaling
- Establishes the session's focus and emotional tone
- Determines which Core Loop phases are most relevant for this session

### State Management Architecture

- **AgentCortex**: Single source of truth for all agent state, exposes reactive `StateFlow` for UI observation
  - `checkInState: StateFlow<CheckInState>` - Sub-phase tracking within CHECK_IN
  - `coreLoopState: StateFlow<CoreLoopState>` - Main phase progression across all 7 phases
  - `conversationHistory: StateFlow<List<ConversationEntry>>` - Complete dialogue history
  - Uses `MutableSharedFlow<UiIntent>` for UI→Agent communication with 64-event buffer capacity
- **ConversationAgent**: Central controller that orchestrates dialogue flow and state transitions
- **CheckInSystem**: Canonical data types and state management for check-in specific logic
- **DialogueChain**: Modular 4-chain architecture for sophisticated dialogue processing

---

## DialogueChain System Overview

The Check-In system uses a sophisticated multi-step dialogue process to create engaging, personalized conversations that help users reflect on their current state and prepare for their manifestation work.

**What it does:**
- Welcomes users back with personalized check-ins
- Facilitates voice journaling and reflection
- Extracts conversation themes to maintain context across sessions
- Determines when to transition to other Core Loop phases
- Adapts conversation strategies based on user engagement and identified themes

**How it works:**
- Uses AI (LLM) to evaluate transitions, extract themes, select strategies, and generate responses
- Tracks engagement metrics and conversation themes to inform decision-making
- Follows a structured 4-chain architecture for reliable dialogue flow

---

## Current Architecture

### The 4-Chain Process

Every check-in interaction follows this sequence. Errors originating from LLM calls or other internal processing are handled as described in the "Error Handling Flow and Strategy" section below.

1. **Chain 1: Transition Evaluation**
   - *Question*: "Should we move to the next phase?"
   - *Method*: Analyzes conversation history + engagement metrics via LLM (invokes `BrainService.getCheckInEvaluation()`).
   - *Output*: `TransitionDecision` (shouldTransition: Boolean + reasoning: String) on success.

2. **Chain 1B: Theme Extraction**
   - *Question*: "What themes are present in the user's message?"
   - *Method*: Analyzes current user message + existing themes via LLM (invokes `BrainService.extractThemesFromCurrentUserMessage()`).
   - *Output*: `List<ConversationalTheme>` (title: String + observations: List<String>) on success.

3. **Chain 2: Strategy Selection (Theme-aware)**
   - *Question*: "What conversation approach should we use based on themes and context?"
   - *Method*: Evaluates user's current state, needs, and conversation themes via LLM (invokes `BrainService.selectConversationStrategyWithThemes()`).
   - *Output*: `StrategySelection` (strategy: Strategy + reasoning: String) on success.

4. **Chain 3: Response Generation (Theme-aware)**
   - *Question*: "What should we actually say that incorporates relevant themes?"
   - *Method*: Uses selected strategy + conversation history + coaching examples + themes (invokes `BrainService.getCheckInResponseWithThemes()`).
   - *Output*: `Response` (response: String + reasoning: String + strategy: Strategy) on success.

---

## Error Handling

The Check-In system uses a sophisticated error handling approach to manage failures at different system layers. Due to its complexity, this topic has been moved to a dedicated document:

**See: [`context/checkin_error_handling.md`](checkin_error_handling.md) for detailed information on:**
- Error propagation architecture across system layers
- Dual-mechanism approach (Result vs Exceptions)
- Complete error flow from API to user interface
- Error handling implementation in each component

---

### Key Files & Responsibilities

**Core Check-In Files:**

- **`CheckInSystem.kt`** - *Canonical Data Hub*
  - Defines all canonical types: `Strategy`, `TransitionDecision`, `StrategySelection`, `Response`, `ConversationalTheme`
  - Contains `CheckInState`, `UserEngagementMetrics` data classes
  - Houses `CheckInSystem.Prompts` with system instructions and prompt builders

- **`DialogueChain.kt`** - *Interface Contract & Implementation*
  - **Interface**: Defines the process contract with `processTurn()` as the main entry point. `processTurn()` can throw exceptions originating from `BrainService` or internal `DialogueChain` processing, which are to be handled by the caller (typically `ConversationAgent`).
  - **ConversationContext**: Data structure containing history, metrics, checkInState, themes, and phase
  - **DialogueChainResponse**: Comprehensive response package with text, reasoning, strategy, and themes
  - **CheckInDialogueChain Class**: Core implementation of the DialogueChain interface
    - `processTurn()` - Main orchestration method. Propagates exceptions from internal chain methods.
    - `extractThemes()` - Chain 1B implementation. Propagates exceptions from `BrainService` (e.g., via `getOrThrow()` if `BrainService` returns `Result`).
    - `evaluateTransition()` - Chain 1 implementation. Propagates exceptions from `BrainService`.
    - `generateResponseWithThemes()` - Orchestrates Chain 2 and 3. Propagates exceptions from `BrainService`.
    - Various prompt builders and utility functions for each chain

**Integration & Controller Files:**

- **`ConversationAgent.kt`** - *Main Controller*
  - The `ConversationAgent` is the primary orchestrator of the entire conversational flow in the app. It is responsible for initiating the Core Loop, managing state transitions, and handling errors. For the Check-In phase, it specifically calls `CheckInDialogueChain.processTurn()` and processes the `DialogueChainOutcome`.
  - For a detailed breakdown of its architecture and responsibilities, see the dedicated **[`convo_agent_context.md`](convo_agent_context.md)** file.

- **`BrainService.kt`** - *AI/LLM Interface*
  - **Model Configuration**: Gemini 2.0 Flash (default), Gemini 2.0 Flash for check-in responses
  - `getCheckInEvaluation()` - Chain 1: Transition evaluation calls. May throw exceptions (e.g., `IOException`, `BrainServiceException`) or return `Result.failure` for network/API errors.
  - `extractThemesFromCurrentUserMessage()` - Chain 1B: Theme extraction calls. May throw exceptions or return `Result.failure`.
  - `selectConversationStrategyWithThemes()` - Chain 2: Strategy selection calls. May throw exceptions or return `Result.failure`.
  - `getCheckInResponseWithThemes()` - Chain 3: Response generation calls. May throw exceptions or return `Result.failure`.
  - Handles Google Gemini API integration and JSON parsing. Responsible for translating low-level API errors into catchable exceptions or `Result.failure` instances.

**State Management Files:**

- **`AgentCortex.kt`** - *Central State Hub*
  - Manages `checkInState` as `StateFlow` including `activeThemes`
  - Exposes conversation history and dialogue state
  - Processes UI intents and coordinates with ConversationAgent
  - Provides `updateActiveThemes()` for theme management

- **`CoreLoopState.kt`** - *Core Loop State*
  - Manages `ConversationPhase` (main phases including CHECK_IN)
  - Tracks current wish index and understanding
  - Coordinates phase transitions across the entire Core Loop

**Supporting Files:**

- **`CoachingTranscripts.kt`** - *Training Examples*
  - Contains strategy-specific conversation examples for AI training
  - Provides `getExamplesForStrategy()` for prompt enhancement
  - Houses real coaching conversation patterns

---

## Data Flow

```
User Input → ConversationAgent.progressCheckIn()
    (try {)
    ↓
CheckInDialogueChain.processTurn()
    ↓
    | → Chain 1: evaluateTransition()
    |     → BrainService (LLM call) - Originates Exception / Result.failure on error
    |     → TransitionDecision (on success) / Throws Exception (on BrainService error via DialogueChain)
    ↓
    | → Chain 1B: extractThemes()
    |     → BrainService (LLM call) - Originates Exception / Result.failure on error
    |     → List<ConversationalTheme> (on success) / Throws Exception (on BrainService error via DialogueChain)
    ↓
    | → Chain 2+3: generateResponseWithThemes()
    |     → BrainService.selectConversationStrategyWithThemes() - Originates Exception / Result.failure
    |     → BrainService.getCheckInResponseWithThemes() - Originates Exception / Result.failure
    |     → Response (on success) / Throws Exception (on BrainService error via DialogueChain)
    ↓
(} catch (e: Exception) { ConversationAgent centrally handles the error, logs, updates UI/state, decides next steps })
    ↓
Update themes and metrics (if `processTurn` was successful) → Continue or transition (based on successful outcome or post-error handling by ConversationAgent)
```

### State Management

- **Main Phase**: `ConversationPhase.CHECK_IN` (from `CoreLoopState.kt`)
- **Sub-Phase**: `CheckInState` with `CheckInStage.OPENING` or `TRANSITION`
- **Themes**: `List<ConversationalTheme>` tracks conversation topics across turns
- **Metrics**: `UserEngagementMetrics` tracks response patterns
- **Storage**: All state flows through `AgentCortex`

---

## Detailed DialogueChain Data Flow

This section maps the complete data flow within the DialogueChain system itself:

### Initial Setup & Context Building

### State Management: The Snapshot Pattern

A key architecture pattern in the DialogueChain system is the use of state snapshots for conversation turns. This pattern ensures conversation consistency by working with an immutable copy of the state at the beginning of a turn.

**Key Concepts:**

1. **`ConversationContext` Class**:
   - The central container class that holds all immutable snapshots for a conversation turn
   - Contains three key components:
     - `historySnapshot`: The conversation history at the time the turn started
     - `checkInStateSnapshot`: An immutable copy of the CheckInState
     - `userWishes`: Contextual information about the user's defined wishes

2. **`checkInState` vs `checkInStateSnapshot`**:
   - `checkInState`: The live, current state in AgentCortex that can be updated during processing
   - `checkInStateSnapshot`: An immutable copy of the state taken at the beginning of a turn

3. **Usage Pattern**:
   - When starting a conversation turn, the system creates a snapshot with `takeConversationSnapshot()`
   - All processing within `processTurn()` works with fields from this snapshot
   - Updates are computed locally and included in the returned `DialogueChainOutcome`
   - ConversationAgent applies these updates to the live state *after* processing completes

4. **Implementation Guidelines**:
   - Always access state from `initialTurnContext.checkInStateSnapshot` during turn processing
   - Never modify the snapshot directly (it should be treated as immutable)
   - Compute changes locally (metrics, themes) but don't apply them to AgentCortex
   - Return all computed changes in the DialogueChainOutcome
   - When creating updated states, use `initialTurnContext.checkInStateSnapshot.copy()`, not `checkInState.copy()`

This pattern prevents race conditions and ensures that each turn operates on a consistent view of the state, regardless of potential asynchronous updates happening elsewhere in the system.

### Conversation Strategies

The system uses 8 predefined strategies (defined in `CheckInSystem.Strategy`):

**Available Strategies:**
- **`CONVERSATION_STARTING`** - Beginning new sessions ("How have things been since we last spoke?")
- **`RAPPORT_BUILDING`** - Building trust, first-time users ("That makes complete sense...")
- **`EXPERIENCE_EXPLORATION`** - User mentions specific events ("Can you tell me more about...")
- **`REFLECTION_DEEPENING`** - Going deeper into insights ("What do you think drives that...")
- **`REFLECTIVE_MIRRORING`** - Acknowledging user's feelings ("It sounds like you're feeling...")
- **`EMOTIONAL_VALIDATION`** - User expresses difficult emotions ("That's completely understandable...")
- **`PERSPECTIVE_SHIFTING`** - Offering new viewpoints ("What if we looked at it this way...")
- **`AFFIRM_SUPPORT`** - Celebrating wins, showing support ("That's fantastic! You should be proud...")

**Examples**: See `CoachingTranscripts.kt` for detailed conversation examples for each strategy.

---

## Detailed DialogueChain Data Flow

This section maps the complete data flow within the DialogueChain system itself:

### Initial Setup & Context Building
```
ConversationAgent.progressCheckIn()
    ↓
Gather conversation context:
    - conversationHistory from AgentCortex
    - engagementMetrics from checkInState
    - activeThemes from checkInState
    - current CheckInStage (OPENING/TRANSITION)
    - current ConversationPhase
    ↓
Create ConversationContext snapshot
    ↓
Pass to CheckInDialogueChain.processTurn()
```

### Chain 1: Transition Evaluation
```
Within processTurn():
    ↓
evaluateTransition()
    ↓
buildTransitionPrompt():
    - conversation history (last 10 entries)
    - formatted engagement metrics
    - system instructions from CheckInSystem.Prompts
    ↓
BrainService.getCheckInEvaluation(prompt)
    → Google Gemini API call
    → JSON response parsing
    ↓
Return TransitionDecision:
    - shouldTransition: Boolean
    - reasoning: String
```

### Chain 1B: Theme Extraction
```
Within processTurn() (if not transitioning):
    ↓
extractThemes(currentUserMessage, existingThemes)
    ↓
buildThemeExtractionPrompt():
    - current user message
    - existing themes from CheckInState
    - theme extraction criteria
    ↓
BrainService.extractThemesFromCurrentUserMessage(prompt)
    → Google Gemini API call
    → JSON response parsing
    ↓
Return List<ConversationalTheme>:
    - title: String
    - observations: List<String>
    ↓
Combine with existing themes
```

### Chain 2: Strategy Selection (Theme-aware)
```
Within processTurn() (within generateResponseWithThemes):
    ↓
buildStrategySelectionWithThemesPrompt():
    - recent conversation (last 5 entries)
    - active themes list
    - available strategies list
    - selection criteria
    ↓
BrainService.selectConversationStrategyWithThemes(prompt)
    → Google Gemini API call
    → Parse strategy enum from response
    ↓
Return StrategySelection:
    - strategy: Strategy enum
    - reasoning: String
```

### Chain 3: Response Generation (Theme-aware)
```
Within processTurn() (within generateResponseWithThemes):
    ↓
buildResponseWithThemesPrompt():
    - conversation history
    - selected strategy
    - active themes
    - strategy-specific examples from CoachingTranscripts
    ↓
BrainService.getCheckInResponseWithThemes(prompt)
    → Google Gemini API call
    → Response validation and improvement
    ↓
Return GeneratedResponse:
    - response: String
    - reasoning: String
    - strategy: Strategy
```

### Response Packaging & State Updates
```
Return DialogueChainOutcome:
    - text: String (the response text)
    - reasoning: String (reasoning for this response)
    - strategy: Strategy (the strategy used)
    - themesForThisTurn: List<ConversationalTheme> (all active themes)
    - updatedMetrics: UserEngagementMetrics (metrics calculated during turn)
    - transitionDecision: TransitionDecision? (if transitioning)
    ↓
ConversationAgent:
    - Updates active themes in AgentCortex
    - Updates engagement metrics from DialogueChainOutcome
    - Speaks response or handles transition
    - Updates conversation history
    ↓
Await next user input cycle (reactive via StateFlow observation)
```

---

## Integration Points

### With ConversationAgent
- `ConversationAgent.progressCheckIn()` orchestrates the entire check-in flow
- `AgentCortex.updateActiveThemes()` maintains theme persistence across turns
- **Migration Status**: Enhanced with theme extraction and theme-aware processing

### With BrainService (AI/LLM)
- `brainService.getCheckInEvaluation()` - Chain 1 (transition decisions)
- `brainService.extractThemesFromCurrentUserMessage()` - Chain 1B (theme extraction)
- `brainService.selectConversationStrategyWithThemes()` - Chain 2 (theme-aware strategy selection)  
- `brainService.getCheckInResponseWithThemes()` - Chain 3 (theme-aware response generation)

### With Core Loop
- Check-in is Phase 1 of the 7-phase Core Loop
- Transitions to `WISH_COLLECTION` when complete
- Extracted themes can provide context for later phases