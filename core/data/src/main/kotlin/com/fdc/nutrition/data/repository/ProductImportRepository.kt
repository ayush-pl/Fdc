package com.fdc.nutrition.data.repository

import com.fdc.nutrition.data.remote.LookupResult
import com.fdc.nutrition.data.remote.OpenFoodFactsProvider
import com.fdc.nutrition.data.remote.UsdaFdcProvider
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BarcodeOutcome {
    /** Already in the local database (works offline). */
    data class Local(val foodId: String) : BarcodeOutcome
    /** Found online; the user reviews/corrects before saving. */
    data class Remote(val result: LookupResult.Found) : BarcodeOutcome
    data object NotFound : BarcodeOutcome
    data class Unavailable(val reason: String) : BarcodeOutcome
}

/** Coordinates barcode/online lookups with the local cache and duplicate detection. */
@Singleton
class ProductImportRepository @Inject constructor(
    private val foods: FoodRepository,
    private val off: OpenFoodFactsProvider,
    private val usda: UsdaFdcProvider,
) {
    suspend fun lookupBarcode(barcode: String): BarcodeOutcome {
        val code = barcode.trim()
        foods.findByBarcode(code)?.let { return BarcodeOutcome.Local(it.id) }
        return when (val r = off.lookupBarcode(code)) {
            is LookupResult.Found -> BarcodeOutcome.Remote(r)
            LookupResult.NotFound -> BarcodeOutcome.NotFound
            is LookupResult.Unavailable -> BarcodeOutcome.Unavailable(r.reason)
        }
    }

    suspend fun searchOnline(query: String): Result<List<FoodDraft>> = usda.search(query)

    /** Saves a reviewed external product; returns the existing food if the same source record was imported before. */
    suspend fun saveImported(draft: FoodDraft): String {
        val ref = draft.sourceRef
        if (ref != null) foods.existingForSource(draft.source, ref)?.let { return it }
        draft.barcode?.let { b -> foods.findByBarcode(b)?.let { return it.id } }
        return foods.saveFood(draft)
    }
}
