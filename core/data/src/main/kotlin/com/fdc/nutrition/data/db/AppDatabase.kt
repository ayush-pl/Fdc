package com.fdc.nutrition.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.fdc.nutrition.data.db.dao.BodyDao
import com.fdc.nutrition.data.db.dao.DiaryDao
import com.fdc.nutrition.data.db.dao.FoodDao
import com.fdc.nutrition.data.db.dao.MetadataDao
import com.fdc.nutrition.data.db.dao.PlanDao
import com.fdc.nutrition.data.db.dao.ProfileDao
import com.fdc.nutrition.data.db.dao.RecipeDao
import com.fdc.nutrition.data.db.dao.SyncDao
import com.fdc.nutrition.data.db.dao.WaterDao
import com.fdc.nutrition.data.db.entity.*
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun fromEpochDay(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun toEpochDay(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun fromMillis(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)
    @TypeConverter fun toMillis(i: Instant?): Long? = i?.toEpochMilli()
}

@Database(
    entities = [
        FoodCategoryEntity::class, FoodEntity::class, FoodFtsEntity::class, FoodNutrientsEntity::class,
        FoodServingEntity::class, FoodBarcodeEntity::class, FoodFavouriteEntity::class, SearchHistoryEntity::class,
        MealEntity::class, DiaryEntryEntity::class, MealNoteEntity::class, DayLogEntity::class,
        SavedMealEntity::class, SavedMealItemEntity::class,
        RecipeEntity::class, RecipeIngredientEntity::class,
        MealPlanEntity::class, MealPlanItemEntity::class, PlannedMealEntity::class, PlanRecurrenceEntity::class,
        NutritionTargetEntity::class, WaterEntryEntity::class, BodyMeasurementEntity::class,
        UserProfileEntity::class, UserPreferencesEntity::class, ReminderEntity::class, AppMetadataEntity::class,
        SyncMetadataEntity::class, SyncQueueEntity::class, SyncRecordVersionEntity::class, SyncConflictEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun planDao(): PlanDao
    abstract fun waterDao(): WaterDao
    abstract fun bodyDao(): BodyDao
    abstract fun profileDao(): ProfileDao
    abstract fun metadataDao(): MetadataDao
    abstract fun syncDao(): SyncDao

    companion object { const val NAME = "fdc_nutrition.db" }
}
