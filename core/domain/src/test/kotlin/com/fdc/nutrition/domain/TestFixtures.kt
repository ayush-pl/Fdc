package com.fdc.nutrition.domain

import com.fdc.nutrition.domain.model.*
import java.time.Instant
import java.time.LocalDate

object Fx {
    val NOW: Instant = Instant.parse("2026-01-01T08:00:00Z")
    val rice = Food(
        id = "rice", name = "Rice, white, cooked", categoryId = "rice", basis = NutrientBasis.GRAM,
        nutrientsPer100 = Nutrients.of(Nutrient.ENERGY to 130.0, Nutrient.PROTEIN to 2.7, Nutrient.CARBOHYDRATE to 28.0, Nutrient.FAT to 0.3),
        servings = listOf(ServingSize("cup", QuantityUnit.CUP, "1 cup, cooked", 158.0, isDefault = false, portionKind = PortionKind.COOKED_WEIGHT)),
        source = FoodSource.USDA_SR_LEGACY, dataQuality = DataQuality.VERIFIED_DATABASE, preparation = PreparationState.COOKED, createdAt = NOW,
    )
    val milk = Food(
        id = "milk", name = "Milk", categoryId = "milk", basis = NutrientBasis.MILLILITRE,
        nutrientsPer100 = Nutrients.of(Nutrient.ENERGY to 61.0, Nutrient.PROTEIN to 3.2, Nutrient.CALCIUM to 113.0),
        densityGPerMl = 1.03, source = FoodSource.USER, dataQuality = DataQuality.USER_ENTERED, createdAt = NOW,
    )

    fun entry(
        date: LocalDate, meal: String = Meal.LUNCH, name: String = "Rice", grams: Double = 100.0,
        per100: Nutrients = rice.nutrientsPer100, id: String = java.util.UUID.randomUUID().toString(),
    ) = DiaryEntry(
        id = id, date = date, mealId = meal, type = EntryType.FOOD, foodId = "f-$name", recipeId = null, name = name, brand = null,
        quantity = grams, unit = QuantityUnit.GRAM, unitLabel = "g", amountPerUnit = 1.0, basis = NutrientBasis.GRAM,
        referenceAmount = 100.0, nutrientsPerReference = per100, dataQuality = DataQuality.VERIFIED_DATABASE,
        loggedAt = NOW, createdAt = NOW, updatedAt = NOW,
    )

    fun target(from: LocalDate, kcal: Double, protein: Double = 150.0) = NutritionTargets(
        id = "t-$from", effectiveFrom = from,
        values = mapOf(Nutrient.ENERGY to kcal, Nutrient.PROTEIN to protein, Nutrient.CARBOHYDRATE to 250.0, Nutrient.FAT to 65.0, Nutrient.FIBRE to 30.0, Nutrient.SODIUM to 2300.0),
        waterMl = 2500.0, macroMethod = MacroMethod.GRAMS, macroSplit = null, origin = TargetOrigin.CUSTOM, createdAt = NOW,
    )
}
