package com.fdc.nutrition.domain.model

import java.time.Instant
import java.time.LocalDate

enum class Sex(val displayName: String) { MALE("Male"), FEMALE("Female"), UNSPECIFIED("Prefer not to say") }

enum class ActivityLevel(val displayName: String, val description: String, val factor: Double) {
    SEDENTARY("Sedentary", "Little or no exercise, desk job", 1.2),
    LIGHT("Lightly active", "Light exercise 1–3 days/week", 1.375),
    MODERATE("Moderately active", "Moderate exercise 3–5 days/week", 1.55),
    ACTIVE("Very active", "Hard exercise 6–7 days/week", 1.725),
    EXTRA("Extra active", "Physical job or twice-daily training", 1.9),
}

enum class Goal(val displayName: String) {
    LOSE("Lose weight"), MAINTAIN("Maintain weight"), GAIN("Gain weight"), CUSTOM("Custom targets");
}

enum class DietaryPreference(val displayName: String) {
    NON_VEGETARIAN("Non-vegetarian"), VEGETARIAN("Vegetarian"), EGGETARIAN("Eggetarian"),
    VEGAN("Vegan"), PESCATARIAN("Pescatarian"), CUSTOM("Custom");
}

/** Common allergen tags (aligned with Open Food Facts `en:` allergen tags). */
object Allergens {
    val ALL = listOf("gluten", "milk", "eggs", "peanuts", "nuts", "soybeans", "fish", "crustaceans", "molluscs", "sesame-seeds", "mustard", "celery", "lupin", "sulphur-dioxide-and-sulphites")
    fun displayName(tag: String) = tag.replace('-', ' ').replaceFirstChar { it.uppercase() }
}

data class UserProfile(
    val displayName: String,
    val dateOfBirth: LocalDate?,
    val heightCm: Double?,
    val sex: Sex,
    val currentWeightKg: Double?,
    val startWeightKg: Double?,
    val targetWeightKg: Double?,
    val activityLevel: ActivityLevel,
    val goal: Goal,
    /** Desired weekly weight change in kg (positive number; direction comes from [goal]). */
    val weeklyChangeKg: Double,
    val dietaryPreference: DietaryPreference,
    val allergies: Set<String>,
    val restrictions: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun ageOn(date: LocalDate): Int? = dateOfBirth?.let { java.time.Period.between(it, date).years }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class WeightUnit(val symbol: String) { KG("kg"), LB("lb") }
enum class LengthUnit(val symbol: String) { CM("cm"), INCH("in") }

data class UserPreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.CM,
    val defaultSolidUnit: QuantityUnit = QuantityUnit.GRAM,
    val defaultLiquidUnit: QuantityUnit = QuantityUnit.MILLILITRE,
    /** If true, beverages other than plain water also count toward the hydration goal. */
    val countBeveragesAsWater: Boolean = false,
    val waterQuickAmountsMl: List<Int> = listOf(150, 250, 500, 750),
    val dynamicColor: Boolean = false,
    val syncEnabled: Boolean = false,
    val supabaseUrl: String? = null,
    val supabaseAnonKey: String? = null,
    val usdaApiKey: String? = null,
)
