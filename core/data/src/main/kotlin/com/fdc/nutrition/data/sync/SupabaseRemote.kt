package com.fdc.nutrition.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Supabase implementation using the REST (PostgREST) and Auth (GoTrue) HTTP APIs directly.
 * Server schema and RLS policies: docs/SUPABASE_SETUP.md. All data access is scoped to the
 * signed-in user by row-level security; the anon key alone cannot read anyone's data.
 */
class SupabaseRemote(private val client: OkHttpClient, baseUrl: String, private val anonKey: String) : SyncRemote {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    private suspend fun call(req: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        try { client.newCall(req).execute().use { it.code to it.body.string() } }
        catch (e: IOException) { throw RemoteException("Network unavailable: ${e.message}") }
    }

    private fun builder(path: String, token: String? = null) = Request.Builder().url("$base$path")
        .header("apikey", anonKey).header("Authorization", "Bearer ${token ?: anonKey}")

    private fun session(body: String): Session {
        val o = json.parseToJsonElement(body).jsonObject
        val user = o["user"]?.jsonObject ?: throw AuthException("Unexpected auth response")
        return Session(
            o["access_token"]!!.jsonPrimitive.content, o["refresh_token"]!!.jsonPrimitive.content,
            o["expires_at"]?.jsonPrimitive?.longOrNull ?: (System.currentTimeMillis() / 1000 + (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600)),
            user["id"]!!.jsonPrimitive.content, user["email"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun authError(code: Int, body: String): Nothing {
        val msg = runCatching { json.parseToJsonElement(body).jsonObject.let { (it["msg"] ?: it["error_description"] ?: it["message"])?.jsonPrimitive?.content } }.getOrNull()
        throw AuthException(msg ?: "Authentication failed (HTTP $code)")
    }

    override suspend fun signUp(email: String, password: String): Session? {
        val body = buildJsonObject { put("email", email); put("password", password) }.toString().toRequestBody(jsonType)
        val (code, text) = call(builder("/auth/v1/signup").post(body).build())
        if (code !in 200..299) authError(code, text)
        // When email confirmation is enabled no session is returned until the email is confirmed.
        return runCatching { session(text) }.getOrNull()
    }

    override suspend fun signIn(email: String, password: String): Session {
        val body = buildJsonObject { put("email", email); put("password", password) }.toString().toRequestBody(jsonType)
        val (code, text) = call(builder("/auth/v1/token?grant_type=password").post(body).build())
        if (code !in 200..299) authError(code, text)
        return session(text)
    }

    override suspend fun refresh(refreshToken: String): Session {
        val body = buildJsonObject { put("refresh_token", refreshToken) }.toString().toRequestBody(jsonType)
        val (code, text) = call(builder("/auth/v1/token?grant_type=refresh_token").post(body).build())
        if (code !in 200..299) authError(code, text)
        return session(text)
    }

    private fun record(o: JsonObject) = RemoteRecord(
        o["entity_type"]!!.jsonPrimitive.content, o["entity_id"]!!.jsonPrimitive.content, o["payload"]?.takeIf { it !is JsonNull },
        o["deleted"]?.jsonPrimitive?.booleanOrNull ?: false, o["version"]!!.jsonPrimitive.long, o["updated_at"]!!.jsonPrimitive.content,
    )

    override suspend fun pull(session: Session, sinceIso: String?, limit: Int): List<RemoteRecord> {
        val url = "$base/rest/v1/sync_records".toHttpUrl().newBuilder()
            .addQueryParameter("select", "entity_type,entity_id,payload,deleted,version,updated_at")
            .addQueryParameter("order", "updated_at.asc").addQueryParameter("limit", limit.toString())
            .apply { if (sinceIso != null) addQueryParameter("updated_at", "gt.$sinceIso") }.build()
        val (code, text) = call(Request.Builder().url(url).header("apikey", anonKey).header("Authorization", "Bearer ${session.accessToken}").build())
        if (code == 401) throw AuthException("Session expired")
        if (code !in 200..299) throw RemoteException("Pull failed (HTTP $code)")
        return json.parseToJsonElement(text).jsonArray.map { record(it.jsonObject) }
    }

    override suspend fun fetch(session: Session, entityType: String, entityId: String): RemoteRecord? {
        val url = "$base/rest/v1/sync_records".toHttpUrl().newBuilder()
            .addQueryParameter("select", "entity_type,entity_id,payload,deleted,version,updated_at")
            .addQueryParameter("entity_type", "eq.$entityType").addQueryParameter("entity_id", "eq.$entityId").build()
        val (code, text) = call(Request.Builder().url(url).header("apikey", anonKey).header("Authorization", "Bearer ${session.accessToken}").build())
        if (code !in 200..299) throw RemoteException("Fetch failed (HTTP $code)")
        return json.parseToJsonElement(text).jsonArray.firstOrNull()?.let { record(it.jsonObject) }
    }

    override suspend fun push(session: Session, entityType: String, entityId: String, payload: JsonElement?, deleted: Boolean, baseVersion: Long?): PushResult {
        val body = buildJsonObject {
            put("p_entity_type", entityType); put("p_entity_id", entityId); put("p_payload", payload ?: JsonNull)
            put("p_deleted", deleted); put("p_base_version", baseVersion?.let { JsonPrimitive(it) } ?: JsonNull)
        }.toString().toRequestBody(jsonType)
        val (code, text) = call(builder("/rest/v1/rpc/push_record", session.accessToken).post(body).build())
        if (code == 401) throw AuthException("Session expired")
        if (code !in 200..299) throw RemoteException("Push failed (HTTP $code): ${text.take(200)}", retryable = code >= 500 || code == 429)
        val o = json.parseToJsonElement(text).let { if (it is JsonArray) it.first().jsonObject else it.jsonObject }
        return when (o["status"]?.jsonPrimitive?.content) {
            "ok" -> PushResult.Ok(o["version"]!!.jsonPrimitive.long)
            "conflict" -> PushResult.Conflict(fetch(session, entityType, entityId))
            else -> throw RemoteException("Unexpected push response")
        }
    }

    override suspend fun deleteAccount(session: Session) {
        val (code, text) = call(builder("/rest/v1/rpc/delete_my_account", session.accessToken).post("{}".toRequestBody(jsonType)).build())
        if (code !in 200..299) throw RemoteException("Account deletion failed (HTTP $code): ${text.take(200)}", retryable = false)
    }
}
