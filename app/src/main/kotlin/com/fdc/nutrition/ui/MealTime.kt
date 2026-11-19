package com.fdc.nutrition.ui

import com.fdc.nutrition.domain.model.Meal
import java.time.LocalTime

/** Suggests the meal slot for "now" so logging needs fewer taps. */
fun suggestedMeal(now: LocalTime = LocalTime.now()): String = when {
    now.isBefore(LocalTime.of(10, 30)) -> Meal.BREAKFAST
    now.isBefore(LocalTime.of(12, 0)) -> Meal.MORNING_SNACK
    now.isBefore(LocalTime.of(15, 30)) -> Meal.LUNCH
    now.isBefore(LocalTime.of(18, 30)) -> Meal.EVENING_SNACK
    else -> Meal.DINNER
}
