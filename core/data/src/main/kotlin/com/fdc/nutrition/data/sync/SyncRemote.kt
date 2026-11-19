package com.fdc.nutrition.data.sync

import kotlinx.serialization.json.JsonElement

data class Session(val accessToken: String, val refreshToken: String, val expiresAtEpochSec: Long, val userId: String, val email: String?)

/** A record as stored in the cloud. [version] increments on every accepted write. */
data class RemoteRecord(val entityType: String, val entityId: String, val payload: JsonElement?, val deleted: Boolean, val version: Long, val updatedAt: String)

sealed interface PushResult {
    data class Ok(val version: Long) : PushResult
    /** The cloud copy changed since we last saw it; nothing was written. */
    data class Conflict(val remote: RemoteRecord?) : PushResult
}

class AuthException(message: String) : Exception(message)
class RemoteException(message: String, val retryable: Boolean = true) : Exception(message)

/** Cloud backend contract (implemented for Supabase; replaceable in tests). */
interface SyncRemote {
    suspend fun signUp(email: String, password: String): Session?
    suspend fun signIn(email: String, password: String): Session
    suspend fun refresh(refreshToken: String): Session
    suspend fun pull(session: Session, sinceIso: String?, limit: Int): List<RemoteRecord>
    suspend fun push(session: Session, entityType: String, entityId: String, payload: JsonElement?, deleted: Boolean, baseVersion: Long?): PushResult
    suspend fun fetch(session: Session, entityType: String, entityId: String): RemoteRecord?
    suspend fun deleteAccount(session: Session)
}
