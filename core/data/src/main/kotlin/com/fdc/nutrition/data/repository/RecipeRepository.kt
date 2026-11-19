package com.fdc.nutrition.data.repository

import androidx.room.withTransaction
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.entity.RecipeEntity
import com.fdc.nutrition.data.db.entity.RecipeIngredientEntity
import com.fdc.nutrition.data.sync.ChangeTracker
import com.fdc.nutrition.data.sync.EntityType
import com.fdc.nutrition.data.util.TimeProvider
import com.fdc.nutrition.domain.calc.Ids
import com.fdc.nutrition.domain.calc.RecipeCalculator
import com.fdc.nutrition.domain.calc.RecipeNutrition
import com.fdc.nutrition.domain.calc.ServingOption
import com.fdc.nutrition.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class IngredientDraft(val id: String? = null, val foodId: String, val quantity: Double, val option: ServingOption, val note: String? = null)

data class RecipeDraft(
    val id: String? = null,
    val name: String,
    val description: String?,
    val instructions: String?,
    val servings: Double,
    val finalWeightGrams: Double?,
    val imageUri: String?,
    val isFavourite: Boolean,
    val ingredients: List<IngredientDraft>,
)

data class RecipeSummary(val recipe: Recipe, val nutrition: RecipeNutrition)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RecipeRepository @Inject constructor(
    private val db: AppDatabase,
    private val foods: FoodRepository,
    private val time: TimeProvider,
    private val changes: ChangeTracker,
) {
    private val dao = db.recipeDao()

    /** Recipes with their nutrition calculated from the current ingredient food definitions. */
    fun observeRecipes(archived: Boolean = false): Flow<List<RecipeSummary>> =
        combine(dao.observeAll(archived), dao.observeAllIngredients()) { recipes, ings ->
            val byRecipe = ings.groupBy { it.recipeId }
            val foodMap = foods.foodsForIds(ings.map { it.foodId }.distinct())
            recipes.map { r -> assemble(r, byRecipe[r.id].orEmpty(), foodMap) }.map { RecipeSummary(it, RecipeCalculator.calculate(it)) }
        }

    fun observeRecipe(id: String): Flow<RecipeSummary?> =
        combine(dao.observe(id), dao.observeIngredients(id)) { r, ings ->
            r?.let { val rec = assemble(it, ings, foods.foodsForIds(ings.map { i -> i.foodId })); RecipeSummary(rec, RecipeCalculator.calculate(rec)) }
        }

    suspend fun getRecipe(id: String): Recipe? {
        val r = dao.get(id) ?: return null
        val ings = dao.ingredients(id)
        return assemble(r, ings, foods.foodsForIds(ings.map { it.foodId }))
    }

    suspend fun search(q: String): List<RecipeSummary> = dao.search(q.trim()).mapNotNull { r -> getRecipe(r.id)?.let { RecipeSummary(it, RecipeCalculator.calculate(it)) } }

    private fun assemble(r: RecipeEntity, ings: List<RecipeIngredientEntity>, foodMap: Map<String, Food>) = Recipe(
        id = r.id, name = r.name, description = r.description, instructions = r.instructions, servings = r.servings,
        finalWeightGrams = r.finalWeightGrams, imageUri = r.imageUri, isFavourite = r.isFavourite, isArchived = r.isArchived,
        createdAt = r.createdAt, updatedAt = r.updatedAt,
        ingredients = ings.mapNotNull { i ->
            val f = foodMap[i.foodId] ?: return@mapNotNull null
            RecipeIngredient(i.id, f.id, f.displayName, i.quantity, i.unit, i.unitLabel, i.amountPerUnit, f.basis, f.nutrientsPer100, f.preparation, f.densityGPerMl, i.sortOrder, i.note)
        },
    )

    fun validate(d: RecipeDraft): List<String> = buildList {
        if (d.name.isBlank()) add("Recipe name is required.")
        if (!(d.servings > 0 && d.servings <= 1000)) add("Servings must be between 0 and 1000.")
        if (d.ingredients.isEmpty()) add("Add at least one ingredient.")
        d.ingredients.forEach { if (!(it.quantity > 0 && it.quantity.isFinite())) add("Ingredient quantities must be positive.") }
        d.finalWeightGrams?.let { if (!(it > 0 && it < 100_000)) add("Final weight must be a positive number of grams.") }
    }.distinct()

    /** Saves a recipe and its ingredients in one transaction. Existing diary entries keep their snapshots. */
    suspend fun save(d: RecipeDraft): String {
        val errors = validate(d)
        if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("\n"))
        val now = time.now()
        val existing = d.id?.let { dao.get(it) }
        val id = existing?.id ?: d.id ?: Ids.new()
        val entity = RecipeEntity(id, d.name.trim(), d.description?.trim()?.ifBlank { null }, d.instructions?.trim()?.ifBlank { null }, d.servings,
            d.finalWeightGrams, d.imageUri, d.isFavourite, existing?.isArchived ?: false, existing?.createdAt ?: now, now)
        val ings = d.ingredients.mapIndexed { idx, i ->
            RecipeIngredientEntity(i.id ?: Ids.new(), id, i.foodId, i.quantity, i.option.unit, i.option.label, i.option.amountPerUnit, idx, i.note?.ifBlank { null })
        }
        db.withTransaction { dao.save(entity, ings); changes.upserted(EntityType.RECIPE, id) }
        return id
    }

    suspend fun duplicate(id: String): String {
        val r = getRecipe(id) ?: throw ValidationException("Recipe not found")
        return save(RecipeDraft(null, "${r.name} (copy)", r.description, r.instructions, r.servings, r.finalWeightGrams, r.imageUri, false,
            r.ingredients.map { IngredientDraft(null, it.foodId, it.quantity, ServingOption("", it.unit, it.unitLabel, it.amountPerUnit, isFoodSpecific = true), it.note) }))
    }

    suspend fun setFavourite(id: String, fav: Boolean) = update(id) { it.copy(isFavourite = fav) }
    suspend fun setArchived(id: String, archived: Boolean) = update(id) { it.copy(isArchived = archived) }

    /** Deletes a recipe that was never logged or planned; otherwise archives it. Returns true if deleted. */
    suspend fun deleteOrArchive(id: String): Boolean = db.withTransaction {
        if (dao.referenceCount(id) > 0) { update(id) { it.copy(isArchived = true) }; false }
        else { dao.delete(id); changes.deleted(EntityType.RECIPE, id); true }
    }

    private suspend fun update(id: String, f: (RecipeEntity) -> RecipeEntity) {
        db.withTransaction {
            val r = dao.get(id) ?: return@withTransaction
            dao.upsert(f(r).copy(updatedAt = time.now()))
            changes.upserted(EntityType.RECIPE, id)
        }
    }
}
