package com.fdc.nutrition.domain.model

/** What the reference amount of a nutrient profile refers to. */
enum class NutrientBasis(val symbol: String) {
    /** Nutrients are per 100 g. */
    GRAM("g"),
    /** Nutrients are per 100 ml. */
    MILLILITRE("ml"),
    /** Nutrients are per 1 serving (used for recipes without a recorded weight and quick-add entries). */
    SERVING("serving");
}

enum class UnitDimension { MASS, VOLUME, HOUSEHOLD }

/**
 * Units a user can log a quantity in. Mass and metric volume units have universal conversions.
 * Cup/tablespoon/teaspoon are standard US volume measures — they convert universally to **ml** but
 * never to grams without food-specific data. Pieces, slices, bowls, plates and servings have **no**
 * universal size and are only available through food-specific [ServingSize] definitions.
 */
enum class QuantityUnit(val symbol: String, val displayName: String, val dimension: UnitDimension, val standardFactor: Double?) {
    GRAM("g", "Grams", UnitDimension.MASS, 1.0),
    KILOGRAM("kg", "Kilograms", UnitDimension.MASS, 1000.0),
    OUNCE("oz", "Ounces", UnitDimension.MASS, 28.349523125),
    POUND("lb", "Pounds", UnitDimension.MASS, 453.59237),
    MILLILITRE("ml", "Millilitres", UnitDimension.VOLUME, 1.0),
    LITRE("L", "Litres", UnitDimension.VOLUME, 1000.0),
    FLUID_OUNCE("fl oz", "Fluid ounces (US)", UnitDimension.VOLUME, 29.5735295625),
    CUP("cup", "Cups (US, 236.6 ml)", UnitDimension.VOLUME, 236.5882365),
    TABLESPOON("tbsp", "Tablespoons", UnitDimension.VOLUME, 14.78676478125),
    TEASPOON("tsp", "Teaspoons", UnitDimension.VOLUME, 4.92892159375),
    PIECE("piece", "Pieces", UnitDimension.HOUSEHOLD, null),
    SLICE("slice", "Slices", UnitDimension.HOUSEHOLD, null),
    BOWL("bowl", "Bowls", UnitDimension.HOUSEHOLD, null),
    PLATE("plate", "Plates", UnitDimension.HOUSEHOLD, null),
    SERVING("serving", "Servings", UnitDimension.HOUSEHOLD, null),
    CUSTOM("custom", "Custom serving", UnitDimension.HOUSEHOLD, null);

    /** Only g/kg/oz/lb and ml/L/fl oz are "plain" measures; cup/tbsp/tsp are household volume measures. */
    val isPlainMetricOrImperial: Boolean get() = this in setOf(GRAM, KILOGRAM, OUNCE, POUND, MILLILITRE, LITRE, FLUID_OUNCE)
}

/**
 * How the serving weight/volume was established, so the UI can distinguish raw weight,
 * cooked weight, edible portion and a prepared serving.
 */
enum class PortionKind { AS_LISTED, RAW_WEIGHT, COOKED_WEIGHT, EDIBLE_PORTION, PREPARED_SERVING }

/**
 * A food-specific serving conversion, e.g. "1 medium roti = 40 g" or "1 cup cooked = 158 g".
 * [amount] is expressed in the food's basis unit (g for GRAM basis, ml for MILLILITRE basis,
 * servings for SERVING basis) for **one** unit of [unit].
 */
data class ServingSize(
    val id: String,
    val unit: QuantityUnit,
    val label: String,
    val amount: Double,
    val isDefault: Boolean = false,
    val portionKind: PortionKind = PortionKind.AS_LISTED,
    val source: String? = null,
)
