import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.data.ManifestationDao
import kotlinx.coroutines.flow.Flow

class ManifestationRepository(private val manifestationDao: ManifestationDao) {

    fun getAllManifestations(): Flow<List<Manifestation>> {
        return manifestationDao.getAllManifestations()
    }

    fun getManifestation(id: Int): Flow<Manifestation> {
        return manifestationDao.getManifestation(id)
    }

    suspend fun getManifestationById(id: Int): Manifestation? {
        return manifestationDao.getManifestationById(id)
    }

    suspend fun getManifestationBySlot(slot: Int): Manifestation? {
        return manifestationDao.getManifestationBySlot(slot)
    }

    suspend fun insertManifestation(manifestation: Manifestation) {
        return manifestationDao.insertManifestation(manifestation)
    }

    suspend fun updateManifestation(manifestation: Manifestation) {
        manifestationDao.updateManifestation(manifestation)
    }

    suspend fun deleteById(id: Int) {
        manifestationDao.deleteById(id)
    }

    suspend fun deleteManifestation(manifestation: Manifestation) {
        manifestationDao.deleteManifestation(manifestation)
    }

    /**
     * Updates the lastDiscussedTimestamp for a specific manifestation
     */
    suspend fun updateLastDiscussedTimestamp(manifestationId: Int, timestamp: Long = System.currentTimeMillis()) {
        manifestationDao.updateLastDiscussedTimestamp(manifestationId, timestamp)
    }

    /*
fun getManifestationWithDetails(id: Int): Flow<ManifestationWithDetails> {
    return manifestationDao.getManifestationWithDetails(id)
}
 */


}
