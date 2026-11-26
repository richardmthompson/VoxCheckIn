package com.example.voxmanifestorapp.ui.agent.utilities

import ManifestationRepository
import android.util.Log
import com.example.voxmanifestorapp.data.ConceptRepository
import com.example.voxmanifestorapp.data.ConceptType
import com.example.voxmanifestorapp.data.MAX_WISH_SLOTS
import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.data.StatusColor
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * Summary of a user's wish, intended for providing context to the agent
 */
@Serializable
data class WishSummary(
    val id: Int,
    val title: String
)

/**
 * Enhanced wish summary including present and desired state items for better theme correlation
 */
@Serializable
data class EnhancedWishSummary(
    val id: Int,
    val title: String,
    val presentStateItems: List<String> = emptyList(),  // Content of present state items
    val desiredStateItems: List<String> = emptyList()   // Content of desired state items
)



/**
 * Fetches the user's active wishes to provide context for the check-in dialogue.
 * Limits to a maximum of 5 wishes for prompt efficiency.
 *
 * @param repository ManifestationRepository for accessing wish data
 * @param logger Optional logging function for status messages
 * @return List of WishSummary objects representing the user's active wishes
 */
suspend fun fetchUserWishesForContext(
    repository: ManifestationRepository,
    logger: ((String, StatusColor) -> Unit)? = null
): List<WishSummary> {
    logger?.invoke("📋 WISHES: Fetching user wishes for context", StatusColor.Default)

    return try {
        val manifestations = repository.getAllManifestations().first()

        if (manifestations.isEmpty()) {
            logger?.invoke("📋 WISHES: No wishes found", StatusColor.Default)
            emptyList()
        } else {
            val wishSummaries = manifestations
                .sortedBy { it.slot }
                .take(5)
                .map { manifestation ->
                    WishSummary(
                        id = manifestation.id,
                        title = manifestation.title
                    )
                }
            logger?.invoke(
                "📋 WISHES: Found ${wishSummaries.size} wishes for context",
                StatusColor.Default
            )
            wishSummaries
        }
    } catch (e: Exception) {
        logger?.invoke("📋 WISHES: Error fetching wishes: ${e.message}", StatusColor.Stop)
        emptyList()
    }
}

/**
 * Fetches enhanced wish data including present and desired state items for transition processing.
 * This provides richer context for theme-to-wish correlation in the TransitionChain.
 *
 * @param manifestationRepository Repository for accessing manifestation data
 * @param conceptRepository Repository for accessing concept data
 * @param logger Optional logging function for status messages
 * @return List of EnhancedWishSummary objects with concept items included
 */
suspend fun fetchEnhancedWishDataForTransition(
    manifestationRepository: ManifestationRepository,
    conceptRepository: ConceptRepository,
    logger: ((String, StatusColor) -> Unit)? = null
): List<EnhancedWishSummary> {
    logger?.invoke("📋 ENHANCED WISHES: Fetching enhanced wish data for transition", StatusColor.Default)

    return try {
        val manifestations = manifestationRepository.getAllManifestations().first()

        if (manifestations.isEmpty()) {
            logger?.invoke("📋 ENHANCED WISHES: No wishes found", StatusColor.Default)
            emptyList()
        } else {
            val enhancedWishes = manifestations
                .sortedBy { it.slot }
                .take(5) // Limit for prompt efficiency
                .map { manifestation ->
                    // Fetch present state items
                    val presentStateItems = try {
                        val presentConcept = conceptRepository.getConceptWithItems(
                            manifestation.id,
                            ConceptType.Present
                        ).first()
                        presentConcept?.items?.map { it.content } ?: emptyList()
                    } catch (e: Exception) {
                        logger?.invoke("📋 ENHANCED WISHES: Error fetching present state for wish ${manifestation.id}: ${e.message}", StatusColor.Default)
                        emptyList()
                    }

                    // Fetch desired state items
                    val desiredStateItems = try {
                        val desiredConcept = conceptRepository.getConceptWithItems(
                            manifestation.id,
                            ConceptType.Desired
                        ).first()
                        desiredConcept?.items?.map { it.content } ?: emptyList()
                    } catch (e: Exception) {
                        logger?.invoke("📋 ENHANCED WISHES: Error fetching desired state for wish ${manifestation.id}: ${e.message}", StatusColor.Default)
                        emptyList()
                    }

                    EnhancedWishSummary(
                        id = manifestation.id,
                        title = manifestation.title,
                        presentStateItems = presentStateItems,
                        desiredStateItems = desiredStateItems
                    )
                }

            logger?.invoke(
                "📋 ENHANCED WISHES: Found ${enhancedWishes.size} enhanced wishes for transition",
                StatusColor.Default
            )
            enhancedWishes
        }
    } catch (e: Exception) {
        logger?.invoke("📋 ENHANCED WISHES: Error fetching enhanced wishes: ${e.message}", StatusColor.Stop)
        emptyList()
    }
}


