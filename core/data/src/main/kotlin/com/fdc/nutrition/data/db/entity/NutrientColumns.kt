package com.fdc.nutrition.data.db.entity

import androidx.room.ColumnInfo
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.domain.model.Nutrients

/**
 * Nullable nutrient columns embedded in several tables (food values per 100 g/ml, diary snapshots,
 * plan snapshots, targets). NULL means "unknown", never zero.
 */
data class NutrientColumns(
    @ColumnInfo(name = "energy_kcal") val energyKcal: Double? = null,
    @ColumnInfo(name = "protein_g") val proteinG: Double? = null,
    @ColumnInfo(name = "carbohydrate_g") val carbohydrateG: Double? = null,
    @ColumnInfo(name = "fat_g") val fatG: Double? = null,
    @ColumnInfo(name = "saturated_fat_g") val saturatedFatG: Double? = null,
    @ColumnInfo(name = "trans_fat_g") val transFatG: Double? = null,
    @ColumnInfo(name = "fibre_g") val fibreG: Double? = null,
    @ColumnInfo(name = "sugars_g") val sugarsG: Double? = null,
    @ColumnInfo(name = "added_sugars_g") val addedSugarsG: Double? = null,
    @ColumnInfo(name = "cholesterol_mg") val cholesterolMg: Double? = null,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double? = null,
    @ColumnInfo(name = "potassium_mg") val potassiumMg: Double? = null,
    @ColumnInfo(name = "calcium_mg") val calciumMg: Double? = null,
    @ColumnInfo(name = "iron_mg") val ironMg: Double? = null,
    @ColumnInfo(name = "magnesium_mg") val magnesiumMg: Double? = null,
    @ColumnInfo(name = "zinc_mg") val zincMg: Double? = null,
    @ColumnInfo(name = "vitamin_a_ug") val vitaminAUg: Double? = null,
    @ColumnInfo(name = "vitamin_c_mg") val vitaminCMg: Double? = null,
    @ColumnInfo(name = "vitamin_d_ug") val vitaminDUg: Double? = null,
    @ColumnInfo(name = "vitamin_e_mg") val vitaminEMg: Double? = null,
    @ColumnInfo(name = "vitamin_k_ug") val vitaminKUg: Double? = null,
    @ColumnInfo(name = "vitamin_b12_ug") val vitaminB12Ug: Double? = null,
    @ColumnInfo(name = "folate_ug") val folateUg: Double? = null,
) {
    fun toDomain(): Nutrients = Nutrients.of(
        Nutrient.ENERGY to energyKcal, Nutrient.PROTEIN to proteinG, Nutrient.CARBOHYDRATE to carbohydrateG,
        Nutrient.FAT to fatG, Nutrient.SATURATED_FAT to saturatedFatG, Nutrient.TRANS_FAT to transFatG,
        Nutrient.FIBRE to fibreG, Nutrient.SUGARS to sugarsG, Nutrient.ADDED_SUGARS to addedSugarsG,
        Nutrient.CHOLESTEROL to cholesterolMg, Nutrient.SODIUM to sodiumMg, Nutrient.POTASSIUM to potassiumMg,
        Nutrient.CALCIUM to calciumMg, Nutrient.IRON to ironMg, Nutrient.MAGNESIUM to magnesiumMg,
        Nutrient.ZINC to zincMg, Nutrient.VITAMIN_A to vitaminAUg, Nutrient.VITAMIN_C to vitaminCMg,
        Nutrient.VITAMIN_D to vitaminDUg, Nutrient.VITAMIN_E to vitaminEMg, Nutrient.VITAMIN_K to vitaminKUg,
        Nutrient.VITAMIN_B12 to vitaminB12Ug, Nutrient.FOLATE to folateUg,
    )

    companion object {
        fun from(n: Nutrients) = NutrientColumns(
            energyKcal = n[Nutrient.ENERGY], proteinG = n[Nutrient.PROTEIN], carbohydrateG = n[Nutrient.CARBOHYDRATE],
            fatG = n[Nutrient.FAT], saturatedFatG = n[Nutrient.SATURATED_FAT], transFatG = n[Nutrient.TRANS_FAT],
            fibreG = n[Nutrient.FIBRE], sugarsG = n[Nutrient.SUGARS], addedSugarsG = n[Nutrient.ADDED_SUGARS],
            cholesterolMg = n[Nutrient.CHOLESTEROL], sodiumMg = n[Nutrient.SODIUM], potassiumMg = n[Nutrient.POTASSIUM],
            calciumMg = n[Nutrient.CALCIUM], ironMg = n[Nutrient.IRON], magnesiumMg = n[Nutrient.MAGNESIUM],
            zincMg = n[Nutrient.ZINC], vitaminAUg = n[Nutrient.VITAMIN_A], vitaminCMg = n[Nutrient.VITAMIN_C],
            vitaminDUg = n[Nutrient.VITAMIN_D], vitaminEMg = n[Nutrient.VITAMIN_E], vitaminKUg = n[Nutrient.VITAMIN_K],
            vitaminB12Ug = n[Nutrient.VITAMIN_B12], folateUg = n[Nutrient.FOLATE],
        )
    }
}
