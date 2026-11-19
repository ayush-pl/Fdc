package com.fdc.nutrition.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.fdc.nutrition.data.db.entity.SyncConflictEntity
import com.fdc.nutrition.data.db.entity.SyncMetadataEntity
import com.fdc.nutrition.data.db.entity.SyncQueueEntity
import com.fdc.nutrition.data.db.entity.SyncRecordVersionEntity
import com.fdc.nutrition.data.db.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_queue WHERE entity_type = :type AND entity_id = :id") suspend fun queued(type: String, id: String): SyncQueueEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(item: SyncQueueEntity)
    @Query("SELECT * FROM sync_queue WHERE status != 'CONFLICT' ORDER BY id LIMIT :limit") suspend fun pending(limit: Int): List<SyncQueueEntity>
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status") fun observeCount(status: SyncStatus): Flow<Int>
    @Query("SELECT COUNT(*) FROM sync_queue") fun observeQueueSize(): Flow<Int>
    @Query("DELETE FROM sync_queue WHERE id = :id") suspend fun remove(id: Long)
    @Query("UPDATE sync_queue SET attempts = attempts + 1, last_error = :error, status = :status WHERE id = :id") suspend fun markFailed(id: Long, error: String?, status: SyncStatus)
    @Query("UPDATE sync_queue SET status = 'PENDING', attempts = 0 WHERE status = 'FAILED'") suspend fun resetFailed()
    @Query("DELETE FROM sync_queue") suspend fun clearQueue()

    @Upsert suspend fun putVersion(item: SyncRecordVersionEntity)
    @Query("SELECT remote_version FROM sync_record_versions WHERE entity_type = :type AND entity_id = :id") suspend fun version(type: String, id: String): Long?
    @Query("DELETE FROM sync_record_versions") suspend fun clearVersions()

    @Insert suspend fun addConflict(item: SyncConflictEntity)
    @Query("SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY detected_at DESC") fun observeConflicts(): Flow<List<SyncConflictEntity>>
    @Query("SELECT * FROM sync_conflicts WHERE id = :id") suspend fun conflict(id: Long): SyncConflictEntity?
    @Query("UPDATE sync_conflicts SET resolved = 1 WHERE id = :id") suspend fun resolveConflict(id: Long)

    @Upsert suspend fun putMeta(item: SyncMetadataEntity)
    @Query("SELECT value FROM sync_metadata WHERE `key` = :key") suspend fun meta(key: String): String?
    @Query("SELECT value FROM sync_metadata WHERE `key` = :key") fun observeMeta(key: String): Flow<String?>
    @Query("DELETE FROM sync_metadata WHERE `key` = :key") suspend fun deleteMeta(key: String)
}
