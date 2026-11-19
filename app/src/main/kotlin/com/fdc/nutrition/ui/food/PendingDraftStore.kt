package com.fdc.nutrition.ui.food

import com.fdc.nutrition.data.repository.FoodDraft
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Holds an externally fetched product draft while the user reviews it on the editor screen. */
@Singleton
class PendingDraftStore @Inject constructor() {
    private val drafts = ConcurrentHashMap<String, Pair<FoodDraft, String?>>()
    fun put(d: FoodDraft, note: String? = null): String = UUID.randomUUID().toString().also { drafts[it] = d to note }
    fun get(key: String): Pair<FoodDraft, String?>? = drafts[key]
    fun remove(key: String) { drafts.remove(key) }
}
