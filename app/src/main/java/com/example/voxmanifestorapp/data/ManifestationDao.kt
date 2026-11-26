package com.example.voxmanifestorapp.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ManifestationDao {

    /* basic functions for dealing with manifestation entities */

    @Query("SELECT * FROM manifestations")
    fun getAllManifestations(): Flow<List<Manifestation>>

    @Query("SELECT * FROM manifestations WHERE id =:id")
    fun getManifestation(id: Int): Flow<Manifestation>

    @Query("SELECT * FROM manifestations WHERE id =:id")
    suspend fun getManifestationById(id: Int) : Manifestation?

    @Query("SELECT * FROM manifestations WHERE slot =:slot")
    suspend fun getManifestationBySlot(slot: Int): Manifestation?

    @Insert
    suspend fun insertManifestation(manifestation: Manifestation)

    @Update
    suspend fun updateManifestation(manifestation: Manifestation)

    @Query("DELETE FROM manifestations WHERE id =:id")
    suspend fun deleteById(id: Int)

    @Delete
    suspend fun deleteManifestation(manifestation: Manifestation)

    /**
     * Updates the lastDiscussedTimestamp for a specific manifestation
     */
    @Query("UPDATE manifestations SET lastDiscussedTimestamp = :timestamp WHERE id = :manifestationId")
    suspend fun updateLastDiscussedTimestamp(manifestationId: Int, timestamp: Long)

    /*
@Transaction
@Query("SELECT * FROM Manifestation WHERE id = :id")
fun getManifestationWithDetails(id: Int): Flow<ManifestationWithDetails>
 */

}
