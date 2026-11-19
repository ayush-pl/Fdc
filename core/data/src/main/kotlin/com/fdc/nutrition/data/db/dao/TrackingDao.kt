package com.fdc.nutrition.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.fdc.nutrition.data.db.entity.AppMetadataEntity
import com.fdc.nutrition.data.db.entity.BodyMeasurementEntity
import com.fdc.nutrition.data.db.entity.NutritionTargetEntity
import com.fdc.nutrition.data.db.entity.ReminderEntity
import com.fdc.nutrition.data.db.entity.UserPreferencesEntity
import com.fdc.nutrition.data.db.entity.UserProfileEntity
import com.fdc.nutrition.data.db.entity.WaterEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class DailyWater(val date: LocalDate, val total: Double)

@Dao
interface WaterDao {
    @Upsert suspend fun upsert(item: WaterEntryEntity)
    @Query("DELETE FROM water_entries WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM water_entries WHERE id = :id") suspend fun get(id: String): WaterEntryEntity?
    @Query("SELECT * FROM water_entries WHERE date = :date ORDER BY logged_at DESC") fun observeForDate(date: LocalDate): Flow<List<WaterEntryEntity>>
    @Query("SELECT * FROM water_entries WHERE date BETWEEN :from AND :to ORDER BY date") fun observeRange(from: LocalDate, to: LocalDate): Flow<List<WaterEntryEntity>>
    @Query("SELECT * FROM water_entries WHERE date BETWEEN :from AND :to ORDER BY date") suspend fun range(from: LocalDate, to: LocalDate): List<WaterEntryEntity>
    @Query("SELECT * FROM water_entries ORDER BY date") suspend fun all(): List<WaterEntryEntity>
}

@Dao
interface BodyDao {
    @Upsert suspend fun upsert(item: BodyMeasurementEntity)
    @Query("DELETE FROM body_measurements WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM body_measurements WHERE id = :id") suspend fun get(id: String): BodyMeasurementEntity?
    @Query("SELECT * FROM body_measurements ORDER BY date DESC, created_at DESC") fun observeAll(): Flow<List<BodyMeasurementEntity>>
    @Query("SELECT * FROM body_measurements WHERE type = 'WEIGHT' ORDER BY date, created_at") fun observeWeights(): Flow<List<BodyMeasurementEntity>>
    @Query("SELECT * FROM body_measurements ORDER BY date") suspend fun all(): List<BodyMeasurementEntity>
    @Query("SELECT * FROM body_measurements WHERE type = 'WEIGHT' ORDER BY date DESC, created_at DESC LIMIT 1") suspend fun latestWeight(): BodyMeasurementEntity?
}

@Dao
interface ProfileDao {
    @Upsert suspend fun upsertProfile(item: UserProfileEntity)
    @Query("SELECT * FROM user_profile WHERE id = 'me'") fun observeProfile(): Flow<UserProfileEntity?>
    @Query("SELECT * FROM user_profile WHERE id = 'me'") suspend fun profile(): UserProfileEntity?
    @Upsert suspend fun upsertPreferences(item: UserPreferencesEntity)
    @Query("SELECT * FROM user_preferences WHERE id = 'me'") fun observePreferences(): Flow<UserPreferencesEntity?>
    @Query("SELECT * FROM user_preferences WHERE id = 'me'") suspend fun preferences(): UserPreferencesEntity?

    @Upsert suspend fun upsertTarget(item: NutritionTargetEntity)
    @Query("SELECT * FROM nutrition_targets ORDER BY effective_from") fun observeTargets(): Flow<List<NutritionTargetEntity>>
    @Query("SELECT * FROM nutrition_targets ORDER BY effective_from") suspend fun targets(): List<NutritionTargetEntity>
    @Query("SELECT * FROM nutrition_targets WHERE effective_from = :date") suspend fun targetFor(date: LocalDate): NutritionTargetEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRemindersIgnore(items: List<ReminderEntity>)
    @Upsert suspend fun upsertReminder(item: ReminderEntity)
    @Query("SELECT * FROM reminders") fun observeReminders(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders") suspend fun reminders(): List<ReminderEntity>
}

@Dao
interface MetadataDao {
    @Upsert suspend fun put(item: AppMetadataEntity)
    @Query("SELECT value FROM app_metadata WHERE `key` = :key") suspend fun get(key: String): String?
    @Query("SELECT value FROM app_metadata WHERE `key` = :key") fun observe(key: String): Flow<String?>
}
