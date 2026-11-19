package com.fdc.nutrition.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.fdc.nutrition.data.db.entity.*
import java.time.LocalDate

/** Single-record lookups and deletes used by sync and restore. */
@Dao
interface RecordDao {
    @Query("SELECT * FROM meals WHERE id = :id") suspend fun meal(id: String): MealEntity?
    @Query("SELECT * FROM meal_notes WHERE date = :date AND meal_id = :mealId") suspend fun mealNote(date: LocalDate, mealId: String): MealNoteEntity?
    @Query("SELECT * FROM day_logs WHERE date = :date") suspend fun dayLog(date: LocalDate): DayLogEntity?
    @Query("DELETE FROM day_logs WHERE date = :date") suspend fun deleteDayLog(date: LocalDate)
    @Query("SELECT * FROM saved_meals WHERE id = :id") suspend fun savedMeal(id: String): SavedMealEntity?
    @Query("DELETE FROM saved_meal_items WHERE saved_meal_id = :id") suspend fun deleteSavedMealItems(id: String)
    @Query("SELECT * FROM plan_recurrences WHERE id = :id") suspend fun recurrence(id: String): PlanRecurrenceEntity?
    @Query("DELETE FROM plan_recurrences WHERE id = :id") suspend fun deleteRecurrence(id: String)
    @Query("SELECT * FROM nutrition_targets WHERE id = :id") suspend fun target(id: String): NutritionTargetEntity?
    @Query("DELETE FROM nutrition_targets WHERE id = :id") suspend fun deleteTarget(id: String)
    @Query("DELETE FROM nutrition_targets WHERE effective_from = :date AND id != :id") suspend fun deleteTargetsOnDateExcept(date: LocalDate, id: String)
    @Query("SELECT * FROM food_favourites WHERE food_id = :id") suspend fun favourite(id: String): FoodFavouriteEntity?
    @Query("SELECT * FROM food_favourites") suspend fun favourites(): List<FoodFavouriteEntity>
    @Query("SELECT COUNT(*) FROM foods WHERE id = :id") suspend fun foodExists(id: String): Int
    @Query("SELECT COUNT(*) FROM recipes WHERE id = :id") suspend fun recipeExists(id: String): Int
    @Query("SELECT COUNT(*) FROM food_categories WHERE id = :id") suspend fun categoryExists(id: String): Int
    @Query("SELECT * FROM food_servings WHERE source = 'USER' AND food_id IN (SELECT id FROM foods WHERE is_custom = 0)") suspend fun userServingsOnDatabaseFoods(): List<FoodServingEntity>

    @Query("SELECT id FROM foods WHERE is_custom = 1") suspend fun customFoodIds(): List<String>
    @Query("SELECT id FROM diary_entries") suspend fun diaryIds(): List<String>
    @Query("SELECT id FROM meals") suspend fun mealIds(): List<String>
    @Query("SELECT date || '|' || meal_id FROM meal_notes") suspend fun mealNoteIds(): List<String>
    @Query("SELECT date FROM day_logs") suspend fun dayLogDates(): List<LocalDate>
    @Query("SELECT id FROM saved_meals") suspend fun savedMealIds(): List<String>
    @Query("SELECT id FROM recipes") suspend fun recipeIds(): List<String>
    @Query("SELECT id FROM planned_meals") suspend fun plannedIds(): List<String>
    @Query("SELECT id FROM plan_recurrences") suspend fun recurrenceIds(): List<String>
    @Query("SELECT id FROM meal_plans") suspend fun mealPlanIds(): List<String>
    @Query("SELECT id FROM nutrition_targets") suspend fun targetIds(): List<String>
    @Query("SELECT id FROM water_entries") suspend fun waterIds(): List<String>
    @Query("SELECT id FROM body_measurements") suspend fun bodyIds(): List<String>

    // bulk deletion of user data (keeps the seeded food database)
    @Query("DELETE FROM diary_entries") suspend fun clearDiary()
    @Query("DELETE FROM meal_notes") suspend fun clearMealNotes()
    @Query("DELETE FROM day_logs") suspend fun clearDayLogs()
    @Query("DELETE FROM saved_meals") suspend fun clearSavedMeals()
    @Query("DELETE FROM planned_meals") suspend fun clearPlanned()
    @Query("DELETE FROM plan_recurrences") suspend fun clearRecurrences()
    @Query("DELETE FROM meal_plans") suspend fun clearMealPlans()
    @Query("DELETE FROM recipes") suspend fun clearRecipes()
    @Query("DELETE FROM water_entries") suspend fun clearWater()
    @Query("DELETE FROM body_measurements") suspend fun clearBody()
    @Query("DELETE FROM nutrition_targets") suspend fun clearTargets()
    @Query("DELETE FROM user_profile") suspend fun clearProfile()
    @Query("DELETE FROM user_preferences") suspend fun clearPreferences()
    @Query("DELETE FROM food_favourites") suspend fun clearFavourites()
    @Query("DELETE FROM search_history") suspend fun clearSearchHistory()
    @Query("DELETE FROM food_servings WHERE source = 'USER'") suspend fun clearUserServings()
    @Query("DELETE FROM foods WHERE is_custom = 1") suspend fun clearCustomFoods()
    @Query("UPDATE foods SET is_archived = 0 WHERE is_custom = 0") suspend fun unarchiveDatabaseFoods()
    @Query("DELETE FROM meals WHERE is_default = 0") suspend fun clearCustomMeals()
    @Query("DELETE FROM sync_queue") suspend fun clearSyncQueue()
    @Query("DELETE FROM sync_conflicts") suspend fun clearSyncConflicts()
    @Query("DELETE FROM sync_record_versions") suspend fun clearSyncVersions()
    @Query("DELETE FROM sync_metadata") suspend fun clearSyncMetadata()
}
