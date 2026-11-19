package com.fdc.nutrition.data.db

import com.fdc.nutrition.data.db.entity.*
import com.fdc.nutrition.domain.model.*

fun FoodCategoryEntity.toDomain() = FoodCategory(id, name, sortOrder, emoji)

fun FoodServingEntity.toDomain() = ServingSize(id, unit, label, amount, isDefault, portionKind, source)

fun FoodWithNutrients.toDomain(servings: List<FoodServingEntity> = emptyList(), barcodes: List<String> = emptyList(), favourite: Boolean = false) = Food(
    id = food.id, name = food.name, categoryId = food.categoryId, brand = food.brand, basis = food.basis,
    nutrientsPer100 = nutrients.toDomain(), servings = servings.map { it.toDomain() }, densityGPerMl = food.densityGPerMl,
    source = food.source, sourceRef = food.sourceRef, dataQuality = food.dataQuality, preparation = food.preparation,
    isCustom = food.isCustom, isArchived = food.isArchived, isPopular = food.isPopular, isFavourite = favourite,
    barcodes = barcodes, allergens = food.allergens.splitTags(), allergenInfoKnown = food.allergenInfoKnown,
    dietClass = food.dietClass, notes = food.notes, createdAt = food.createdAt, updatedAt = food.updatedAt,
    sourceUpdatedAt = food.sourceUpdatedAt,
)

fun Food.toEntity(aliases: String? = null, originalName: String? = null, seedVersion: Int? = null) = FoodEntity(
    id = id, name = name.trim(), brand = brand?.trim()?.ifBlank { null }, categoryId = categoryId, basis = basis,
    densityGPerMl = densityGPerMl, source = source, sourceRef = sourceRef, dataQuality = dataQuality, preparation = preparation,
    isCustom = isCustom, isArchived = isArchived, isPopular = isPopular, dietClass = dietClass, allergens = allergens.joinTags(),
    allergenInfoKnown = allergenInfoKnown, aliases = aliases, originalName = originalName, notes = notes, seedVersion = seedVersion,
    createdAt = createdAt, updatedAt = updatedAt, sourceUpdatedAt = sourceUpdatedAt,
)

fun ServingSize.toEntity(foodId: String, sortOrder: Int) = FoodServingEntity(id, foodId, unit, label, amount, isDefault, portionKind, source, sortOrder)

fun String.splitTags(): Set<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
fun Collection<String>.joinTags(): String = map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().joinToString(",")

fun DiaryEntryEntity.toDomain() = DiaryEntry(
    id = id, date = date, mealId = mealId, type = type, foodId = foodId, recipeId = recipeId, name = name, brand = brand,
    quantity = quantity, unit = unit, unitLabel = unitLabel, amountPerUnit = amountPerUnit, basis = basis,
    referenceAmount = referenceAmount, nutrientsPerReference = snapshot.toDomain(), dataQuality = dataQuality,
    preparation = preparation, note = note, plannedSourceKey = plannedSourceKey, loggedAt = loggedAt, createdAt = createdAt, updatedAt = updatedAt,
)

fun DiaryEntry.toEntity() = DiaryEntryEntity(
    id = id, date = date, mealId = mealId, type = type, foodId = foodId, recipeId = recipeId, name = name, brand = brand,
    quantity = quantity, unit = unit, unitLabel = unitLabel, amountPerUnit = amountPerUnit, basis = basis,
    referenceAmount = referenceAmount, snapshot = NutrientColumns.from(nutrientsPerReference), dataQuality = dataQuality,
    preparation = preparation, note = note, plannedSourceKey = plannedSourceKey, loggedAt = loggedAt, createdAt = createdAt, updatedAt = updatedAt,
)

fun MealEntity.toDomain() = Meal(id, name, sortOrder, isDefault, isArchived)

fun PlannedMealEntity.toDomain() = PlannedMeal(
    id = id, date = date, mealId = mealId, foodId = item.foodId, recipeId = item.recipeId, name = item.name, quantity = item.quantity,
    unit = item.unit, unitLabel = item.unitLabel, amountPerUnit = item.amountPerUnit, basis = item.basis, referenceAmount = item.referenceAmount,
    nutrientsPerReference = item.nutrients.toDomain(), recurrenceId = recurrenceId, loggedEntryId = loggedEntryId, note = note,
)