/**
 * Represents the status of a wish for prioritization purposes.
 * This is a transient computational result used to determine the next focus area.
 */
data class WishStatus(
    val slot: Int,
    val manifestationId: Int?,  // Store ID instead of entity
    val isDefinedOnMainScreen: Boolean,
    val hasCompletePresentState: Boolean,
    val hasCompleteDesiredState: Boolean,
    val lastDiscussedTimestamp: Long?
) {
    /**
     * Returns true if the wish has both Present and Desired states completed
     */
    val isConceptDefinitionComplete: Boolean
        get() = hasCompletePresentState && hasCompleteDesiredState
}

/**
 * Contains logic for selecting the next wish to focus on in the Core Conversation Loop.
 * The prioritization logic follows this order:
 * 1. Find the first slot without a defined Manifestation
 * 2. If all defined, find the first Manifestation with incomplete concepts
 * 3. If all complete, find the Manifestation least recently discussed
 */
class WishPriorityManager {
    
    companion object {
        private const val TAG = "WishPriority"
        private var lastSelectionReason = ""
        
        /**
         * Returns the reason why the last wish was selected
         */
        fun getLastSelectionReason(): String {
            return lastSelectionReason
        }
        
        /**
         * Selects the next wish to focus on based on prioritization rules
         * 
         * @param manifestationRepository Repository for accessing Manifestation data
         * @param conceptRepository Repository for accessing Concept data
         * @return The slot index of the next wish to focus on (0-4)
         */
        suspend fun selectNextWish(
            manifestationRepository: ManifestationRepository,
            conceptRepository: ConceptRepository
        ): Int {
            Log.d(TAG, "Selecting next wish...")
            
            // Get all manifestations
            val manifestations = manifestationRepository.getAllManifestations().first()
            
            // Build status for all slots
            val wishStatusList = buildWishStatusList(manifestations, conceptRepository)
            
            // Debug log the status of each wish
            wishStatusList.forEach { status ->
                Log.d(TAG, "Slot ${status.slot}: " +
                        "defined=${status.isDefinedOnMainScreen}, " +
                        "presentComplete=${status.hasCompletePresentState}, " +
                        "desiredComplete=${status.hasCompleteDesiredState}, " +
                        "lastDiscussed=${status.lastDiscussedTimestamp}")
            }
            
            // PRIORITY 1: Find first empty slot
            val emptySlot = wishStatusList.find { !it.isDefinedOnMainScreen }
            if (emptySlot != null) {
                Log.d(TAG, "Selected empty slot ${emptySlot.slot}")
                lastSelectionReason = "empty_slot"
                return emptySlot.slot
            }
            
            // PRIORITY 2: Find first with incomplete concepts
            val incompleteConceptsWish = wishStatusList.find { !it.isConceptDefinitionComplete }
            if (incompleteConceptsWish != null) {
                if (!incompleteConceptsWish.hasCompletePresentState) {
                    lastSelectionReason = "missing_present_state"
                } else {
                    lastSelectionReason = "missing_desired_state"
                }
                Log.d(TAG, "Selected slot ${incompleteConceptsWish.slot} with incomplete concepts")
                return incompleteConceptsWish.slot
            }
            
            // PRIORITY 3: Find least recently discussed (oldest timestamp or null)
            val oldestDiscussed = wishStatusList.minByOrNull { it.lastDiscussedTimestamp ?: 0L }
            if (oldestDiscussed != null) {
                Log.d(TAG, "Selected least recently discussed slot ${oldestDiscussed.slot}")
                lastSelectionReason = "least_recently_updated"
                return oldestDiscussed.slot
            }
            
            // Fallback - should never reach here as there's always at least 1 slot
            Log.d(TAG, "Fallback to slot 0")
            lastSelectionReason = ""
            return 0
        }
        
        /**
         * Builds a list of WishStatus objects for all slots
         */
        private suspend fun buildWishStatusList(
            manifestations: List<Manifestation>,
            conceptRepository: ConceptRepository
        ): List<WishStatus> {
            val result = mutableListOf<WishStatus>()
            
            // Check each slot
            for (slot in 0 until MAX_WISH_SLOTS) {
                val manifestation = manifestations.find { it.slot == slot }
                
                // If this slot has a manifestation, check its concept status
                val hasCompletePresentState = if (manifestation != null) {
                    val presentConcept = conceptRepository.getConceptWithItems(
                        manifestation.id, 
                        ConceptType.Present
                    ).first()
                    
                    // Check if it has the required number of items
                    presentConcept?.items?.size ?: 0 >= (ConceptType.Present.requiredItems ?: 0)
                } else false
                
                val hasCompleteDesiredState = if (manifestation != null) {
                    val desiredConcept = conceptRepository.getConceptWithItems(
                        manifestation.id,
                        ConceptType.Desired
                    ).first()
                    
                    // Check if it has the required number of items
                    desiredConcept?.items?.size ?: 0 >= (ConceptType.Desired.requiredItems ?: 0)
                } else false
                
                result.add(
                    WishStatus(
                        slot = slot,
                        manifestationId = manifestation?.id,
                        isDefinedOnMainScreen = manifestation != null,
                        hasCompletePresentState = hasCompletePresentState,
                        hasCompleteDesiredState = hasCompleteDesiredState,
                        lastDiscussedTimestamp = manifestation?.lastDiscussedTimestamp
                    )
                )
            }
            
            return result
        }
    }
}

