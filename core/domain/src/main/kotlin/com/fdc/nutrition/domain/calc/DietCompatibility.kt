package com.fdc.nutrition.domain.calc

import com.fdc.nutrition.domain.model.DietaryPreference
import com.fdc.nutrition.domain.model.Food
import com.fdc.nutrition.domain.model.Tristate

enum class Compatibility { COMPATIBLE, INCOMPATIBLE, UNKNOWN }

/**
 * Diet and allergen checks. When the underlying data is incomplete the answer is UNKNOWN — a food is
 * never silently classified as suitable or allergen-free.
 */
object DietCompatibility {
    fun forDiet(food: Food, pref: DietaryPreference): Compatibility = when (pref) {
        DietaryPreference.NON_VEGETARIAN, DietaryPreference.CUSTOM -> Compatibility.COMPATIBLE
        DietaryPreference.VEGETARIAN -> food.vegetarian.toCompat()
        DietaryPreference.VEGAN -> food.vegan.toCompat()
        DietaryPreference.EGGETARIAN -> when {
            food.vegetarian == Tristate.YES -> Compatibility.COMPATIBLE
            food.vegetarian == Tristate.NO && food.containsEgg == Tristate.YES && food.containsFish != Tristate.YES -> Compatibility.UNKNOWN
            food.vegetarian == Tristate.NO -> Compatibility.INCOMPATIBLE
            else -> Compatibility.UNKNOWN
        }
        DietaryPreference.PESCATARIAN -> when {
            food.vegetarian == Tristate.YES -> Compatibility.COMPATIBLE
            food.containsFish == Tristate.YES -> Compatibility.COMPATIBLE
            food.vegetarian == Tristate.NO -> Compatibility.INCOMPATIBLE
            else -> Compatibility.UNKNOWN
        }
    }

    /** Allergens from [userAllergies] known to be present; UNKNOWN when the food has no allergen data. */
    fun allergenStatus(food: Food, userAllergies: Set<String>): Pair<Compatibility, Set<String>> {
        if (userAllergies.isEmpty()) return Compatibility.COMPATIBLE to emptySet()
        val hits = food.allergens.intersect(userAllergies)
        return when {
            hits.isNotEmpty() -> Compatibility.INCOMPATIBLE to hits
            !food.allergenInfoKnown -> Compatibility.UNKNOWN to emptySet()
            else -> Compatibility.COMPATIBLE to emptySet()
        }
    }

    private fun Tristate.toCompat() = when (this) {
        Tristate.YES -> Compatibility.COMPATIBLE; Tristate.NO -> Compatibility.INCOMPATIBLE; Tristate.UNKNOWN -> Compatibility.UNKNOWN
    }
}
