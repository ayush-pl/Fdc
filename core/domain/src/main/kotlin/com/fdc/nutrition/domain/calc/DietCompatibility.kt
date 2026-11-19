package com.fdc.nutrition.domain.calc

import com.fdc.nutrition.domain.model.DietClass
import com.fdc.nutrition.domain.model.DietaryPreference
import com.fdc.nutrition.domain.model.Food

enum class Compatibility { COMPATIBLE, INCOMPATIBLE, UNKNOWN }

/**
 * Diet and allergen checks. When the underlying data is incomplete the answer is UNKNOWN — a food is
 * never silently classified as suitable or allergen-free.
 */
object DietCompatibility {
    fun forDiet(food: Food, pref: DietaryPreference): Compatibility = forDiet(food.dietClass, pref)

    fun forDiet(c: DietClass, pref: DietaryPreference): Compatibility {
        if (c == DietClass.UNKNOWN) return if (pref == DietaryPreference.NON_VEGETARIAN || pref == DietaryPreference.CUSTOM) Compatibility.COMPATIBLE else Compatibility.UNKNOWN
        val allowed: Set<DietClass> = when (pref) {
            DietaryPreference.NON_VEGETARIAN, DietaryPreference.CUSTOM -> DietClass.entries.toSet()
            DietaryPreference.VEGAN -> setOf(DietClass.VEGAN)
            DietaryPreference.VEGETARIAN -> setOf(DietClass.VEGAN, DietClass.VEGETARIAN)
            DietaryPreference.EGGETARIAN -> setOf(DietClass.VEGAN, DietClass.VEGETARIAN, DietClass.EGG)
            DietaryPreference.PESCATARIAN -> setOf(DietClass.VEGAN, DietClass.VEGETARIAN, DietClass.EGG, DietClass.PESCATARIAN)
        }
        return if (c in allowed) Compatibility.COMPATIBLE else Compatibility.INCOMPATIBLE
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
}
