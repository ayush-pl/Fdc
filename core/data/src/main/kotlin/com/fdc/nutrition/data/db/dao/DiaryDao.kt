package com.fdc.nutrition.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.fdc.nutrition.data.db.entity.DayLogEntity
import com.fdc.nutrition.data.db.entity.DiaryEntryEntity
import com.fdc.nutrition.data.db.entity.MealEntity
import com.fdc.nutrition.data.db.entity.MealNoteEntity
import com.fdc.nutrition.data.db.entity.SavedMealEntity
import com.fdc.nutrition.data.db.entity.SavedMealItemEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class DayEntryStats(val date: LocalDate, val entries: Int, val meals: Int, val kcal: Double?)

@Dao
interface DiaryDao {
    // meals
    @Upsert suspend fun upsertMeals(items: List<MealEntity>)
    @Upsert suspend fun upsertMeal(item: MealEntity)
    @Query("SELECT * FROM meals WHERE is_archived = 0 ORDER BY sort_order") fun observeMeals(): Flow<List<MealEntity>>
    @Query("SELECT * FROM meals ORDER BY sort_order") suspend fun allMeals(): List<MealEntity>

    // entries
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entry: DiaryEntryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(entries: List<DiaryEntryEntity>)
    @Update suspend fun update(entry: DiaryEntryEntity)
    @Upsert suspend fun upsert(entry: DiaryEntryEntity)
    @Delete suspend fun delete(entry: DiaryEntryEntity)
    @Query("DELETE FROM diary_entries WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT * FROM diary_entries WHERE id = :id") suspend fun get(id: String): DiaryEntryEntity?
    @Query("SELECT * FROM diary_entries WHERE id = :id") fun observe(id: String): Flow<DiaryEntryEntity?>
    @Query("SELECT * FROM diary_entries WHERE date = :date ORDER BY logged_at") fun observeForDate(date: LocalDate): Flow<List<DiaryEntryEntity>>
    @Query("SELECT * FROM diary_entries WHERE date = :date ORDER BY logged_at") suspend fun forDate(date: LocalDate): List<DiaryEntryEntity>
    @Query("SELECT * FROM diary_entries WHERE date = :date AND meal_id = :mealId ORDER BY logged_at") suspend fun forMeal(date: LocalDate, mealId: String): List<DiaryEntryEntity>
    @Query("SELECT * FROM diary_entries WHERE date BETWEEN :from AND :to ORDER BY date, logged_at") fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DiaryEntryEntity>>
    @Query("SELECT * FROM diary_entries WHERE date BETWEEN :from AND :to ORDER BY date, logged_at") suspend fun range(from: LocalDate, to: LocalDate): List<DiaryEntryEntity>
    @Query("SELECT * FROM diary_entries ORDER BY date, logged_at") suspend fun all(): List<DiaryEntryEntity>
    @Query("SELECT MIN(date) FROM diary_entries") suspend fun firstDate(): LocalDate?
    @Query("SELECT * FROM diary_entries WHERE type != 'QUICK_ADD' ORDER BY logged_at DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<DiaryEntryEntity>>
    @Query("SELECT * FROM diary_entries WHERE food_id = :foodId ORDER BY logged_at DESC LIMIT 1") suspend fun lastForFood(foodId: String): DiaryEntryEntity?
    @Query("SELECT * FROM diary_entries WHERE planned_source_key = :key") suspend fun byPlannedKey(key: String): DiaryEntryEntity?

    @Query(
        """SELECT date, COUNT(*) AS entries, COUNT(DISTINCT meal_id) AS meals,
           SUM(s_energy_kcal * quantity * amount_per_unit / reference_amount) AS kcal
           FROM diary_entries WHERE date BETWEEN :from AND :to GROUP BY date"""
    )
    fun observeDayStats(from: LocalDate, to: LocalDate): Flow<List<DayEntryStats>>

    // notes & day logs
    @Upsert suspend fun upsertMealNote(note: MealNoteEntity)
    @Query("DELETE FROM meal_notes WHERE date = :date AND meal_id = :mealId") suspend fun deleteMealNote(date: LocalDate, mealId: String)
    @Query("SELECT * FROM meal_notes WHERE date = :date") fun observeMealNotes(date: LocalDate): Flow<List<MealNoteEntity>>
    @Query("SELECT * FROM meal_notes") suspend fun allMealNotes(): List<MealNoteEntity>
    @Upsert suspend fun upsertDayLog(log: DayLogEntity)
    @Query("SELECT * FROM day_logs WHERE date = :date") fun observeDayLog(date: LocalDate): Flow<DayLogEntity?>
    @Query("SELECT * FROM day_logs WHERE date BETWEEN :from AND :to") fun observeDayLogs(from: LocalDate, to: LocalDate): Flow<List<DayLogEntity>>
    @Query("SELECT * FROM day_logs") suspend fun allDayLogs(): List<DayLogEntity>

    // saved meals
    @Upsert suspend fun upsertSavedMeal(item: SavedMealEntity)
    @Upsert suspend fun upsertSavedMealItems(items: List<SavedMealItemEntity>)
    @Query("SELECT * FROM saved_meals ORDER BY updated_at DESC") fun observeSavedMeals(): Flow<List<SavedMealEntity>>
    @Query("SELECT * FROM saved_meals") suspend fun allSavedMeals(): List<SavedMealEntity>
    @Query("SELECT * FROM saved_meal_items WHERE saved_meal_id = :id ORDER BY sort_order") suspend fun savedMealItems(id: String): List<SavedMealItemEntity>
    @Query("SELECT * FROM saved_meal_items ORDER BY sort_order") fun observeAllSavedMealItems(): Flow<List<SavedMealItemEntity>>
    @Query("SELECT * FROM saved_meal_items") suspend fun allSavedMealItems(): List<SavedMealItemEntity>
    @Query("DELETE FROM saved_meals WHERE id = :id") suspend fun deleteSavedMeal(id: String)

    @Transaction
    suspend fun saveMealTemplate(meal: SavedMealEntity, items: List<SavedMealItemEntity>) {
        upsertSavedMeal(meal); upsertSavedMealItems(items)
    }
}
