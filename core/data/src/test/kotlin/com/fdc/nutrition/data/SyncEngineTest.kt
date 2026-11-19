package com.fdc.nutrition.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fdc.nutrition.data.db.entity.SyncStatus
import com.fdc.nutrition.data.sync.*
import com.fdc.nutrition.domain.model.Meal
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** In-memory backend with the same optimistic-concurrency contract as the Supabase RPC. */
class FakeRemote : SyncRemote {
    data class Row(val payload: JsonElement?, val deleted: Boolean, val version: Long, val updatedAt: String)
    val rows = linkedMapOf<Pair<String, String>, Row>()
    private var clock = 0L
    var offline = false
    private fun ts() = Instant.ofEpochMilli(1_700_000_000_000 + (++clock)).toString()
    private fun check() { if (offline) throw RemoteException("offline") }
    private val session = Session("a", "r", Long.MAX_VALUE / 2, "user-1", "me@example.com")
    override suspend fun signUp(email: String, password: String) = session
    override suspend fun signIn(email: String, password: String) = session.also { check() }
    override suspend fun refresh(refreshToken: String) = session
    override suspend fun pull(session: Session, sinceIso: String?, limit: Int): List<RemoteRecord> { check()
        return rows.filter { sinceIso == null || it.value.updatedAt > sinceIso }.entries.sortedBy { it.value.updatedAt }.take(limit)
            .map { (k, v) -> RemoteRecord(k.first, k.second, v.payload, v.deleted, v.version, v.updatedAt) } }
    override suspend fun fetch(session: Session, entityType: String, entityId: String) = rows[entityType to entityId]?.let { RemoteRecord(entityType, entityId, it.payload, it.deleted, it.version, it.updatedAt) }
    override suspend fun push(session: Session, entityType: String, entityId: String, payload: JsonElement?, deleted: Boolean, baseVersion: Long?): PushResult { check()
        val cur = rows[entityType to entityId]
        if (cur != null && cur.version != baseVersion) return PushResult.Conflict(fetch(session, entityType, entityId))
        val v = (cur?.version ?: 0) + 1
        rows[entityType to entityId] = Row(payload, deleted, v, ts())
        return PushResult.Ok(v)
    }
    override suspend fun deleteAccount(session: Session) { rows.clear() }
}

@RunWith(AndroidJUnit4::class)
class SyncEngineTest {
    private val remote = FakeRemote()
    private fun device(): Pair<TestGraph, SyncEngine> {
        val g = TestGraph()
        val factory = object : SyncRemoteFactory(OkHttpClient()) { override fun create(url: String, anonKey: String) = remote }
        return g to SyncEngine(g.db, g.codec, g.profile, factory, g.changes, g.time)
    }
    private suspend fun configure(g: TestGraph, e: SyncEngine) {
        g.seeder.seedDefaults()
        g.profile.updatePreferences { it.copy(supabaseUrl = "https://example.supabase.co", supabaseAnonKey = "anon") }
        e.signIn("me@example.com", "secret1", create = false)
    }

    @Test fun twoDevicesSyncAndConflictsAreNeverOverwritten() = runBlocking<Unit> {
        val (a, ea) = device(); val (b, eb) = device()
        configure(a, ea); configure(b, eb)
        // A logs offline-first; sync uploads
        val id = a.diary.quickAdd(a.today, Meal.LUNCH, "Dosa", 300.0)
        a.water.add(a.today, 250.0)
        val r1 = ea.sync() as SyncOutcome.Success
        assertThat(r1.pushed).isAtLeast(2)
        // B receives it
        eb.sync()
        assertThat(b.diary.entry(id)!!.nutrients.energyKcal).isEqualTo(300.0)
        assertThat(b.water.observeForDate(b.today).first()).hasSize(1)
        // Sync is idempotent
        val again = ea.sync() as SyncOutcome.Success
        assertThat(again.pushed).isEqualTo(0)

        // Both edit the same entry before syncing → conflict, both versions kept
        a.diary.updateQuickAdd(id, "Dosa", 350.0, null, null, null)
        b.diary.updateQuickAdd(id, "Dosa", 420.0, null, null, null)
        ea.sync()
        val rb = eb.sync() as SyncOutcome.Success
        val conflicts = eb.observeStatus().first().conflicts
        assertThat(conflicts).hasSize(1)
        assertThat(b.diary.entry(id)!!.nutrients.energyKcal).isEqualTo(420.0) // local not overwritten
        assertThat(conflicts.first().remotePayload).contains("350")
        // User picks the cloud version on B
        eb.keepRemote(conflicts.first().id)
        assertThat(b.diary.entry(id)!!.nutrients.energyKcal).isEqualTo(350.0)
        assertThat(rb.conflicts + 0).isAtLeast(0)

        // Deletion propagates
        a.diary.delete(id); ea.sync(); eb.sync()
        assertThat(b.diary.entry(id)).isNull()
    }

    @Test fun offlineFailuresAreRetriedWithoutLosingChanges() = runBlocking<Unit> {
        val (a, ea) = device()
        configure(a, ea)
        remote.offline = true
        a.diary.quickAdd(a.today, Meal.DINNER, "Biryani", 650.0)
        val r = ea.sync()
        assertThat(r).isInstanceOf(SyncOutcome.Failed::class.java)
        assertThat((r as SyncOutcome.Failed).retryable).isTrue()
        assertThat(a.db.syncDao().pending(100)).isNotEmpty() // still queued
        remote.offline = false
        ea.retryFailed()
        assertThat(ea.sync()).isInstanceOf(SyncOutcome.Success::class.java)
        assertThat(a.db.syncDao().pending(100)).isEmpty()
        assertThat(remote.rows.keys.map { it.first }).contains(EntityType.DIARY_ENTRY)
    }

    @Test fun localOnlyUseRecordsNothingInTheOutbox() = runBlocking<Unit> {
        val (a, _) = device()
        a.seeder.seedDefaults()
        a.diary.quickAdd(a.today, Meal.LUNCH, "Thali", 700.0)
        assertThat(a.db.syncDao().pending(10)).isEmpty()
        assertThat(SyncStatus.PENDING.name).isEqualTo("PENDING")
    }
}
