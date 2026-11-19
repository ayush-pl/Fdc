package com.fdc.nutrition.ui.components

import com.fdc.nutrition.domain.calc.BodyUnits
import com.fdc.nutrition.domain.calc.NutrientFormat
import com.fdc.nutrition.domain.model.LengthUnit
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.domain.model.WeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Presentation-only formatting. Values are rounded here, never in storage or calculations. */
object Fmt {
    fun kcal(v: Double?): String = if (v == null) "—" else "%,d".format(v.roundToInt())
    fun grams(v: Double?): String = if (v == null) "—" else NutrientFormat.number(v, if (v < 10) 1 else 0)
    fun nutrient(v: Double?, n: Nutrient, unit: Boolean = true) = NutrientFormat.format(v, n, unit)
    fun num(v: Double, decimals: Int = 1) = NutrientFormat.number(v, decimals)
    fun qty(v: Double) = NutrientFormat.number(v, 2)
    fun ml(v: Double?): String = if (v == null) "—" else "%,d ml".format(v.roundToInt())
    fun litres(v: Double): String = if (v >= 1000) "${num(v / 1000.0, 2)} L" else "${v.roundToInt()} ml"

    fun weight(kg: Double?, unit: WeightUnit, decimals: Int = 1): String = when {
        kg == null -> "—"
        unit == WeightUnit.KG -> "${num(kg, decimals)} kg"
        else -> "${num(BodyUnits.kgToLb(kg), decimals)} lb"
    }
    fun weightValue(kg: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) kg else BodyUnits.kgToLb(kg)
    fun weightToKg(v: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) v else BodyUnits.lbToKg(v)
    fun length(cm: Double?, unit: LengthUnit): String = when {
        cm == null -> "—"
        unit == LengthUnit.CM -> "${num(cm)} cm"
        else -> "${num(BodyUnits.cmToIn(cm))} in"
    }
    fun lengthValue(cm: Double, unit: LengthUnit) = if (unit == LengthUnit.CM) cm else BodyUnits.cmToIn(cm)
    fun lengthToCm(v: Double, unit: LengthUnit) = if (unit == LengthUnit.CM) v else BodyUnits.inToCm(v)

    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM")
    private val longFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    fun day(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> d.format(dayFmt)
    }
    fun short(d: LocalDate): String = d.format(shortFmt)
    fun long(d: LocalDate): String = d.format(longFmt)

    /** Parses user decimal input accepting both "." and "," separators. */
    fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
