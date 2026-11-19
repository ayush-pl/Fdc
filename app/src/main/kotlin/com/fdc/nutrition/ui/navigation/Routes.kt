package com.fdc.nutrition.ui.navigation

import kotlinx.serialization.Serializable

// Top-level destinations
@Serializable object HomeRoute
@Serializable object FoodRoute
@Serializable data class DiaryRoute(val date: Long? = null)
@Serializable object NutritionRoute
@Serializable object ProfileRoute

@Serializable object SetupRoute

/** Food search used for logging (mode LOG) or planning (mode PLAN). */
@Serializable data class AddFoodRoute(val date: Long, val mealId: String? = null, val mode: String = MODE_LOG)
@Serializable data class FoodDetailRoute(val foodId: String, val date: Long? = null, val mealId: String? = null, val mode: String = MODE_BROWSE)
@Serializable data class FoodEditorRoute(val foodId: String? = null, val draftKey: String? = null, val barcode: String? = null, val date: Long? = null, val mealId: String? = null)
@Serializable data class ScannerRoute(val date: Long? = null, val mealId: String? = null)
@Serializable data class CompareRoute(val a: String? = null, val b: String? = null)
@Serializable data class EntryEditRoute(val entryId: String)
@Serializable data class QuickAddRoute(val date: Long, val mealId: String, val entryId: String? = null)
@Serializable object CalendarRoute
@Serializable object RecipesRoute
@Serializable data class RecipeEditorRoute(val recipeId: String? = null)
@Serializable data class RecipeDetailRoute(val recipeId: String, val date: Long? = null, val mealId: String? = null, val mode: String = MODE_BROWSE)
@Serializable data class PlannerRoute(val weekStart: Long? = null)
@Serializable data class WaterRoute(val date: Long? = null)
@Serializable object BodyRoute
@Serializable object ProfileEditRoute
@Serializable object TargetsRoute
@Serializable object SettingsRoute
@Serializable object RemindersRoute
@Serializable object DataRoute
@Serializable object SyncRoute
@Serializable object AboutRoute
@Serializable object SavedMealsRoute

const val MODE_BROWSE = "BROWSE"
const val MODE_LOG = "LOG"
const val MODE_PLAN = "PLAN"
