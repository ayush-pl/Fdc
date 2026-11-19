package com.fdc.nutrition.data.sync

import androidx.room.withTransaction
import com.fdc.nutrition.data.backup.RecordCodec
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.entity.*
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.data.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatusInfo(val signedInEmail: String?, val lastSyncAt: String?, val lastError: String?, val pending: Int, val conflicts: List<SyncConflictEntity>)

sealed interface SyncOutcome {
    data class Success(val pulled: Int, val pushed: Int, val conflicts: Int) : SyncOutcome
    data class Failed(val message: String, val retryable: Boolean) : SyncOutcome
    data object NotConfigured : SyncOutcome
}

/** Builds the remote for the user's configured Supabase project; replaceable in tests. */
open class SyncRemoteFactory @Inject constructor(private val client: OkHttpClient) {
    open fun create(url: String, anonKey: String): SyncRemote = SupabaseRemote(client, url, anonKey)
}

/**
 * Two-way sync between the local database and the cloud:
 * 1. pull remote changes since the last cursor; a remote change to a record that also has a pending
 *    local change is recorded as a **conflict** (both versions kept, nothing overwritten);
 * 2. push the outbox with optimistic concurrency (base version). The server refuses stale writes,
 *    which are also stored as conflicts for the user to resolve.
 * Local writes never wait for this; the app is fully functional offline.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val db: AppDatabase,
    private val codec: RecordCodec,
    private val profiles: ProfileRepository,
    private val factory: SyncRemoteFactory,
    private val changes: ChangeTracker,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()
    private val dao get() = db.syncDao()

    fun observeStatus(): Flow<SyncStatusInfo> = combine(
        dao.observeMeta(K_EMAIL), dao.observeMeta(K_LAST_SYNC), dao.observeMeta(K_LAST_ERROR), dao.observeQueueSize(), dao.observeConflicts(),
    ) { email, last, err, pending, conflicts -> SyncStatusInfo(email, last, err, pending, conflicts) }

    private suspend fun remote(): SyncRemote? {
        val p = profiles.preferences()
        val url = p.supabaseUrl?.takeIf { it.isNotBlank() } ?: return null
        val key = p.supabaseAnonKey?.takeIf { it.isNotBlank() } ?: return null
        return factory.create(url, key)
    }

    private suspend fun putMeta(k: String, v: String?) { if (v == null) dao.deleteMeta(k) else dao.putMeta(SyncMetadataEntity(k, v)) }

    private suspend fun store(s: Session) {
        putMeta(K_ACCESS, s.accessToken); putMeta(K_REFRESH, s.refreshToken); putMeta(K_EXPIRES, s.expiresAtEpochSec.toString())
        putMeta(K_USER, s.userId); putMeta(K_EMAIL, s.email)
    }

    private suspend fun session(r: SyncRemote): Session? {
        val access = dao.meta(K_ACCESS) ?: return null
        val refresh = dao.meta(K_REFRESH) ?: return null
        val exp = dao.meta(K_EXPIRES)?.toLongOrNull() ?: 0
        val s = Session(access, refresh, exp, dao.meta(K_USER) ?: "", dao.meta(K_EMAIL))
        return if (exp - 60 < time.now().epochSecond) r.refresh(refresh).also { store(it) } else s
    }

    suspend fun signIn(email: String, password: String, create: Boolean): String {
        val r = remote() ?: throw IllegalStateException("Enter your Supabase project URL and anon key first.")
        val previousUser = dao.meta(K_USER)
        val s = if (create) r.signUp(email.trim(), password) ?: return "Check your email to confirm the account, then sign in." else r.signIn(email.trim(), password)
        if (previousUser != null && previousUser != s.userId) {
            // Different account: forget cursors so we don't mix version bookkeeping between accounts.
            dao.clearVersions(); putMeta(K_CURSOR, null)
        }
        store(s)
        enableAndEnqueueAll()
        return "Signed in as ${s.email ?: email}"
    }

    /** Turns on change tracking and queues every existing record for the first upload (idempotent). */
    suspend fun enableAndEnqueueAll() {
        profiles.updatePreferences { it.copy(syncEnabled = true) }
        changes.enabled = true
        db.withTransaction { EntityType.ALL.forEach { type -> codec.allIds(type).forEach { id -> if (dao.queued(type, id) == null) changes.record(type, id, SyncOperation.UPSERT, force = true) } } }
    }

    suspend fun signOut() {
        listOf(K_ACCESS, K_REFRESH, K_EXPIRES, K_EMAIL).forEach { putMeta(it, null) }
        profiles.updatePreferences { it.copy(syncEnabled = false) }
        changes.enabled = false
    }

    suspend fun deleteAccount() {
        val r = remote() ?: throw IllegalStateException("Sync isn't configured")
        val s = session(r) ?: throw IllegalStateException("Sign in first")
        r.deleteAccount(s)
        signOut(); dao.clearQueue(); dao.clearVersions(); putMeta(K_CURSOR, null); putMeta(K_USER, null)
    }

    suspend fun retryFailed() = dao.resetFailed()

    suspend fun sync(): SyncOutcome = mutex.withLock {
        val r = remote() ?: return SyncOutcome.NotConfigured
        try {
            val s = session(r) ?: return SyncOutcome.Failed("Not signed in", false)
            val pulled = pull(r, s)
            val (pushed, conflicts) = push(r, s)
            putMeta(K_LAST_SYNC, time.now().toString()); putMeta(K_LAST_ERROR, null)
            SyncOutcome.Success(pulled, pushed, conflicts)
        } catch (e: AuthException) {
            putMeta(K_LAST_ERROR, "Sign-in required: ${e.message}"); SyncOutcome.Failed(e.message ?: "Auth error", false)
        } catch (e: RemoteException) {
            putMeta(K_LAST_ERROR, e.message); SyncOutcome.Failed(e.message ?: "Sync failed", e.retryable)
        }
    }

    private suspend fun pull(r: SyncRemote, s: Session): Int {
        var count = 0
        while (true) {
            val batch = r.pull(s, dao.meta(K_CURSOR), PAGE)
            if (batch.isEmpty()) break
            val ordered = batch.sortedWith(compareBy({ EntityType.ALL.indexOf(it.entityType).let { i -> if (i < 0) 99 else i } }, { it.updatedAt }))
            db.withTransaction {
                for (rec in ordered) {
                    val known = dao.version(rec.entityType, rec.entityId)
                    if (known != null && rec.version <= known) continue
                    val pending = dao.queued(rec.entityType, rec.entityId)
                    if (pending != null && !rec.deleted && same(codec.read(rec.entityType, rec.entityId), rec.payload)) {
                        // Same content on both sides (e.g. default meals created on each device): not a conflict.
                        dao.remove(pending.id)
                        dao.putVersion(SyncRecordVersionEntity(rec.entityType, rec.entityId, rec.version))
                        continue
                    }
                    if (pending != null) {
                        // Both sides changed: keep both versions and let the user decide.
                        dao.addConflict(SyncConflictEntity(entityType = rec.entityType, entityId = rec.entityId, localPayload = codec.read(rec.entityType, rec.entityId)?.toString(),
                            remotePayload = rec.payload?.toString(), remoteVersion = rec.version, detectedAt = time.now(), resolved = false))
                        dao.markFailed(pending.id, "Conflicts with a change made on another device", SyncStatus.CONFLICT)
                        continue
                    }
                    if (rec.deleted) codec.delete(rec.entityType, rec.entityId) else rec.payload?.let { codec.write(rec.entityType, it) }
                    dao.putVersion(SyncRecordVersionEntity(rec.entityType, rec.entityId, rec.version))
                    count++
                }
                putMeta(K_CURSOR, batch.maxOf { it.updatedAt })
            }
            if (batch.size < PAGE) break
        }
        return count
    }

    private suspend fun push(r: SyncRemote, s: Session): Pair<Int, Int> {
        var pushed = 0; var conflicts = 0
        while (true) {
            val items = dao.pending(PAGE).filter { it.attempts < MAX_ATTEMPTS }
            if (items.isEmpty()) break
            var progressed = false
            for (q in items) {
                val payload: JsonElement? = if (q.operation == SyncOperation.DELETE) null else codec.read(q.entityType, q.entityId)
                val deleted = q.operation == SyncOperation.DELETE || payload == null
                val base = dao.version(q.entityType, q.entityId) ?: q.baseRemoteVersion
                try {
                    when (val res = r.push(s, q.entityType, q.entityId, payload, deleted, base)) {
                        is PushResult.Ok -> db.withTransaction {
                            // Only clear the queue row if it wasn't re-enqueued by a newer local edit meanwhile.
                            val current = dao.queued(q.entityType, q.entityId)
                            if (current?.id == q.id) dao.remove(q.id)
                            dao.putVersion(SyncRecordVersionEntity(q.entityType, q.entityId, res.version)); pushed++; progressed = true
                        }
                        is PushResult.Conflict -> if (res.remote != null && !res.remote.deleted && same(payload, res.remote.payload)) {
                            dao.remove(q.id); dao.putVersion(SyncRecordVersionEntity(q.entityType, q.entityId, res.remote.version)); progressed = true
                        } else {
                            dao.addConflict(SyncConflictEntity(entityType = q.entityType, entityId = q.entityId, localPayload = payload?.toString(), remotePayload = res.remote?.payload?.toString(),
                                remoteVersion = res.remote?.version ?: -1, detectedAt = time.now(), resolved = false))
                            dao.markFailed(q.id, "Conflict", SyncStatus.CONFLICT); conflicts++; progressed = true
                        }
                    }
                } catch (e: RemoteException) {
                    dao.markFailed(q.id, e.message, SyncStatus.FAILED)
                    if (e.retryable) throw e
                }
            }
            if (!progressed) break
        }
        return pushed to conflicts
    }

    private fun same(a: JsonElement?, b: JsonElement?): Boolean = a != null && b != null && a == b

    /** Resolve a conflict by keeping the local version (it will overwrite the cloud copy on next sync). */
    suspend fun keepLocal(conflictId: Long) = db.withTransaction {
        val c = dao.conflict(conflictId) ?: return@withTransaction
        if (c.remoteVersion >= 0) dao.putVersion(SyncRecordVersionEntity(c.entityType, c.entityId, c.remoteVersion))
        val op = if (c.localPayload == null) SyncOperation.DELETE else SyncOperation.UPSERT
        dao.enqueue(SyncQueueEntity(entityType = c.entityType, entityId = c.entityId, operation = op, baseRemoteVersion = c.remoteVersion.takeIf { it >= 0 }, createdAt = time.now(), attempts = 0, lastError = null, status = SyncStatus.PENDING))
        dao.resolveConflict(conflictId)
    }

    /** Resolve a conflict by taking the cloud version. */
    suspend fun keepRemote(conflictId: Long) = db.withTransaction {
        val c = dao.conflict(conflictId) ?: return@withTransaction
        val remote = c.remotePayload?.let { codec.json.parseToJsonElement(it) }
        if (remote == null) codec.delete(c.entityType, c.entityId) else codec.write(c.entityType, remote)
        if (c.remoteVersion >= 0) dao.putVersion(SyncRecordVersionEntity(c.entityType, c.entityId, c.remoteVersion))
        dao.queued(c.entityType, c.entityId)?.let { dao.remove(it.id) }
        dao.resolveConflict(conflictId)
    }

    companion object {
        const val PAGE = 200
        const val MAX_ATTEMPTS = 10
        const val K_ACCESS = "access_token"; const val K_REFRESH = "refresh_token"; const val K_EXPIRES = "expires_at"
        const val K_USER = "user_id"; const val K_EMAIL = "email"; const val K_CURSOR = "pull_cursor"
        const val K_LAST_SYNC = "last_sync_at"; const val K_LAST_ERROR = "last_error"
    }
}
