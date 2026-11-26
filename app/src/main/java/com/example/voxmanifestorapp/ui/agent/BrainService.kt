package com.example.voxmanifestorapp.ui.agent

import android.util.Log
import com.example.voxmanifestorapp.data.ConceptActionState
import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.ui.agent.affirmations.AffirmationResponse
import com.example.voxmanifestorapp.ui.agent.affirmations.AffirmationSet
import com.example.voxmanifestorapp.ui.agent.checkin.Response
import com.example.voxmanifestorapp.ui.agent.checkin.Strategy
import com.example.voxmanifestorapp.ui.agent.checkin.StrategySelection
import com.example.voxmanifestorapp.ui.agent.checkin.TransitionDecision
import com.example.voxmanifestorapp.ui.agent.checkin.ConversationalTheme
import com.example.voxmanifestorapp.ui.agent.checkin.ThemesWrapper
import com.example.voxmanifestorapp.ui.agent.checkin.ThemeReinterpretationResponse
import com.example.voxmanifestorapp.ui.agent.checkin.TransitionActionPlan
import com.example.voxmanifestorapp.ui.agent.checkin.PhaseSuggestionResult
import com.example.voxmanifestorapp.ui.agent.checkin.TransitionMessageResponse
import kotlinx.serialization.json.Json

open class BrainService(apiKey: String) : BrainServiceBase(apiKey) {
    private val CHECKIN_MODEL = "gemini-3.5-flash"

