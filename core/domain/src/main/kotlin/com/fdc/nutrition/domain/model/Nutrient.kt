package com.fdc.nutrition.domain.model

/** Measurement unit a nutrient amount is expressed in. */
enum class NutrientUnit(val symbol: String) {
    KCAL("kcal"), GRAM("g"), MILLIGRAM("mg"), MICROGRAM("µg");
}

enum class NutrientGroup { ENERGY, MACRO, MACRO_DETAIL, MINERAL, VITAMIN }

/**
 * How a daily target for this nutrient should be interpreted.
 * - [GOAL]: aim to reach the target (e.g. protein, fibre, vitamins).
 * - [LIMIT]: stay at or below the target (e.g. sodium, saturated fat).
 * - [BALANCE]: aim to land close to the target (energy and energy-yielding macros).
 */
enum class TargetKind { GOAL, LIMIT, BALANCE }

/**
 * The full nutrient set tracked by the app. All stored amounts are expressed in [unit]
 * and, for foods, per 100 g or 100 ml of the food's nutrient basis.
 */
enum class Nutrient(
    val key: String,
    val displayName: String,
    val unit: NutrientUnit,
    val group: NutrientGroup,
    val targetKind: TargetKind,
) {
    ENERGY("energy_kcal", "Calories", NutrientUnit.KCAL, NutrientGroup.ENERGY, TargetKind.BALANCE),
    PROTEIN("protein_g", "Protein", NutrientUnit.GRAM, NutrientGroup.MACRO, TargetKind.BALANCE),
    CARBOHYDRATE("carbohydrate_g", "Carbohydrates", NutrientUnit.GRAM, NutrientGroup.MACRO, TargetKind.BALANCE),
    FAT("fat_g", "Fat", NutrientUnit.GRAM, NutrientGroup.MACRO, TargetKind.BALANCE),
    SATURATED_FAT("saturated_fat_g", "Saturated fat", NutrientUnit.GRAM, NutrientGroup.MACRO_DETAIL, TargetKind.LIMIT),
    TRANS_FAT("trans_fat_g", "Trans fat", NutrientUnit.GRAM, NutrientGroup.MACRO_DETAIL, TargetKind.LIMIT),
    FIBRE("fibre_g", "Fibre", NutrientUnit.GRAM, NutrientGroup.MACRO_DETAIL, TargetKind.GOAL),
    SUGARS("sugars_g", "Total sugars", NutrientUnit.GRAM, NutrientGroup.MACRO_DETAIL, TargetKind.LIMIT),
    ADDED_SUGARS("added_sugars_g", "Added sugars", NutrientUnit.GRAM, NutrientGroup.MACRO_DETAIL, TargetKind.LIMIT),
    CHOLESTEROL("cholesterol_mg", "Cholesterol", NutrientUnit.MILLIGRAM, NutrientGroup.MACRO_DETAIL, TargetKind.LIMIT),
    SODIUM("sodium_mg", "Sodium", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, TargetKind.LIMIT),
    POTASSIUM("potassium_mg", "Potassium", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, TargetKind.GOAL),
    CALCIUM("calcium_mg", "Calcium", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, TargetKind.GOAL),
    IRON("iron_mg", "Iron", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, TargetKind.GOAL),
    MAGNESIUM("magnesium_mg", "Magnesium", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, TargetKind.GOAL),
    ZINC("zinc_mg", "Zinc", NutrientUnit.MILLIGRAM, NutrientGroup.MINERAL, TargetKind.GOAL),
    VITAMIN_A("vitamin_a_ug", "Vitamin A (RAE)", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, TargetKind.GOAL),
    VITAMIN_C("vitamin_c_mg", "Vitamin C", NutrientUnit.MILLIGRAM, NutrientGroup.VITAMIN, TargetKind.GOAL),
    VITAMIN_D("vitamin_d_ug", "Vitamin D", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, TargetKind.GOAL),
    VITAMIN_E("vitamin_e_mg", "Vitamin E", NutrientUnit.MILLIGRAM, NutrientGroup.VITAMIN, TargetKind.GOAL),
    VITAMIN_K("vitamin_k_ug", "Vitamin K", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, TargetKind.GOAL),
    VITAMIN_B12("vitamin_b12_ug", "Vitamin B12", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, TargetKind.GOAL),
    FOLATE("folate_ug", "Folate (DFE)", NutrientUnit.MICROGRAM, NutrientGroup.VITAMIN, TargetKind.GOAL);

    companion object {
        val MACROS = listOf(ENERGY, PROTEIN, CARBOHYDRATE, FAT)
        val MICROS = entries.filter { it.group == NutrientGroup.MINERAL || it.group == NutrientGroup.VITAMIN }
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String): Nutrient? = byKey[key]
    }
}
