package com.fdc.nutrition.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.fdc.nutrition.data.db.entity.MealPlanEntity
import com.fdc.nutrition.data.db.entity.MealPlanItemEntity
import com.fdc.nutrition.data.db.entity.PlanRecurrenceEntity
import com.fdc.nutrition.data.db.entity.PlannedMealEntity
import com.fdc.nutrition.data.db.entity.RecipeEntity
import com.fdc.nutrition.data.db.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecipeDao {
    @Upsert suspend fun upsert(recipe: RecipeEntity)
    @Upsert suspend fun upsertIngredients(items: List<RecipeIngredientEntity>)
    @Query("DELETE FROM recipe_ingredients WHERE recipe_id = :recipeId") suspend fun deleteIngredients(recipeId: String)
    @Query("SELECT * FROM recipes WHERE id = :id") suspend fun get(id: String): RecipeEntity?
    @Query("SELECT * FROM recipes WHERE id = :id") fun observe(id: String): Flow<RecipeEntity?>
    @Query("SELECT * FROM recipes WHERE is_archived = :archived ORDER BY is_favourite DESC, updated_at DESC") fun observeAll(archived: Boolean = false): Flow<List<RecipeEntity>>
    @Query("SELECT * FROM recipes") suspend fun all(): List<RecipeEntity>
    @Query("SELECT * FROM recipe_ingredients WHERE recipe_id = :recipeId ORDER BY sort_order") suspend fun ingredients(recipeId: String): List<RecipeIngredientEntity>
    @Query("SELECT * FROM recipe_ingredients WHERE recipe_id = :recipeId ORDER BY sort_order") fun observeIngredients(recipeId: String): Flow<List<RecipeIngredientEntity>>
    @Query("SELECT * FROM recipe_ingredients ORDER BY sort_order") fun observeAllIngredients(): Flow<List<RecipeIngredientEntity>>
    @Query("SELECT * FROM recipe_ingredients") suspend fun allIngredients(): List<RecipeIngredientEntity>
    @Query("SELECT * FROM recipes WHERE is_archived = 0 AND name LIKE '%' || :q || '%' ORDER BY is_favourite DESC, name LIMIT 30") suspend fun search(q: String): List<RecipeEntity>
    @Query("SELECT (SELECT COUNT(*) FROM diary_entries WHERE recipe_id = :id) + (SELECT COUNT(*) FROM planned_meals WHERE recipe_id = :id) + (SELECT COUNT(*) FROM meal_plan_items WHERE recipe_id = :id) + (SELECT COUNT(*) FROM saved_meal_items WHERE recipe_id = :id) + (SELECT COUNT(*) FROM plan_recurrences WHERE recipe_id = :id)")
    suspend fun referenceCount(id: String): Int
    @Query("DELETE FROM recipes WHERE id = :id") suspend fun delete(id: String)

    @Transaction
    suspend fun save(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>) {
        upsert(recipe); deleteIngredients(recipe.id); upsertIngredients(ingredients)
    }
}

@Dao
interface PlanDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlanned(items: List<PlannedMealEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPlannedIgnore(items: List<PlannedMealEntity>)
    @Update suspend fun updatePlanned(item: PlannedMealEntity)
    @Upsert suspend fun upsertPlanned(item: PlannedMealEntity)
    @Query("DELETE FROM planned_meals WHERE id = :id") suspend fun deletePlanned(id: String)
    @Query("SELECT * FROM planned_meals WHERE id = :id") suspend fun getPlanned(id: String): PlannedMealEntity?
    @Query("SELECT * FROM planned_meals WHERE date BETWEEN :from AND :to AND is_skipped = 0 ORDER BY date, created_at") fun observeRange(from: LocalDate, to: LocalDate): Flow<List<PlannedMealEntity>>
    @Query("SELECT * FROM planned_meals WHERE date BETWEEN :from AND :to AND is_skipped = 0 ORDER BY date, created_at") suspend fun range(from: LocalDate, to: LocalDate): List<PlannedMealEntity>
    @Query("SELECT * FROM planned_meals WHERE date = :date AND is_skipped = 0 ORDER BY created_at") suspend fun forDate(date: LocalDate): List<PlannedMealEntity>
    @Query("SELECT * FROM planned_meals") suspend fun all(): List<PlannedMealEntity>
    @Query("SELECT DISTINCT date FROM planned_meals WHERE date BETWEEN :from AND :to AND is_skipped = 0") fun observePlannedDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>>
    @Query("UPDATE planned_meals SET logged_entry_id = NULL WHERE logged_entry_id = :entryId") suspend fun clearLoggedEntry(entryId: String)

    @Upsert suspend fun upsertRecurrence(item: PlanRecurrenceEntity)
    @Query("SELECT * FROM plan_recurrences WHERE is_active = 1") suspend fun activeRecurrences(): List<PlanRecurrenceEntity>
    @Query("SELECT * FROM plan_recurrences ORDER BY created_at DESC") fun observeRecurrences(): Flow<List<PlanRecurrenceEntity>>
    @Query("SELECT * FROM plan_recurrences") suspend fun allRecurrences(): List<PlanRecurrenceEntity>
    @Query("UPDATE plan_recurrences SET is_active = 0 WHERE id = :id") suspend fun deactivateRecurrence(id: String)
    @Query("DELETE FROM planned_meals WHERE recurrence_id = :id AND date >= :from AND logged_entry_id IS NULL") suspend fun deleteFutureOccurrences(id: String, from: LocalDate)

    @Upsert suspend fun upsertMealPlan(item: MealPlanEntity)
    @Upsert suspend fun upsertMealPlanItems(items: List<MealPlanItemEntity>)
    @Query("DELETE FROM meal_plan_items WHERE plan_id = :planId") suspend fun deleteMealPlanItems(planId: String)
    @Query("SELECT * FROM meal_plans ORDER BY is_favourite DESC, updated_at DESC") fun observeMealPlans(): Flow<List<MealPlanEntity>>
    @Query("SELECT * FROM meal_plans") suspend fun allMealPlans(): List<MealPlanEntity>
    @Query("SELECT * FROM meal_plans WHERE id = :id") suspend fun getMealPlan(id: String): MealPlanEntity?
    @Query("SELECT * FROM meal_plan_items WHERE plan_id = :planId ORDER BY day_offset") suspend fun mealPlanItems(planId: String): List<MealPlanItemEntity>
    @Query("SELECT * FROM meal_plan_items") suspend fun allMealPlanItems(): List<MealPlanItemEntity>
    @Query("DELETE FROM meal_plans WHERE id = :id") suspend fun deleteMealPlan(id: String)

    @Transaction
    suspend fun saveMealPlan(plan: MealPlanEntity, items: List<MealPlanItemEntity>) {
        upsertMealPlan(plan); deleteMealPlanItems(plan.id); upsertMealPlanItems(items)
    }
}