/**
 * Centralized wish management utilities for both CommandMode and WishCreationManager.
 * Provides a single source of truth for all wish-related database operations.
 */
class WishUtilities(private val repository: ManifestationRepository) {
    
    companion object {
        private const val TAG = "WishUtilities"
    }
    
    /**
     * Gets all manifestations (wishes) from the database.
     * @return List of all Manifestation objects
     */
    suspend fun getAllManifestations(): List<Manifestation> {
        return try {
            repository.getAllManifestations().first()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all manifestations: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Saves a wish to a specific slot in the database.
     * @param wishText The wish text to save
     * @param slot The slot index (0-4)
     * @return True if successful, false otherwise
     */
    suspend fun saveWishToSlot(wishText: String, slot: Int): Boolean {
        return try {
            // Validate slot
            if (!isValidWishSlot(slot)) {
                Log.e(TAG, "Invalid slot: $slot")
                return false
            }
            
            // Get current manifestations to check for existing wish in slot
            val manifestations = getAllManifestations()
            val existingWish = manifestations.find { it.slot == slot }
            
            // Delete existing wish in slot if present
            if (existingWish != null) {
                repository.deleteById(existingWish.id)
                Log.d(TAG, "Deleted existing wish in slot $slot")
            }
            
            // Create new manifestation
            val manifestation = Manifestation(
                id = 0, // Room will auto-generate ID
                title = wishText,
                slot = slot,
                timeframe = "", // Empty timeframe for new wish
                completedBy = null, // Not completed yet
                notes = "", // Empty notes for new wish
                lastDiscussedTimestamp = System.currentTimeMillis()
            )
            
            repository.insertManifestation(manifestation)
            Log.d(TAG, "Successfully saved wish to slot $slot: $wishText")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving wish to slot $slot: ${e.message}")
            false
        }
    }
    
    /**
     * Deletes a wish from a specific slot.
     * @param slot The slot index to delete from
     * @return True if successful, false otherwise
     */
    suspend fun deleteWishFromSlot(slot: Int): Boolean {
        return try {
            if (!isValidWishSlot(slot)) {
                Log.e(TAG, "Invalid slot: $slot")
                return false
            }
            
            val manifestations = getAllManifestations()
            val wishToDelete = manifestations.find { it.slot == slot }
            
            if (wishToDelete != null) {
                repository.deleteById(wishToDelete.id)
                Log.d(TAG, "Successfully deleted wish from slot $slot")
                true
            } else {
                Log.d(TAG, "No wish found in slot $slot to delete")
                true // Consider this a success since the goal is achieved
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting wish from slot $slot: ${e.message}")
            false
        }
    }
    
    /**
     * Gets a wish by slot index.
     * @param slot The slot index to retrieve
     * @return Manifestation object if found, null otherwise
     */
    suspend fun getWishBySlot(slot: Int): Manifestation? {
        return try {
            if (!isValidWishSlot(slot)) {
                Log.e(TAG, "Invalid slot: $slot")
                return null
            }
            
            val manifestations = getAllManifestations()
            manifestations.find { it.slot == slot }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting wish from slot $slot: ${e.message}")
            null
        }
    }
    
    /**
     * Finds the next empty wish slot.
     * @return Index of the first empty slot (0-4), or -1 if all slots are filled
     */
    suspend fun findNextEmptyWishSlot(): Int {
        return try {
            val manifestations = getAllManifestations()
            findNextEmptyWishSlot(manifestations)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding empty wish slot: ${e.message}")
            -1
        }
    }
    
    /**
     * Checks if a slot is valid.
     * @param slot The slot index to check
     * @return True if valid, false otherwise
     */
    fun isValidWishSlot(slot: Int): Boolean = slot in 0 until MAX_WISH_SLOTS
}

/**
 * Returns the index of the first empty wish slot, or -1 if all slots are filled.
 * @param manifestations List of Manifestation objects representing current wishes
 * @return Index of the first empty slot (0-based), or -1 if all slots are filled
 */
fun findNextEmptyWishSlot(manifestations: List<Manifestation>): Int {
    val slotMap = Array<Manifestation?>(MAX_WISH_SLOTS) { null }
    manifestations.forEach { wish ->
        slotMap[wish.slot] = wish
    }
    return slotMap.indexOfFirst { it == null }
}

/**
 * Checks if a given slot index is valid (within 0..MAX_WISH_SLOTS-1).
 * @param slot The slot index to check
 * @return True if the slot is valid, false otherwise
 */
fun isValidWishSlot(slot: Int): Boolean = slot in 0 until MAX_WISH_SLOTS

/**
 * Converts a number to its ordinal string representation (e.g., 1 -> "first").
 * @param n The number to convert
 * @return Ordinal string ("first", "second", etc.)
 */
fun getOrdinal(n: Int): String = when (n) {
    1 -> "first"
    2 -> "second"
    3 -> "third"
    4 -> "fourth"
    5 -> "fifth"
    else -> "${n}th"
}

/**
 * Extracts a number from text, trying digits first then word numbers.
 * @param text The input text
 * @return The extracted number, or null if not found
 */
fun extractNumber(text: String): Int? {
    val digitNumber = extractIntNumber(text)
    if (digitNumber != null) {
        return digitNumber
    }
    return parseNumberWord(text)
}

/**
 * Extracts the first integer number from text using regex.
 * @param text The input text
 * @return The extracted integer, or null if not found
 */
fun extractIntNumber(text: String): Int? {
    val regex = "\\d+".toRegex()
    return regex.find(text)?.value?.toIntOrNull()
}

/**
 * Parses word numbers ("one", "two", etc.) to integers.
 * @param text The input text
 * @return The parsed integer, or null if not found
 */
fun parseNumberWord(text: String): Int? {
    val numberWords = mapOf(
        "one" to 1, "first" to 1,
        "two" to 2, "second" to 2, "to" to 2,
        "three" to 3, "third" to 3,
        "four" to 4, "fourth" to 4, "for" to 4,
        "five" to 5, "fifth" to 5
    )
    return numberWords.entries.firstOrNull { (word, _) ->
        text.lowercase().contains(word)
    }?.value
}
