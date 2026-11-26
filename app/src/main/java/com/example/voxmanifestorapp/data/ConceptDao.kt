package com.example.voxmanifestorapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConceptDao {
    // Query operations
    @Transaction
    @Query("SELECT * FROM concepts WHERE manifestationId = :manifestationId AND type = :type")
    fun getConceptWithItems(manifestationId: Int, type: String): Flow<ConceptWithItems?>

    @Query("SELECT * FROM concepts WHERE manifestationId = :manifestationId")
    fun getConceptsForManifestation(manifestationId: Int): Flow<List<ConceptEntity>>

    @Query("SELECT * FROM concept_items WHERE conceptId = :conceptId ORDER BY position")
    fun getItemsForConcept(conceptId: String): Flow<List<ConceptItemEntity>>

    // Insert operations
    @Transaction
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConcept(concept: ConceptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConceptItem(item: ConceptItemEntity)

    // Update operations
    @Update
    suspend fun updateConcept(concept: ConceptEntity)

    @Update
    suspend fun updateConceptItem(item: ConceptItemEntity)

    // Delete operations
    @Delete
    suspend fun deleteConcept(concept: ConceptEntity)

    @Delete
    suspend fun deleteConceptItem(item: ConceptItemEntity)

    @Query("DELETE FROM concept_items WHERE conceptId = :conceptId")
    suspend fun deleteConceptItems(conceptId: String)

    // Utility queries
    @Query("SELECT COUNT(*) FROM concept_items WHERE conceptId = :conceptId")
    suspend fun getItemCount(conceptId: String): Int

}