    suspend fun getNextConceptAction(prompt: String): Result<BrainDecision> {
        logStatus("About to prompt brain as follows: \n$prompt")

        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)
            response.text?.let { jsonString ->
                /** removing logs of raw data as its cloggin up the logs
                 */
                //Log.d(TAG, "Model response received: ${response.text}")
                val cleanedJson = cleanJsonResponse(jsonString)
                //Log.d(TAG, "Cleaned response: " + cleanedJson)

                try {
                    // uses structure in BrainResponse class to decode model json output
                    val json = Json.decodeFromString<BrainResponse>(cleanedJson)
                    // parse the BrainResponse into a valid BrainDecision object that can be used by the agent
                    Result.success(
                        BrainDecision(
                            toolName = json.toolName,
                            parameters = json.parameters,
                            action = ConceptActionState.valueOf(json.action),
                            plan = json.plan
                        ))
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing brain response: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    Result.failure(e)
                }
            } ?: Result.failure(Exception("Empty response from brain"))
        }
    }



    /**
     * DialogueChain integration: Evaluates check-in transition decisions.
     * Used by CheckInDialogueChain.evaluateTransition()
     */
    suspend fun getCheckInEvaluation(prompt: String): Result<TransitionDecision> {
        logStatus("Requesting CHECK_IN transition evaluation from brain")
        
        // Console logging for debugging
        Log.d(TAG, "🧠 [BRAIN - CHAIN 1] getCheckInEvaluation() called")
        //Log.d(TAG, "🧠 [BRAIN - CHAIN 1] Prompt received (${prompt.length} chars):\n$prompt")
        Log.d(TAG, "🧠 [BRAIN - CHAIN 1] Sending to LLM...")

        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)
            
            // Console log raw response
            Log.d(TAG, "🧠 [BRAIN - CHAIN 1] Raw LLM response: ${response.text}")
            Log.d(TAG, "🧠 [BRAIN - CHAIN 1] =====================================")
            
            response.text?.let { jsonString ->
                try {
                    val cleanedJson = cleanJsonResponse(jsonString)
                    val json = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        coerceInputValues = true
                    }
                    val parsedResponse = json.decodeFromString<TransitionDecision>(cleanedJson)
                    Result.success(parsedResponse)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing CHECK_IN evaluation response: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    Result.failure(e)
                }
            } ?: Result.failure(Exception("Empty response from model"))
        }
    }

    /**
     * DialogueChain integration: Strategy selection for check-in conversations.
     * Used by CheckInDialogueChain for Chain 2 (strategy selection).
     */
    suspend fun selectConversationStrategy(prompt: String): Result<StrategySelection> {
        logStatus("🔍 Requesting strategy selection from brain")
        
        // Console logging for debugging
        Log.d(TAG, "🧠 [BRAIN - CHAIN 2] selectConversationStrategy() called")
        Log.d(TAG, "🧠 [BRAIN - CHAIN 2] Prompt received (${prompt.length} chars):\n$prompt")
        Log.d(TAG, "🧠 [BRAIN - CHAIN 2] Sending to LLM...")
        
        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)
            
            // Console log raw response
            Log.d(TAG, "🧠 [BRAIN - CHAIN 2] Raw LLM response: ${response.text}")
            Log.d(TAG, "🧠 [BRAIN - CHAIN 2] =====================================")
            
            response.text?.let { jsonString ->
                try {
                    val cleanedJson = cleanJsonResponse(jsonString)
                    val json = Json { 
                        ignoreUnknownKeys = true 
                        isLenient = true
                        coerceInputValues = true
                    }
                    
                    // Direct deserialization like other chains
                    val selection = json.decodeFromString<StrategySelection>(cleanedJson)
                    logStatus("Selected strategy: ${selection.strategy.name}")
                    Result.success(selection)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing strategy selection: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    
                    // Fallback for invalid enum or other parsing errors
                    Result.success(
                        StrategySelection(
                            strategy = Strategy.REFLECTIVE_MIRRORING,
                            reasoning = "Fallback - parsing error: ${e.message}"
                        )
                    )
                }
            } ?: Result.failure(Exception("Empty response"))
        }
    }

    /**
     * Gets simple text guidance from the LLM for Core Loop conversation flow.
     * Returns plain text response that can be spoken directly.
     *
     * @param prompt The full prompt to send to the LLM
     * @return Result containing either a String response or an error
     */
    suspend fun getCoreLoopGuidance(prompt: String): Result<String> {
        logStatus("About to prompt brain for Core Loop guidance:\n$prompt")

        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)
            response.text?.let { textResponse ->
                Result.success(textResponse.trim())
            } ?: Result.failure(Exception("Empty response from brain"))
        }
    }

    // Store the base prompt as a constant since it's substantial
    private fun getAffirmationPrompt(numAffirmations: Int) = """
        You are a genie helping users manifest their wishes into their lives through affirmations.
        For each wish, provide exactly $numAffirmations powerful, positive, first-person affirmations.
        Each affirmation should be clear, concise, and empowering.
        
        Format your response as JSON with this structure:
        {
          "wishes": [
            {
              "wish": "the original wish text",
              "affirmations": ["string", "string", ...]  // Array of $numAffirmations affirmations
            }
          ]
        }
        
        Important: 
        - Each affirmation must be in first person ("I am...", "I have...", "I create...")
        - Make each affirmation powerful, present tense, and positive
        - For each wish, provide exactly $numAffirmations different affirmations
        - Make each affirmation unique and specifically tailored to the wish
        
        Do NOT add any quotes or text before the opening bracket { or after the closing bracket } at the beginning and end of your response!

    """.trimIndent()

    suspend fun generateAffirmations(wishes: List<Manifestation>, numAffirmations: Int = 1): Result<List<AffirmationSet>> {
        return try {
            // Build the complete prompt with the user's wishes
            val fullPrompt = buildString {
                append(getAffirmationPrompt(numAffirmations))
                append("\n\nHere are the wishes to create affirmations for:\n")
                wishes.forEachIndexed { index, wish ->
                    append("${index + 1}. ${wish.title}\n")
                }
            }

//            Log.d(TAG,"[Brain] Sending prompt to Gemini:\n$fullPrompt")

            val response = model.generateContent(
                fullPrompt
            )
//            Log.d(TAG, "[Brain] Raw response from Gemini: ${response.text}")

            val jsonString = response.text ?: throw Exception("No response from model")
            //val cleanJson = cleanJsonResponse(jsonString)

            // Parse the JSON response into our data class
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
            }

            val parsedResponse = json.decodeFromString<AffirmationResponse>(jsonString.trim())
//            Log.d(TAG,"[Brain] Successfully parsed ${parsedResponse.wishes.size} affirmation sets")

            Result.success(parsedResponse.wishes)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * DialogueChain integration: Theme extraction from current user message.
     * Used by CheckInDialogueChain.extractThemes()
     */
    suspend fun extractThemesFromCurrentUserMessage(prompt: String): Result<List<ConversationalTheme>> {
        logStatus("Extracting themes from current user message.")
        //logStatus("Prompt n$prompt")
        
        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)
            
            response.text?.let { jsonString ->
                try {
                    val cleanedJson = cleanJsonResponse(jsonString)
                    val json = Json { 
                        ignoreUnknownKeys = true 
                        isLenient = true
                        coerceInputValues = true
                    }
                    
                    // Parse directly as ThemesWrapper to match the prompt format
                    val wrapper = json.decodeFromString<ThemesWrapper>(cleanedJson)
                    Result.success(wrapper.themes)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing themes response: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    // Properly propagate the error instead of returning an empty list
                    Result.failure(Exception("Failed to parse themes response: ${e.message}", e))
                }
            } ?: Result.failure(Exception("Empty response from model"))
        }
    }
    
    /**
     * DialogueChain integration: Strategy selection that incorporates themes.
     * Used by CheckInDialogueChain.generateResponseWithThemes()
     */
    suspend fun selectConversationStrategyWithThemes(prompt: String): Result<StrategySelection> {
        logStatus("🔍 Requesting theme-aware strategy selection from brain")
        
        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)
            
            response.text?.let { jsonString ->
                try {
                    val cleanedJson = cleanJsonResponse(jsonString)
                    val json = Json { 
                        ignoreUnknownKeys = true 
                        isLenient = true
                        coerceInputValues = true
                    }
                    
                    // Direct deserialization like other chains
                    val selection = json.decodeFromString<StrategySelection>(cleanedJson)
                    logStatus("Selected strategy with themes: ${selection.strategy.name}")
                    Result.success(selection)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing theme-aware strategy selection: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    
                    // Fallback for invalid enum or other parsing errors
                    Result.success(
                        StrategySelection(
                            strategy = Strategy.REFLECTIVE_MIRRORING,
                            reasoning = "Fallback - parsing error: ${e.message}"
                        )
                    )
                }
            } ?: Result.failure(Exception("Empty response"))
        }
    }
    
    /**
     * DialogueChain integration: Generates check-in responses with theme awareness.
     * Used by CheckInDialogueChain.generateResponseWithThemes()
     */
    suspend fun generateCheckInResponse(prompt: String): Result<Response> {
        logStatus("Requesting theme-aware CHECK_IN response generation from brain using ${CHECKIN_MODEL}")
        
        return withNetworkErrorHandling {
            val checkInModel = createModel(CHECKIN_MODEL)
            val response = checkInModel.generateContent(prompt)
            
            response.text?.let { jsonString ->
                try {
                    val cleanedJson = cleanJsonResponse(jsonString)
                    val json = Json { 
                        ignoreUnknownKeys = true 
                        isLenient = true
                        coerceInputValues = true
                    }
                    val parsedResponse = json.decodeFromString<Response>(cleanedJson)
                    Result.success(parsedResponse)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing theme-aware CHECK_IN response: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    Result.failure(e)
                }
            } ?: Result.failure(Exception("Empty response from model"))
        }
    }

    /**
     * DialogueChain integration: Reinterprets themes for natural conversation.
     * Used by CheckInDialogueChain.reinterpretThemesForConversation() (Chain A)
     */
    suspend fun reinterpretThemesForConversation(prompt: String): Result<ThemeReinterpretationResponse> {
        logStatus("🔄 CHAIN A: Requesting theme reinterpretation from brain")
        
        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)
            
            response.text?.let { jsonString ->
                try {
                    val cleanedJson = cleanJsonResponse(jsonString)
                    val json = Json { 
                        ignoreUnknownKeys = true 
                        isLenient = true
                        coerceInputValues = true
                    }
                    val parsedResponse = json.decodeFromString<ThemeReinterpretationResponse>(cleanedJson)
                    logStatus("🔄 CHAIN A: Successfully reinterpreted themes")
                    Result.success(parsedResponse)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing theme reinterpretation response: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    Result.failure(e)
                }
            } ?: Result.failure(Exception("Empty response from model"))
        }
    }

    /**
     * DialogueChain integration: Generates theme-based action suggestions.
     * Used by CheckInDialogueChain.generateThemeBasedActionSuggestion() (Chain B)
     */
    suspend fun generateThemeBasedActionSuggestion(prompt: String): Result<TransitionActionPlan> {
        logStatus("🔄 CHAIN B: Requesting theme-based action suggestion from brain")

        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)

            response.text?.let { jsonString ->
                try {
                    val cleanedJson = cleanJsonResponse(jsonString)
                    val json = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        coerceInputValues = true
                    }
                    val parsedResponse = json.decodeFromString<TransitionActionPlan>(cleanedJson)
                    logStatus("🔄 CHAIN B: Successfully generated structured action plan")
                    Result.success(parsedResponse)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing transition action plan: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    Result.failure(e)
                }
            } ?: Result.failure(Exception("Empty response from model"))
        }
    }

    /**
     * NEW TransitionChain Chain A: Analyzes themes and wishes to suggest optimal next phase.
     * Used by restructured TransitionChain.makePhaseSuggestion()
     */
    suspend fun makePhaseSuggestion(prompt: String): Result<PhaseSuggestionResult> {
        logStatus("🔄 NEW CHAIN A: Requesting phase suggestion from brain")

        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)

            response.text?.let { jsonString ->
                try {
                    val cleanedJson = cleanJsonResponse(jsonString)
                    val json = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        coerceInputValues = true
                    }
                    val parsedResponse = json.decodeFromString<PhaseSuggestionResult>(cleanedJson)

                    // Detailed logging for phase suggestion result
                    Log.d(TAG, "🔄 NEW CHAIN A: Phase suggestion result received")
                    Log.d(TAG, "🔄 NEW CHAIN A: Suggested phase: ${parsedResponse.suggestedPhase}")
                    Log.d(TAG, "🔄 NEW CHAIN A: Target wish ID: ${parsedResponse.targetWishId}")
                    Log.d(TAG, "🔄 NEW CHAIN A: Reasoning: ${parsedResponse.reasoning}")
                    Log.d(TAG, "🔄 NEW CHAIN A: Raw JSON response: $jsonString")

                    logStatus("🔄 NEW CHAIN A: Successfully generated phase suggestion")
                    Result.success(parsedResponse)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing phase suggestion response: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    Result.failure(e)
                }
            } ?: Result.failure(Exception("Empty response from model"))
        }
    }

    /**
     * NEW TransitionChain Chain B: Crafts natural transition message incorporating themes and selected phase.
     * Used by restructured TransitionChain.craftTransitionMessage()
     */
    suspend fun craftTransitionMessage(prompt: String): Result<TransitionMessageResponse> {
        logStatus("🔄 NEW CHAIN B: Requesting transition message crafting from brain")

        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)

            response.text?.let { jsonString ->
                try {
                    val cleanedJson = cleanJsonResponse(jsonString)
                    val json = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        coerceInputValues = true
                    }
                    val parsedResponse = json.decodeFromString<TransitionMessageResponse>(cleanedJson)

                    // Detailed logging for message crafting result
                    Log.d(TAG, "🔄 NEW CHAIN B: Transition message result received")
                    Log.d(TAG, "🔄 NEW CHAIN B: Message length: ${parsedResponse.message.length} chars")
                    Log.d(TAG, "🔄 NEW CHAIN B: Message: ${parsedResponse.message}")
                    Log.d(TAG, "🔄 NEW CHAIN B: Reasoning: ${parsedResponse.reasoning}")
                    Log.d(TAG, "🔄 NEW CHAIN B: Raw JSON response: $jsonString")

                    logStatus("🔄 NEW CHAIN B: Successfully crafted transition message")
                    Result.success(parsedResponse)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing transition message response: ${e.message}")
                    Log.e(TAG, "Raw response: $jsonString")
                    Result.failure(e)
                }
            } ?: Result.failure(Exception("Empty response from model"))
        }
    }

    /**
     * Simple text generation for wish creation and refinement.
     * Used by WishCreationManager for generating and refining wishes.
     */
    suspend fun generateSimpleText(prompt: String): Result<String> {
        logStatus("Generating simple text for wish creation")

        return withNetworkErrorHandling {
            val response = model.generateContent(prompt)
            response.text?.let { text ->
                Result.success(text.trim())
            } ?: Result.failure(Exception("Empty response from model"))
        }
    }

}
