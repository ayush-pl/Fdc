package com.fdc.nutrition.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(@PrimaryKey val key: String, val value: String)

enum class SyncOperation { UPSERT, DELETE }
enum class SyncStatus { PENDING, FAILED, CONFLICT }

/**
 * Outbox of local changes awaiting upload. One row per entity (coalesced); the payload is read from
 * the live row at push time, except for deletes, which carry a tombstone.
 */
@Entity(tableName = "sync_queue", indices = [Index(value = ["entity_type", "entity_id"], unique = true), Index("status")])
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val operation: SyncOperation,
    @ColumnInfo(name = "base_remote_version") val baseRemoteVersion: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    val attempts: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
    val status: SyncStatus,
)

/** Remote version last seen for each synced record; used to detect concurrent edits. */
@Entity(tableName = "sync_record_versions", primaryKeys = ["entity_type", "entity_id"])
data class SyncRecordVersionEntity(
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long,
)

@Entity(tableName = "sync_conflicts", indices = [Index(value = ["entity_type", "entity_id"])])
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "local_payload") val localPayload: String?,
    @ColumnInfo(name = "remote_payload") val remotePayload: String?,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long,
    @ColumnInfo(name = "detected_at") val detectedAt: Instant,
    val resolved: Boolean,
)
