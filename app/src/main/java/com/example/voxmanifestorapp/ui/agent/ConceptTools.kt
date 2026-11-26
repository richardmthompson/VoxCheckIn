package com.example.voxmanifestorapp.ui.agent

import com.example.voxmanifestorapp.data.ConceptItem
import com.example.voxmanifestorapp.data.ConceptRepository
import com.example.voxmanifestorapp.data.ConceptType
import com.example.voxmanifestorapp.ui.concept.ConceptViewModel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ToolContinuationMethod {
    WAIT_FOR_USER,
    CONTINUE_IMMEDIATE
}

sealed class ConceptTool<T>(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    //val continuationMethod: ToolContinuationMethod
) {
    abstract suspend fun execute(params: ParametersWrapper): ToolResult<T>
}

sealed class ToolResult<T> {
    data class Success<T>(val data: T) : ToolResult<T>()
    data class Error<T>(val message: String) : ToolResult<T>()
}

data class ToolParameter(
    val name: String,
    val description: String,
    val type: String,
    val required: Boolean = true
)


@Serializable
data class SaveRequest(
    val conceptType: String,
    val slotIndex: Int,
    val content: String
)

/** this allows us to use more complex parameters
 * e.g. provide an array of save requests within the parameters field of a tool
 * So when we get a response from the LLM, kotlinx.serialization:
 * Sees the top-level structure matches BrainResponse
 * Inside that, sees parameters matches ParametersWrapper
 * Inside that, sees saveRequests is a list of objects matching SaveRequest
 * The downside is that we need to keep ParametersWrapper populated with whatever parameters we may use in the various tools
 */
@Serializable
data class ParametersWrapper(
    val saveRequests: List<SaveRequest>? = null,
    val question: String? = null,
    val responseText: String? = null,
    // Add other possible parameter types here
)


class QuestionTool(
    private val agent: ConversationAgent
) : ConceptTool<String>(
    name = "askQuestion",
    description = "Ask the user a question and await their response",
    parameters = listOf(
        ToolParameter(
            name = "question",
            description = "The question to ask the user",
            type = "string"
        ),
    ),
    //continuationMethod = ToolContinuationMethod.WAIT_FOR_USER
) {
    override suspend fun execute(params: ParametersWrapper): ToolResult<String> {
        return try {

            val question = params.question ?: return ToolResult.Error("No question provided")
            agent.askAndListen(question)
            ToolResult.Success("Question asked successfully")

        } catch (e: Exception) {
            ToolResult.Error("Failed to ask question: ${e.message}")
        }
    }
}

