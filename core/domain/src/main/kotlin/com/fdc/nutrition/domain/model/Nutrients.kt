package com.fdc.nutrition.domain.model

/**
 * An immutable set of nutrient amounts. A nutrient that is absent from the map is **unknown**,
 * which is deliberately different from a known value of zero. Arithmetic preserves that
 * distinction: scaling keeps unknowns unknown, and [plus] only yields a value when both
 * operands know it (use [com.fdc.nutrition.domain.calc.NutrientAggregator] to sum many items
 * while tracking coverage).
 */
class Nutrients(values: Map<Nutrient, Double> = emptyMap()) {
    val values: Map<Nutrient, Double> = values.filterValues { it.isFinite() }

    operator fun get(n: Nutrient): Double? = values[n]
    fun has(n: Nutrient): Boolean = values.containsKey(n)
    val energyKcal: Double? get() = values[Nutrient.ENERGY]
    val proteinG: Double? get() = values[Nutrient.PROTEIN]
    val carbohydrateG: Double? get() = values[Nutrient.CARBOHYDRATE]
    val fatG: Double? get() = values[Nutrient.FAT]

    fun scale(factor: Double): Nutrients {
        require(factor.isFinite() && factor >= 0) { "Scale factor must be a finite non-negative number" }
        return Nutrients(values.mapValues { it.value * factor })
    }

    /** Strict addition: a nutrient is present in the result only if it is present in both. */
    operator fun plus(other: Nutrients): Nutrients =
        Nutrients(values.keys.intersect(other.values.keys).associateWith { values.getValue(it) + other.values.getValue(it) })

    fun with(n: Nutrient, value: Double?): Nutrients =
        Nutrients(if (value == null) values - n else values + (n to value))

    /** Fraction of the tracked nutrient set that has known values (0..1). */
    fun completeness(set: Collection<Nutrient> = Nutrient.entries): Double =
        if (set.isEmpty()) 1.0 else set.count { has(it) }.toDouble() / set.size

    val isEmpty: Boolean get() = values.isEmpty()

    override fun equals(other: Any?): Boolean = other is Nutrients && other.values == values
    override fun hashCode(): Int = values.hashCode()
    override fun toString(): String = "Nutrients($values)"

    companion object {
        val EMPTY = Nutrients()
        fun of(vararg pairs: Pair<Nutrient, Double?>): Nutrients =
            Nutrients(pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap())
    }
}
