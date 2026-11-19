package com.fdc.nutrition.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.entity.*
import com.fdc.nutrition.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable internal class SeedCategory(val id: String, val name: String, val emoji: String, val order: Int)
@Serializable internal class SeedSource(val id: String, val title: String, val license: String, val url: String)
@Serializable internal class SeedFood(
    val id: String, val n: String, val c: String, val src: String, val ref: String? = null, val q: String, val p: String, val d: String,
    val nv: List<Double?>, val sv: List<JsonArray> = emptyList(), val pub: String? = null, val orig: String? = null, val al: String? = null,
    val pop: Int = 0, val den: Double? = null, val note: String? = null,
)
@Serializable internal class SeedDoc(val version: Int, val nutrients: List<String>, val categories: List<SeedCategory>, val sources: List<SeedSource>, val foods: List<SeedFood>)

sealed interface SeedState {
    data object Idle : SeedState
    data class Running(val message: String) : SeedState
    data class Done(val foodCount: Int) : SeedState
    data class Failed(val error: String) : SeedState
}

/** Opens the bundled seed asset; replaceable in tests. */
open class SeedAssetSource @Inject constructor(@ApplicationContext private val context: Context) {
    open fun open(): InputStream = context.assets.open(FoodSeeder.ASSET)
}

/**
 * Loads the bundled USDA-derived food database into Room on first launch (and upgrades seeded rows
 * when a newer asset version ships). User data is never touched: custom foods are separate rows and
 * user-added serving sizes (source = USER) are preserved.
 */
@Singleton
class FoodSeeder @Inject constructor(
    private val assets: SeedAssetSource,
    private val db: AppDatabase,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SeedState>(SeedState.Idle)
    val state: StateFlow<SeedState> = _state

    suspend fun ensureSeeded(): SeedState = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                seedDefaults()
                val current = db.metadataDao().get(KEY_SEED_VERSION)?.toIntOrNull() ?: 0
                if (current >= SEED_VERSION && db.foodDao().count() > 0) {
                    _state.value = SeedState.Done(db.foodDao().count()); return@withContext _state.value
                }
                _state.value = SeedState.Running("Preparing food database…")
                val doc = assets.open().use { parse(it) }
                import(doc)
                _state.value = SeedState.Done(db.foodDao().count())
            } catch (e: Exception) {
                _state.value = SeedState.Failed(e.message ?: e.toString())
            }
            _state.value
        }
    }

    /** Default meals and reminders (idempotent). */
    suspend fun seedDefaults() {
        val dao = db.diaryDao()
        val existing = dao.allMeals().map { it.id }.toSet()
        dao.upsertMeals(Meal.DEFAULTS.filter { it.id !in existing }.map { MealEntity(it.id, it.name, it.sortOrder, true) })
        db.profileDao().insertRemindersIgnore(ReminderType.entries.map { ReminderEntity(it, false, it.defaultHour, it.defaultMinute) })
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun parse(input: InputStream): SeedDoc =
        GZIPInputStream(input.buffered()).use { json.decodeFromStream<SeedDoc>(it) }

    private suspend fun import(doc: SeedDoc) {
        val keys = doc.nutrients.map { Nutrient.fromKey(it) }
        val now = Instant.now()
        val foods = ArrayList<FoodEntity>(doc.foods.size)
        val nutrients = ArrayList<FoodNutrientsEntity>(doc.foods.size)
        val servings = ArrayList<FoodServingEntity>()
        doc.foods.forEach { f ->
            val prep = when (f.p) { "RAW" -> PreparationState.RAW; "COOKED" -> PreparationState.COOKED; "PREPARED" -> PreparationState.PREPARED; else -> PreparationState.NOT_APPLICABLE }
            val published = f.pub?.let { runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull() }
            foods += FoodEntity(
                id = f.id, name = f.n, brand = null, categoryId = f.c, basis = NutrientBasis.GRAM, densityGPerMl = f.den,
                source = FoodSource.valueOf(f.src), sourceRef = f.ref, dataQuality = DataQuality.valueOf(f.q), preparation = prep,
                isCustom = false, isArchived = false, isPopular = f.pop == 1, dietClass = DietClass.valueOf(f.d), allergens = "",
                allergenInfoKnown = false, aliases = f.al, originalName = f.orig, notes = f.note, seedVersion = doc.version,
                createdAt = now, updatedAt = now, sourceUpdatedAt = published,
            )
            val map = HashMap<Nutrient, Double>()
            f.nv.forEachIndexed { i, v -> val k = keys.getOrNull(i); if (k != null && v != null) map[k] = v }
            nutrients += FoodNutrientsEntity(f.id, NutrientColumns.from(Nutrients(map)))
            val kind = when (prep) {
                PreparationState.RAW -> PortionKind.EDIBLE_PORTION
                PreparationState.COOKED -> PortionKind.COOKED_WEIGHT
                PreparationState.PREPARED -> PortionKind.PREPARED_SERVING
                PreparationState.NOT_APPLICABLE -> PortionKind.AS_LISTED
            }
            val defaultIndex = f.sv.indexOfFirst { it[0].jsonPrimitive.content in setOf("PIECE", "SLICE", "PLATE") }
            f.sv.forEachIndexed { i, s ->
                servings += FoodServingEntity(
                    id = "${f.id}:s$i", foodId = f.id, unit = QuantityUnit.valueOf(s[0].jsonPrimitive.content), label = s[1].jsonPrimitive.content,
                    amount = s[2].jsonPrimitive.double, isDefault = i == defaultIndex, portionKind = kind, source = f.src, sortOrder = i,
                )
            }
        }
        db.withTransaction {
            val dao = db.foodDao()
            dao.upsertCategories(doc.categories.map { FoodCategoryEntity(it.id, it.name, it.emoji, it.order) })
            foods.chunked(500).forEach { dao.upsertFoods(it) }
            nutrients.chunked(500).forEach { dao.upsertNutrients(it) }
            foods.map { it.id }.chunked(500).forEach { dao.deleteSourceServingsFor(it) }
            servings.chunked(500).forEach { dao.upsertServings(it) }
            db.metadataDao().put(AppMetadataEntity(KEY_SEED_VERSION, doc.version.toString()))
        }
    }

    companion object {
        const val SEED_VERSION = 1
        const val ASSET = "seed/foods.seed"
        const val KEY_SEED_VERSION = "seed_version"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
