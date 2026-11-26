package com.example.voxmanifestorapp.data

import android.util.Log
import kotlinx.coroutines.flow.map

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConceptRepository(private val conceptDao: ConceptDao) {

    // Read a concept with all its items
    fun getConceptWithItems(manifestationId: Int, type: ConceptType): Flow<Concept?> {
        val typeString = type.toString()
        Log.d("ConceptRepository", "Querying with type: $typeString")
        return conceptDao.getConceptWithItems(manifestationId, type.toString())
            .map { conceptWithItems ->
                conceptWithItems?.let {
                    conceptWithItems.concept.toConcept(conceptWithItems.items)
                }
            }
    }

    // Save a concept and all its items
    suspend fun saveConcept(manifestationId: Int, concept: Concept) {
        Log.d("ConceptRepository", "Attempting to save concept: ${concept.name} for manifestation $manifestationId")

        // Convert and save the concept entity
        val conceptEntity = concept.toConceptEntity(manifestationId)
        Log.d("ConceptRepository", "Created ConceptEntity: $conceptEntity")

        try {
            val conceptId = conceptDao.insertConcept(conceptEntity)
            Log.d("ConceptRepository", "Inserted concept. New ID: $conceptId")

            // Convert and save all items
            concept.items.forEachIndexed { index, item ->
                val itemEntity = item.toConceptItemEntity(conceptId.toInt(), index)
                conceptDao.insertConceptItem(itemEntity)
            }

        } catch (e: Exception) {
            Log.e("ConceptRepository", "Error inserting concept: ${e.message}", e)
            throw e
        }

    }

    /**
     * Saves or updates a concept item at a specific position.
     * If an item already exists at that position, it will be replaced.
     */
    suspend fun saveConceptItem(conceptId: Int, item: ConceptItem) {
        // First, check if there's an existing item at this position
        val existingItems = conceptDao.getItemsForConcept(conceptId.toString()).first()
        val existingItem = existingItems.find { it.position == item.position }

        if (existingItem != null) {
            // Delete the existing item
            conceptDao.deleteConceptItem(existingItem)
        }

        // Create new item entity
        val itemEntity = ConceptItemEntity(
            id = 0, // Room will assign new ID
            conceptId = conceptId,
            content = item.content,
            position = item.position,
            metadata = Json.encodeToString(item.metadata)
        )

        // Save the new item
        conceptDao.insertConceptItem(itemEntity)
    }

    /**
     * Updates multiple items for a concept, handling replacements at specific positions
     */
    suspend fun updateConceptItems(conceptId: Int, newItems: List<ConceptItem>) {
        // Get existing items
        val existingItems = conceptDao.getItemsForConcept(conceptId.toString()).first()

        // Create a map of position to existing items for quick lookup
        val existingItemsByPosition = existingItems.associateBy { it.position }

        // Process each new item
        newItems.forEach { newItem ->
            // If there's an existing item at this position, delete it
            existingItemsByPosition[newItem.position]?.let { existing ->
                conceptDao.deleteConceptItem(existing)
            }

            // Create and save new item
            val itemEntity = ConceptItemEntity(
                id = 0,
                conceptId = conceptId,
                content = newItem.content,
                position = newItem.position,
                metadata = Json.encodeToString(newItem.metadata)
            )

            conceptDao.insertConceptItem(itemEntity)
        }
    }
}

/**
 * Extension functions to convert between domain models and database entities
 */

// Convert from database entities to domain model
fun ConceptEntity.toConcept(items: List<ConceptItemEntity>): Concept {
    return Concept(
        id = this.id,
        name = this.name,
        type = when (this.type) {
            "Present" -> ConceptType.Present
            "Desired" -> ConceptType.Desired
            else -> throw IllegalArgumentException("Unknown concept type: ${this.type}")
        },
        items = items.sortedBy { it.position }.map { it.toConceptItem() },
        properties = Json.decodeFromString(this.properties)
    )
}

fun ConceptItemEntity.toConceptItem(): ConceptItem {
    return ConceptItem(
        id = this.id,
        conceptId = this.conceptId,
        content = this.content,
        position = this.position,
        metadata = Json.decodeFromString(this.metadata)
    )
}

// Convert from domain model to database entities
fun Concept.toConceptEntity(manifestationId: Int): ConceptEntity {
    return ConceptEntity(
        id = this.id,
        name = this.name,
        type = when (this.type) {
            is ConceptType.Present -> "Present"
            is ConceptType.Desired -> "Desired"
            else -> "State Transformation"
        },
        manifestationId = manifestationId,
        properties = Json.encodeToString(this.properties)
    )
}

fun ConceptItem.toConceptItemEntity(conceptId: Int, position: Int): ConceptItemEntity {
    return ConceptItemEntity(
        id = this.id,
        conceptId = conceptId,
        content = this.content,
        position = position,
        metadata = Json.encodeToString(this.metadata)
    )
}
