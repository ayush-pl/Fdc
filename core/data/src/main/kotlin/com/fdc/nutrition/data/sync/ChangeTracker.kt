package com.fdc.nutrition.data.sync

import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.entity.SyncOperation
import com.fdc.nutrition.data.db.entity.SyncQueueEntity
import com.fdc.nutrition.data.db.entity.SyncStatus
import com.fdc.nutrition.data.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Entity types that participate in cloud sync and backups. */
object EntityType {
    const val FOOD = "food"                    // custom / imported foods only
    const val FAVOURITE = "food_favourite"
    const val DIARY_ENTRY = "diary_entry"
    const val MEAL = "meal"
    const val MEAL_NOTE = "meal_note"
    const val DAY_LOG = "day_log"
    const val SAVED_MEAL = "saved_meal"
    const val RECIPE = "recipe"
    const val PLANNED_MEAL = "planned_meal"
    const val RECURRENCE = "plan_recurrence"
    const val MEAL_PLAN = "meal_plan"
    const val TARGET = "nutrition_target"
    const val WATER = "water_entry"
    const val BODY = "body_measurement"
    const val PROFILE = "profile"
    /** Dependency order: referenced records come before the records that reference them. */
    val ALL = listOf(FOOD, MEAL, RECIPE, FAVOURITE, DIARY_ENTRY, MEAL_NOTE, DAY_LOG, SAVED_MEAL, PLANNED_MEAL, RECURRENCE, MEAL_PLAN, TARGET, WATER, BODY, PROFILE)
}

/**
 * Records local changes into the sync outbox. Call inside the same transaction as the change.
 * Nothing is recorded while sync is disabled; enabling sync enqueues a full snapshot instead.
 * Repeated changes to the same record coalesce into one queue row (idempotent upload).
 */
@Singleton
class ChangeTracker @Inject constructor(private val db: AppDatabase, private val time: TimeProvider) {
    @Volatile var enabled: Boolean = false

    suspend fun upserted(type: String, id: String) = record(type, id, SyncOperation.UPSERT)
    suspend fun deleted(type: String, id: String) = record(type, id, SyncOperation.DELETE)

    suspend fun record(type: String, id: String, op: SyncOperation, force: Boolean = false) {
        if (!enabled && !force) return
        val dao = db.syncDao()
        val base = dao.version(type, id)
        dao.enqueue(SyncQueueEntity(entityType = type, entityId = id, operation = op, baseRemoteVersion = base, createdAt = time.now(), attempts = 0, lastError = null, status = SyncStatus.PENDING))
    }
}