fun PortionColumns.nutrientsConsumed(): Nutrients = nutrients.toDomain().scale(quantity * amountPerUnit / referenceAmount)

fun DiaryEntry.toPortion() = PortionColumns(
    type = type, foodId = foodId, recipeId = recipeId, name = name, brand = brand, quantity = quantity, unit = unit, unitLabel = unitLabel,
    amountPerUnit = amountPerUnit, basis = basis, referenceAmount = referenceAmount, nutrients = NutrientColumns.from(nutrientsPerReference),
    dataQuality = dataQuality,
)

fun NutritionTargetEntity.toDomain() = NutritionTargets(
    id = id, effectiveFrom = effectiveFrom, values = values.toDomain().values, waterMl = waterMl, macroMethod = macroMethod,
    macroSplit = if (proteinPct != null && carbPct != null && fatPct != null) MacroSplit(proteinPct, carbPct, fatPct) else null,
    origin = origin, createdAt = createdAt,
)

fun NutritionTargets.toEntity() = NutritionTargetEntity(
    id = id, effectiveFrom = effectiveFrom, values = NutrientColumns.from(Nutrients(values)), waterMl = waterMl, macroMethod = macroMethod,
    proteinPct = macroSplit?.proteinPct, carbPct = macroSplit?.carbPct, fatPct = macroSplit?.fatPct, origin = origin, createdAt = createdAt,
)

fun WaterEntryEntity.toDomain() = WaterEntry(id, date, amountMl, beverage, loggedAt)
fun WaterEntry.toEntity() = WaterEntryEntity(id, date, amountMl, beverage, loggedAt)
fun BodyMeasurementEntity.toDomain() = BodyMeasurement(id, date, type, customName, value, note, createdAt, updatedAt)
fun BodyMeasurement.toEntity() = BodyMeasurementEntity(id, date, type, customName, value, note, createdAt, updatedAt)

fun UserProfileEntity.toDomain() = UserProfile(
    displayName, dateOfBirth, heightCm, sex, currentWeightKg, startWeightKg, targetWeightKg, activityLevel, goal, weeklyChangeKg,
    dietaryPreference, allergies.splitTags(), restrictions, createdAt, updatedAt,
)
fun UserProfile.toEntity() = UserProfileEntity(
    displayName = displayName.trim(), dateOfBirth = dateOfBirth, heightCm = heightCm, sex = sex, currentWeightKg = currentWeightKg,
    startWeightKg = startWeightKg, targetWeightKg = targetWeightKg, activityLevel = activityLevel, goal = goal, weeklyChangeKg = weeklyChangeKg,
    dietaryPreference = dietaryPreference, allergies = allergies.joinTags(), restrictions = restrictions, createdAt = createdAt, updatedAt = updatedAt,
)

fun UserPreferencesEntity?.toDomain(): UserPreferences = if (this == null) UserPreferences() else UserPreferences(
    theme = theme, weightUnit = weightUnit, lengthUnit = lengthUnit, defaultSolidUnit = defaultSolidUnit, defaultLiquidUnit = defaultLiquidUnit,
    countBeveragesAsWater = countBeveragesAsWater,
    waterQuickAmountsMl = waterQuickAmounts.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(150, 250, 500, 750) },
    dynamicColor = dynamicColor, syncEnabled = syncEnabled, supabaseUrl = supabaseUrl, supabaseAnonKey = supabaseAnonKey, usdaApiKey = usdaApiKey,
)
fun UserPreferences.toEntity() = UserPreferencesEntity(
    theme = theme, weightUnit = weightUnit, lengthUnit = lengthUnit, defaultSolidUnit = defaultSolidUnit, defaultLiquidUnit = defaultLiquidUnit,
    countBeveragesAsWater = countBeveragesAsWater, waterQuickAmounts = waterQuickAmountsMl.joinToString(","), dynamicColor = dynamicColor,
    syncEnabled = syncEnabled, supabaseUrl = supabaseUrl, supabaseAnonKey = supabaseAnonKey, usdaApiKey = usdaApiKey,
)
