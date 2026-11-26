package com.example.voxmanifestorapp.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.voxmanifestorapp.ui.agent.ConversationEntry

// Concept composites: Knowledge structures based around lists of descriptive text items
data class Concept(
    val id: Int = 0,        // room will assign the actual Id
    val name: String,
    val type: ConceptType,
    val items: List<ConceptItem>,
    val properties: Map<String, String> = emptyMap()
)

data class ConceptItem(
    val id: Int,
    val conceptId: Int,
    val content: String,
    val position: Int,
    val metadata: Map<String, String> = emptyMap()
)

@Entity(tableName = "concepts")
data class ConceptEntity(
    @PrimaryKey (autoGenerate = true) val id: Int,
    val name: String,
    val type: String,
    val manifestationId: Int,
    val properties: String // JSON serialized properties
)

@Entity(
    tableName = "concept_items",
    foreignKeys = [
        ForeignKey(
            entity = ConceptEntity::class,
            parentColumns = ["id"],
            childColumns = ["conceptId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ConceptItemEntity(
    @PrimaryKey (autoGenerate = true) val id: Int,
    val conceptId: Int,
    val content: String,
    val position: Int,
    val metadata: String // JSON serialized metadata
)

// We'll need this data class for the Transaction query
data class ConceptWithItems(
    @Embedded val concept: ConceptEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "conceptId"
    )
    val items: List<ConceptItemEntity>
)

// a structure that helps us provide all the necessary information directly to the Brain so it can perceive current status
data class ConceptBuildingContext (
    val manifestationTitle: String,
    val conceptData: Map<ConceptType, List<ConceptItem>>,      // asking for a single type because this should be the parent type
    val includeMetaData: Boolean,
)

enum class ConceptActionState(val description: String) {
    INITIATE(
        """Start the concept building process by explaining what we'll do and setting context.
                Use this when beginning work on a new concept type (Present or Desired state).
                """
    ),

    QUESTION_RESPONSE(
        """Ask a specific question to gather information about their state.
                Use this to guide the user in describing their situation, using the concept type's guidance prompts.
                The question will be provided in responseText.
                """
    ),

    ANALYZE_CONFIRM(
        """
            1. Check that each item represents a unique idea, with no overlap to others.  If there is overlap, explain this to
                the user and ask if they want to change anything.
            2. Compare each of the present state items with their corresponding desired state item.  I.e. 1->1, 2->2, 3->3.
            If they don't seem related, explain this to the user and ask if they want to change anything.
            3. The user can correct and improve what was said at this point.
               Iterate until user has confirmed correct.
               """
    ),

    SAVE_CONCEPTS(
        "Store the confirmed concepts and decide next steps. " +
                "Use this after concepts have been verified. " +
                "This may lead to requesting more items or completing the process."
    ),

    COMPLETE(
        "The concept building process is complete for now.  You can wait for further user requests. "
    );

    companion object {
        fun getActionDescriptions(): String = buildString {
            values().forEach { action ->
                appendLine("${action.name}:")
                appendLine("  ${action.description}")
                appendLine()
            }
        }
    }
}

sealed class ConceptType (
    val description: String,
    val purpose: String,
    val guidance: String,
    val parentType: ConceptType? = null,    // parent concepts draw relationships between child concepts.
    val requiredItems: Int? = null      // null means it is a parent / linking concept
) {
    /* present / desired state stack
     * description - provides a general explanation of the concept
     * purpose - explains the function of the concept in the context of the app
     * guidance - what types of things should the Brain be asking the user to obtain the information?
     */


    object StateTransformation : ConceptType(
        description = """Represents the relationship between the current and the desired state of affairs.
                The contrast between the two states allow the user to obtain additional information about how to accomplish their goal
                Some people will find it easier to think about what they don't want than what they do want, or vice versa.
                """,
        purpose = "Helps the user draw a contrast between how things are and how they want things to be.",
        guidance = "Imagine stepping into each of the states in turn.  What are the x most important elements of the experience?",
        requiredItems = null
    )

    object Present : ConceptType(
        description = "A description of the present state of affairs as relating to the wish.",
        purpose = "Describes the most important elements of what the user wants to change.",
        guidance = "What are the main things about your current situation you want to change? " +
                    "What don't you like about the present situation that this wish could change?",
        parentType = ConceptType.StateTransformation,
        requiredItems = 3
    )

    object Desired : ConceptType(
        description = "A description of the desired stateof affairs once the wish is manifested.",
        purpose = "Describes how the user would know that the wish has been manifested in their life.",
        guidance = "What evidence would prove that this wish had manifested in your life? " +
                    "How would you know that this wish had manifested for you?" +
                    "Your wish is <users wish> - can you describe more about what achieving this looks like? ",
        parentType = ConceptType.StateTransformation,
        requiredItems = 3
    )

    // this ensures we are searching the database for the string value
    override fun toString(): String = when(this) {
        is Present -> "Present"
        is Desired -> "Desired"
        is StateTransformation -> "StateTransformation"
    }

}
