package com.fdc.nutrition.data.remote

import com.fdc.nutrition.data.repository.FoodDraft
import com.fdc.nutrition.domain.model.FoodSource

/** Result of an online lookup. Products are never invented: unknown barcodes return [NotFound]. */
sealed interface LookupResult {
    data class Found(val draft: FoodDraft, val imageUrl: String? = null, val completenessNote: String? = null) : LookupResult
    data object NotFound : LookupResult
    data class Unavailable(val reason: String) : LookupResult
}

/** Adapter for an external food data source. Each provider keeps its own attribution and limits. */
interface FoodDataProvider {
    val source: FoodSource
    val attribution: String
    suspend fun lookupBarcode(barcode: String): LookupResult = LookupResult.NotFound
    suspend fun search(query: String): Result<List<FoodDraft>> = Result.success(emptyList())
}

class OfflineException(message: String = "You're offline. Online product lookup is unavailable; local foods still work.") : Exception(message)