class SaveConceptItemTool(
    private val agent: ConversationAgent,
    private val conceptViewModel: ConceptViewModel,
    private val conceptRepository: ConceptRepository
) : ConceptTool<List<ConceptItem>>(
    name = "saveConceptItem",
    description = """Save one or more concept items.  se this tool to save or update concept items based on user input.
        
    Complete usage example:
    {
        "toolName": "setConceptItem",
        "parameters": {
            "saveRequests": [
                {
                    "conceptType": "PRESENT",
                    "slotIndex": 0,
                    "content": "Currently feeling unmotivated"
                },
                {
                    "conceptType": "PRESENT",
                    "slotIndex": 1,
                    "content": "Often procrastinate on important tasks"
                }
            ]
            "responseText": "That's 2 of 3 present concepts saved.  Tell me another and I'll save that too."
        },
        "action": "SAVE_CONCEPTS"
    }
    
    Parameters:
    - saveRequests: Array of objects, each with:
      - conceptType: Must be either "PRESENT" or "DESIRED"
      - slotIndex: Position in the concept list (0-based)
      - content: The actual text content for the concept
    Note: You can save multiple items in a single request to maintain context coherence.""${'"'},
          """,
    parameters = listOf(
        ToolParameter(
            name = "saveRequests",
            description = """Array of objects, each with:
                {
                    "conceptType": string ("PRESENT" or "DESIRED"),
                    "slotIndex": number,
                    "content": string
                }""",
            type = "array",
            required = true
        ),
        ToolParameter(
            name = "responseText",
            description = "Text to speak to user after saving.  This could be a simple statement to say that the current process has completed, " +
            "the tasks have been saved, etc., or it could be a continuation of the question/answer procedure to gain clarification to what has been entered already. " +
            "Or it could be a comment about what's going to be done next, e.g. move onto another concept.",
            type = "string",
            required = true
        )
    ),
    //continuationMethod = ToolContinuationMethod.CONTINUE_IMMEDIATE
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** the tool receives parameters from the LLM, as the LLM effectively gives the command to execute the tool
     *  params contains the parameters defined by the LLM, in this case, it contains data on what
     *  to save within which concept, and where (slot-wise)
     */
    override suspend fun execute(params: ParametersWrapper): ToolResult<List<ConceptItem>> {
        // Basic validation
        val manifestationId = conceptViewModel.manifestationId
            ?: return ToolResult.Error("No manifestation ID")

        // the parameters wrapper automatically parses the data sent from the LLM, so this variable
        // structures itself without us explicitly parsing the json
        val requests = params.saveRequests
            ?: return ToolResult.Error("No save requests")

        if (requests.isEmpty()) {
            return ToolResult.Error("No valid save requests found in input")
        }

        // Process each request
        val savedItems = mutableListOf<ConceptItem>()

        // First group the requests by concept type
        val requestsByType = requests.groupBy { it.conceptType.uppercase() }

        // Now process each group
        requestsByType.forEach { (typeStr, requestsForType) ->
            // Convert string to concept type once
            val conceptType = when (typeStr) {
                "PRESENT" -> ConceptType.Present
                "DESIRED" -> ConceptType.Desired
                else -> return ToolResult.Error("Invalid type: $typeStr")
            }

            // Get the concept once for this group
            val concept =
                conceptRepository.getConceptWithItems(manifestationId, conceptType).first()
                    ?: return ToolResult.Error("No concept found for $typeStr")

            // Create all items for this concept
            val itemsForConcept = requestsForType.map { request ->
                ConceptItem(
                    id = 0,
                    conceptId = concept.id,
                    content = request.content,
                    position = request.slotIndex,
                    metadata = emptyMap()
                )
            }
            // Save all items for this concept in one go
            conceptRepository.updateConceptItems(concept.id, itemsForConcept)
            savedItems.addAll(itemsForConcept)
        }
        if (params.responseText != null) agent.askAndListen(params.responseText)

        return ToolResult.Success(savedItems)
    }
}

class NavigateBackTool(
    private val agent: ConversationAgent
) : ConceptTool<String>(
    name = "navigateBack",
    description = "Exit the concept screen and return to the main screen. Use this when the user asks to leave or when concept building is complete.",
    parameters = listOf(),
) {
    override suspend fun execute(params: ParametersWrapper): ToolResult<String> {
        return try {
            // todo: Bring back this once concept screen has been re-integrated
            // commenting for now to avoid errors
            // agent.closeConceptScreen()
            ToolResult.Success("Navigation executed successfully")
        } catch (e: Exception) {
            ToolResult.Error("Failed to navigate: ${e.message}")
        }
    }
}

/** The concept tool library holds all the concept tools
 */

class ConceptToolLibrary(
    private val agent: ConversationAgent,
    private val conceptViewModel: ConceptViewModel,
    private val conceptRepository: ConceptRepository
) {
    private val tools = mutableMapOf<String, ConceptTool<*>>()

    init {
        // populate the tools variable with the available tools
        registerTools()
    }

    fun getTool(name: String): ConceptTool<*>? = tools[name]

    private fun registerTools() {
        listOf(
            QuestionTool(agent),
            SaveConceptItemTool(agent, conceptViewModel, conceptRepository),
            NavigateBackTool(agent),
        ).forEach { tool ->
            tools[tool.name] = tool
        }
    }

    fun getToolDescriptions(): String = buildString {
        appendLine("Available tools:")
        tools.values.forEach { tool ->
            append("""
                \nTool: ${tool.name}
                Description: ${tool.description}
                Parameters:\n
            """.trimIndent())
            tool.parameters.forEach { param ->
                appendLine("   - ${param.name} (${param.type}${if (!param.required) ", optional" else '"'}")
                appendLine("     ${param.description}")
            }
        }
    }

    suspend fun executeTool(decision: BrainDecision): ToolResult<*> {
        return tools[decision.toolName]?.execute(decision.parameters)
            ?: ToolResult.Error<Nothing>("Unknown tool: ${decision.toolName}")
    }
}
