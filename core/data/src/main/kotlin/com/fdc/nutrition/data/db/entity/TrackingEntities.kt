package com.fdc.nutrition.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fdc.nutrition.domain.model.ActivityLevel
import com.fdc.nutrition.domain.model.BeverageType
import com.fdc.nutrition.domain.model.DietaryPreference
import com.fdc.nutrition.domain.model.Goal
import com.fdc.nutrition.domain.model.LengthUnit
import com.fdc.nutrition.domain.model.MacroMethod
import com.fdc.nutrition.domain.model.MeasurementType
import com.fdc.nutrition.domain.model.QuantityUnit
import com.fdc.nutrition.domain.model.Sex
import com.fdc.nutrition.domain.model.TargetOrigin
import com.fdc.nutrition.domain.model.ThemeMode
import com.fdc.nutrition.domain.model.WeightUnit
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "nutrition_targets", indices = [Index(value = ["effective_from"], unique = true)])
data class NutritionTargetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "effective_from") val effectiveFrom: LocalDate,
    @Embedded(prefix = "t_") val values: NutrientColumns,
    @ColumnInfo(name = "water_ml") val waterMl: Double,
    @ColumnInfo(name = "macro_method") val macroMethod: MacroMethod,
    @ColumnInfo(name = "protein_pct") val proteinPct: Double?,
    @ColumnInfo(name = "carb_pct") val carbPct: Double?,
    @ColumnInfo(name = "fat_pct") val fatPct: Double?,
    val origin: TargetOrigin,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(tableName = "water_entries", indices = [Index("date")])
data class WaterEntryEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    @ColumnInfo(name = "amount_ml") val amountMl: Double,
    val beverage: BeverageType,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
)

@Entity(tableName = "body_measurements", indices = [Index("date"), Index(value = ["type", "date"])])
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val type: MeasurementType,
    @ColumnInfo(name = "custom_name") val customName: String?,
    /** kg for weight, cm for lengths, % for body fat. */
    val value: Double,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: LocalDate?,
    @ColumnInfo(name = "height_cm") val heightCm: Double?,
    val sex: Sex,
    @ColumnInfo(name = "current_weight_kg") val currentWeightKg: Double?,
    @ColumnInfo(name = "start_weight_kg") val startWeightKg: Double?,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double?,
    @ColumnInfo(name = "activity_level") val activityLevel: ActivityLevel,
    val goal: Goal,
    @ColumnInfo(name = "weekly_change_kg") val weeklyChangeKg: Double,
    @ColumnInfo(name = "dietary_preference") val dietaryPreference: DietaryPreference,
    /** Comma-separated allergen tags. */
    val allergies: String,
    val restrictions: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) { companion object { const val SINGLETON_ID = "me" } }

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: String = UserProfileEntity.SINGLETON_ID,
    val theme: ThemeMode,
    @ColumnInfo(name = "weight_unit") val weightUnit: WeightUnit,
    @ColumnInfo(name = "length_unit") val lengthUnit: LengthUnit,
    @ColumnInfo(name = "default_solid_unit") val defaultSolidUnit: QuantityUnit,
    @ColumnInfo(name = "default_liquid_unit") val defaultLiquidUnit: QuantityUnit,
    @ColumnInfo(name = "count_beverages_as_water") val countBeveragesAsWater: Boolean,
    @ColumnInfo(name = "water_quick_amounts") val waterQuickAmounts: String,
    @ColumnInfo(name = "dynamic_color") val dynamicColor: Boolean,
    @ColumnInfo(name = "sync_enabled") val syncEnabled: Boolean,
    @ColumnInfo(name = "supabase_url") val supabaseUrl: String?,
    @ColumnInfo(name = "supabase_anon_key") val supabaseAnonKey: String?,
    @ColumnInfo(name = "usda_api_key") val usdaApiKey: String?,
)

enum class ReminderType(val title: String, val defaultHour: Int, val defaultMinute: Int) {
    BREAKFAST("Log breakfast", 9, 30), LUNCH("Log lunch", 14, 0), DINNER("Log dinner", 21, 0),
    WATER("Drink water", 16, 0), MEAL_PLANNING("Plan tomorrow's meals", 20, 0), DAILY_REVIEW("Review today's nutrition", 22, 0),
}

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val type: ReminderType,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(@PrimaryKey val key: String, val value: String)
