package com.fdc.nutrition.data.repository

import androidx.room.withTransaction
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.entity.FoodBarcodeEntity
import com.fdc.nutrition.data.db.entity.FoodFavouriteEntity
import com.fdc.nutrition.data.db.entity.FoodNutrientsEntity
import com.fdc.nutrition.data.db.entity.FoodServingEntity
import com.fdc.nutrition.data.db.entity.NutrientColumns
import com.fdc.nutrition.data.db.entity.SearchHistoryEntity
import com.fdc.nutrition.data.db.toDomain
import com.fdc.nutrition.data.db.toEntity
import com.fdc.nutrition.data.sync.ChangeTracker
import com.fdc.nutrition.data.sync.EntityType
import com.fdc.nutrition.data.util.TimeProvider
import com.fdc.nutrition.domain.calc.Ids
import com.fdc.nutrition.domain.calc.NutritionCalculator
import com.fdc.nutrition.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import javax.inject.Singleton

class ValidationException(message: String) : IllegalArgumentException(message)

/** Input for creating or editing a custom food. Nutrients are per 100 g/ml of [basis]. */
data class FoodDraft(
    val id: String? = null,
    val name: String,
    val brand: String?,
    val categoryId: String,
    val basis: NutrientBasis,
    val nutrientsPer100: Nutrients,
    val servings: List<ServingSize>,
    val preparation: PreparationState = PreparationState.NOT_APPLICABLE,
    val barcode: String? = null,
    val dietClass: DietClass = DietClass.UNKNOWN,
    val allergens: Set<String> = emptySet(),
    val allergenInfoKnown: Boolean = false,
    val densityGPerMl: Double? = null,
    val source: FoodSource = FoodSource.USER,
    val sourceRef: String? = null,
    val dataQuality: DataQuality = DataQuality.USER_ENTERED,
    val notes: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FoodRepository @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeProvider,
    private val changes: ChangeTracker,
) {
    private val dao = db.foodDao()

    fun observeCategories(): Flow<List<FoodCategory>> = dao.observeCategories().map { l -> l.map { it.toDomain() } }

    suspend fun getFood(id: String): Food? {
        val f = dao.get(id) ?: return null
        return f.toDomain(dao.servings(id), dao.barcodes(id), favourite = false)
    }

    fun observeFood(id: String): Flow<Food?> =
        combine(dao.observe(id), dao.observeServings(id), dao.observeIsFavourite(id)) { f, s, fav ->
            f?.toDomain(s, dao.barcodes(id), fav)
        }

    fun observePopular(): Flow<List<Food>> = withFavourites(dao.observePopular().map { l -> l.map { it.toDomain() } })
    fun observeFavourites(): Flow<List<Food>> = dao.observeFavourites().map { l -> l.map { it.toDomain(favourite = true) } }
    fun observeCustom(archived: Boolean = false): Flow<List<Food>> = withFavourites(dao.observeCustom(archived).map { l -> l.map { it.toDomain() } })
    fun observeByCategory(categoryId: String, limit: Int = 400): Flow<List<Food>> =
        withFavourites(dao.observeByCategory(categoryId, limit).map { l -> l.map { it.toDomain() } })
    fun observeFoodCount(): Flow<Int> = dao.observeCount()

    private fun withFavourites(src: Flow<List<Food>>): Flow<List<Food>> =
        combine(src, dao.observeFavouriteIds()) { list, favs -> val set = favs.toSet(); list.map { it.copy(isFavourite = it.id in set) } }

    /**
     * Searches local foods by name, brand and aliases. Results are ranked: exact/prefix name
     * matches, then popular, custom and favourite foods, then shorter (more generic) names.
     */
    suspend fun search(query: String, categoryId: String? = null, limit: Int = 100): List<Food> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        val match = tokens.joinToString(" ") { "$it*" }
        val rows = dao.search(match, categoryId, 600)
        val q = query.trim().lowercase()
        return rows.map { it.toDomain() }
            .sortedByDescending { score(it, q, tokens) }
            .take(limit)
    }

    private fun score(f: Food, q: String, tokens: List<String>): Double {
        val name = f.name.lowercase()
        var s = 0.0
        if (name == q) s += 200
        if (name.startsWith(q)) s += 100
        val words = name.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (words.firstOrNull()?.startsWith(tokens.first()) == true) s += 60
        s += tokens.count { t -> words.any { it == t } } * 15
        if (f.isPopular) s += 50
        if (f.isCustom) s += 40
        if (f.source == FoodSource.OPEN_FOOD_FACTS) s += 10
        s -= name.length * 0.4
        return s
    }

    suspend fun recordSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val existing = dao.findSearch(q)
        dao.upsertSearch(SearchHistoryEntity(existing?.id ?: 0, q, time.now()))
        dao.trimSearchHistory()
    }
    fun observeSearchHistory(): Flow<List<String>> = dao.observeSearchHistory().map { l -> l.map { it.query } }
    suspend fun clearSearchHistory() = dao.clearSearchHistory()

    suspend fun existingForSource(source: FoodSource, ref: String): String? = dao.findBySource(source.name, ref)?.id

    suspend fun findByBarcode(barcode: String): Food? = dao.foodIdForBarcode(barcode.trim())?.let { getFood(it) }

    fun validate(d: FoodDraft): List<String> = buildList {
        if (d.name.isBlank()) add("Name is required.")
        if (d.name.length > 120) add("Name is too long (max 120 characters).")
        val n = d.nutrientsPer100
        if (n.energyKcal == null) add("Calories per 100 ${d.basis.symbol} are required.")
        n.values.forEach { (k, v) -> if (v < 0 || !v.isFinite()) add("${k.displayName} must be zero or more.") }
        n.energyKcal?.let { if (it > 900) add("Calories per 100 ${d.basis.symbol} cannot exceed 900 kcal.") }
        val massSum = listOfNotNull(n.proteinG, n.carbohydrateG, n.fatG).sum()
        if (d.basis == NutrientBasis.GRAM && massSum > 100.5) add("Protein + carbohydrate + fat cannot exceed 100 g per 100 g.")
        listOfNotNull(n[Nutrient.SATURATED_FAT], n[Nutrient.TRANS_FAT]).forEach { if (n.fatG != null && it > n.fatG!! + 0.01) add("Saturated/trans fat cannot exceed total fat.") }
        n[Nutrient.SUGARS]?.let { s -> n.carbohydrateG?.let { c -> if (s > c + 0.01) add("Sugars cannot exceed total carbohydrate.") } }
        d.servings.forEach { if (it.amount <= 0 || !it.amount.isFinite()) add("Serving \"${it.label}\" must have a positive size.") }
        d.barcode?.let { if (it.isNotBlank() && !it.trim().matches(Regex("\\d{6,14}"))) add("Barcode must be 6–14 digits.") }
    }.distinct()

    /** Warnings that don't block saving (e.g. label energy not matching macros). */
    fun warnings(d: FoodDraft): List<String> = listOfNotNull(NutritionCalculator.energyMismatch(d.nutrientsPer100))

    /** Finds existing foods that look like duplicates (same name+brand or barcode). */
    suspend fun possibleDuplicates(d: FoodDraft): List<Food> {
        val byName = dao.findByNameAndBrand(d.name.trim(), d.brand?.trim()?.ifBlank { null }).filter { it.id != d.id }.map { it.id }
        val byBarcode = d.barcode?.takeIf { it.isNotBlank() }?.let { dao.foodIdForBarcode(it.trim()) }?.takeIf { it != d.id }
        return (byName + listOfNotNull(byBarcode)).distinct().mapNotNull { getFood(it) }
    }

    /** Creates or updates a user-owned food. Seeded database foods are never edited in place. */
    suspend fun saveFood(d: FoodDraft): String {
        val errors = validate(d)
        if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("\n"))
        val now = time.now()
        val existing = d.id?.let { dao.get(it) }
        require(existing == null || existing.food.isCustom) { "Database foods can't be edited. Create a custom copy instead." }
        val id = existing?.food?.id ?: d.id ?: Ids.new()
        val food = Food(
            id = id, name = d.name.trim(), categoryId = d.categoryId, brand = d.brand?.trim()?.ifBlank { null }, basis = d.basis,
            nutrientsPer100 = d.nutrientsPer100, servings = d.servings, densityGPerMl = d.densityGPerMl, source = d.source,
            sourceRef = d.sourceRef, dataQuality = d.dataQuality, preparation = d.preparation, isCustom = true, isArchived = false,
            dietClass = d.dietClass, allergens = d.allergens, allergenInfoKnown = d.allergenInfoKnown, notes = d.notes,
            createdAt = existing?.food?.createdAt ?: now, updatedAt = now, sourceUpdatedAt = if (d.source == FoodSource.USER) null else now,
        )
        val servings = d.servings.mapIndexed { i, s -> s.copy(id = s.id.ifBlank { Ids.new() }, source = s.source ?: "USER").toEntity(id, i) }
        val barcodes = listOfNotNull(d.barcode?.trim()?.takeIf { it.isNotEmpty() }).map { FoodBarcodeEntity(it, id, now) }
        db.withTransaction {
            dao.insertFoodGraph(food.toEntity(), FoodNutrientsEntity(id, NutrientColumns.from(d.nutrientsPer100)), servings, barcodes)
            changes.upserted(EntityType.FOOD, id)
        }
        return id
    }

    /** Adds a personal serving size (e.g. "my bowl = 220 g") to any food, including database foods. */
    suspend fun addServing(foodId: String, unit: QuantityUnit, label: String, amount: Double, kind: PortionKind = PortionKind.AS_LISTED): String {
        if (label.isBlank()) throw ValidationException("Serving name is required.")
        if (!(amount > 0 && amount.isFinite() && amount < 100_000)) throw ValidationException("Serving size must be a positive number.")
        val id = Ids.new()
        val order = dao.servings(foodId).size
        db.withTransaction {
            dao.upsertServings(listOf(FoodServingEntity(id, foodId, unit, label.trim(), amount, false, kind, "USER", order)))
            dao.get(foodId)?.food?.let { dao.updateFood(it.copy(updatedAt = time.now())); if (it.isCustom) changes.upserted(EntityType.FOOD, foodId) }
        }
        return id
    }

    suspend fun deleteServing(foodId: String, servingId: String) {
        val s = dao.servings(foodId).firstOrNull { it.id == servingId } ?: return
        require(s.source == "USER") { "Only servings you added can be removed." }
        dao.deleteServing(servingId)
    }

    suspend fun setFavourite(foodId: String, favourite: Boolean) {
        db.withTransaction {
            if (favourite) dao.addFavourite(FoodFavouriteEntity(foodId, time.now())) else dao.removeFavourite(foodId)
            changes.record(EntityType.FAVOURITE, foodId, if (favourite) com.fdc.nutrition.data.db.entity.SyncOperation.UPSERT else com.fdc.nutrition.data.db.entity.SyncOperation.DELETE)
        }
    }

    /** Archives a food (hides it from search). Foods referenced by history are never deleted. */
    suspend fun archive(foodId: String, archived: Boolean = true) {
        db.withTransaction {
            dao.setArchived(foodId, archived, time.now())
            if (dao.get(foodId)?.food?.isCustom == true) changes.upserted(EntityType.FOOD, foodId)
        }
    }

    /**
     * Deletes a custom food only if nothing references it; otherwise it is archived.
     * @return true if deleted, false if archived instead.
     */
    suspend fun deleteOrArchive(foodId: String): Boolean = db.withTransaction {
        val food = dao.get(foodId)?.food ?: return@withTransaction false
        if (!food.isCustom || dao.referenceCount(foodId) > 0) {
            dao.setArchived(foodId, true, time.now()); if (food.isCustom) changes.upserted(EntityType.FOOD, foodId); false
        } else {
            dao.deleteFood(foodId); changes.deleted(EntityType.FOOD, foodId); true
        }
    }

    /** Creates an editable custom copy of a database food. */
    suspend fun duplicateAsCustom(foodId: String): String {
        val f = getFood(foodId) ?: throw ValidationException("Food not found")
        return saveFood(FoodDraft(
            name = "${f.name} (copy)", brand = f.brand, categoryId = f.categoryId, basis = f.basis, nutrientsPer100 = f.nutrientsPer100,
            servings = f.servings.map { it.copy(id = "", source = "USER") }, preparation = f.preparation, dietClass = f.dietClass,
            allergens = f.allergens, allergenInfoKnown = f.allergenInfoKnown, densityGPerMl = f.densityGPerMl,
            notes = "Copied from ${f.source.displayName}${f.sourceRef?.let { " #$it" } ?: ""}",
        ))
    }

    suspend fun foodsForIds(ids: List<String>): Map<String, Food> {
        if (ids.isEmpty()) return emptyMap()
        val servings = dao.servingsFor(ids).groupBy { it.foodId }
        return dao.getMany(ids).associate { it.food.id to it.toDomain(servings[it.food.id].orEmpty()) }
    }

    companion object {
        fun tokenize(query: String): List<String> =
            query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.take(6)
    }
}
