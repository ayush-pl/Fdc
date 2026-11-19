package com.fdc.nutrition.domain.model

import java.time.Instant

/** Where a food's data came from. */
enum class FoodSource(val displayName: String) {
    USDA_SR_LEGACY("USDA FoodData Central – SR Legacy"),
    USDA_FNDDS("USDA FoodData Central – FNDDS (survey foods)"),
    USDA_FOUNDATION("USDA FoodData Central – Foundation Foods"),
    USDA_FDC_API("USDA FoodData Central (online)"),
    OPEN_FOOD_FACTS("Open Food Facts"),
    USER("Entered by you"),
    RECIPE("Your recipe"),
}

/**
 * How trustworthy a food's nutrient values are. Shown to users so estimates are never presented as
 * verified laboratory measurements.
 */
enum class DataQuality(val displayName: String, val description: String) {
    VERIFIED_DATABASE("Verified database", "Analytical or calculated values from a government reference database (USDA SR Legacy / Foundation)."),
    GENERIC_ESTIMATE("Generic estimate", "A representative recipe-based estimate for a typical preparation (USDA FNDDS). Actual dishes vary."),
    BRANDED_LABEL("Product label", "Values transcribed from a packaged product's nutrition label (Open Food Facts, community-contributed)."),
    USER_ENTERED("User entered", "Values you entered yourself."),
    CALCULATED("Calculated", "Calculated from recipe ingredients."),
}

enum class PreparationState(val displayName: String) {
    RAW("Raw"), COOKED("Cooked"), PREPARED("Prepared dish"), NOT_APPLICABLE("—");
}

data class FoodCategory(val id: String, val name: String, val sortOrder: Int, val emoji: String)

/**
 * Diet classification of a food. "Vegetarian" follows the Indian convention of lacto-vegetarian
 * (no meat, fish or eggs); [EGG] marks foods whose only animal flesh-free exception is egg.
 * [UNKNOWN] is used whenever the source does not let us classify reliably.
 */
enum class DietClass(val displayName: String) {
    VEGAN("Vegan"), VEGETARIAN("Vegetarian"), EGG("Contains egg"), PESCATARIAN("Contains fish/seafood"),
    NON_VEGETARIAN("Contains meat"), UNKNOWN("Diet info unavailable");
}

data class Food(
    val id: String,
    val name: String,
    val categoryId: String,
    val brand: String? = null,
    val basis: NutrientBasis = NutrientBasis.GRAM,
    /** Nutrients per 100 g / 100 ml of [basis]. Unknown nutrients are absent. */
    val nutrientsPer100: Nutrients,
    val servings: List<ServingSize> = emptyList(),
    val densityGPerMl: Double? = null,
    val source: FoodSource,
    val sourceRef: String? = null,
    val dataQuality: DataQuality,
    val preparation: PreparationState = PreparationState.NOT_APPLICABLE,
    val isCustom: Boolean = false,
    val isArchived: Boolean = false,
    val isPopular: Boolean = false,
    val isFavourite: Boolean = false,
    val barcodes: List<String> = emptyList(),
    /** Allergen tags known to be present (e.g. "milk", "gluten"). */
    val allergens: Set<String> = emptySet(),
    /** True when allergen information was actually supplied by the source (even if the set is empty). */
    val allergenInfoKnown: Boolean = false,
    val dietClass: DietClass = DietClass.UNKNOWN,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    /** When the source data was last refreshed (data freshness). */
    val sourceUpdatedAt: Instant? = null,
) {
    val displayName: String get() = if (brand.isNullOrBlank()) name else "$name · $brand"
    val defaultServing: ServingSize? get() = servings.firstOrNull { it.isDefault } ?: servings.firstOrNull()
}
