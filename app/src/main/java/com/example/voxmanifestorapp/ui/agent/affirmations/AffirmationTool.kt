package com.example.voxmanifestorapp.ui.agent.affirmations

import android.util.Log
import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.data.StatusColor
import com.example.voxmanifestorapp.ui.agent.BrainService
import com.example.voxmanifestorapp.ui.agent.ConversationAction
import com.example.voxmanifestorapp.ui.agent.ConversationSequence
import com.example.voxmanifestorapp.ui.agent.ConversationTool
import com.example.voxmanifestorapp.ui.agent.DisplayState
import kotlinx.serialization.Serializable

@Serializable
data class AffirmationSet(
    val wish: String = "",
    val affirmations: List<String> = listOf()
)
@Serializable
data class AffirmationResponse(
    val wishes: List<AffirmationSet> = listOf()
)

// this provides a standardised input to the affirmation tool, to ensure we fulfil requirements
data class AffirmationToolInput(
    val wishes: List<Manifestation>,
    val numAffirmations: Int
)

class AffirmationTool(
    private val brainService: BrainService,
    private val logger: (String, StatusColor) -> Unit

) : ConversationTool {

    private fun logStatus(message: String, color: StatusColor = StatusColor.Default) {
        logger(message,color)
    }

    override suspend fun execute(data: Any?): ConversationSequence {

        logStatus("[AffirmationTool.execute] Executing AffirmationTool's conversation sequence...")

        when (data) {
            is AffirmationToolInput -> {
                // generate our list of affirmations from the brain
                val affirmationsResult = brainService.generateAffirmations(
                    data.wishes.sortedBy { it.slot },
                    numAffirmations = data.numAffirmations
                )
                logStatus("[AffirmationTool.execute] ${data.numAffirmations} Affirmations requested from brain")

                return affirmationsResult.fold(
                    onSuccess = { affirmationSets ->
                        // zip the affirmations together with the source wishes, to present them as a pair to the build function
                        buildAffirmationSequence(data.wishes.zip(affirmationSets))
                    },
                    onFailure = {
                        ConversationSequence(
                            listOf(
                                ConversationAction("I apologize, but I encountered an error generating affirmations.")
                            )
                        )
                    }
                )  // return affirmationsresults
            }

            else -> return ConversationSequence(listOf(ConversationAction("I couldn't access the wishes to create affirmations")))
        }
    }

    private fun buildAffirmationSequence(
        wishAffirmations: List<Pair<Manifestation, AffirmationSet>>
    ): ConversationSequence {
        val actions = mutableListOf<ConversationAction>()

        actions.add(
            ConversationAction(
                "Let's begin your affirmation session.",
                pauseAfter = 2000,
                displayState = DisplayState.StartSession
            )
        )

        wishAffirmations.forEach { (wish, affirmationSet) ->
            affirmationSet.affirmations.forEach { affirmation ->
                // Initial reading
                actions.add(
                    ConversationAction(
                        affirmation,
                        pauseAfter = 1000,
                        displayState = DisplayState.ShowAffirmation(affirmation, false, 0)
                    )
                )

                /*
                // todo: one repetition per affirmation - can make number of repetitions variable
                repeat(1) { index ->
                    actions.add(
                        ConversationAction(
                            affirmation,
                            pauseAfter = 3000,
                            displayState = DisplayState.ShowAffirmation(
                                affirmation,
                                true,
                                index + 1
                            )
                        )
                    )
                }

                actions.add(ConversationAction("", pauseAfter = 1000))
                 */

            }
        }

        actions.add(
            ConversationAction(
                "That concludes our affirmation session.",
                displayState = DisplayState.EndSession
            )
        )

        Log.d(
            "brainservice",
            "built affirmation sequence, now returning as a ConversationSequence"
        )

        return ConversationSequence(actions)
    }
}

