package com.fdc.nutrition.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.fdc.nutrition.data.repository.TargetProposal
import com.fdc.nutrition.domain.calc.TargetCalculator
import com.fdc.nutrition.domain.model.MacroMethod
import com.fdc.nutrition.domain.model.MacroSplit
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.domain.model.NutritionTargets
import com.fdc.nutrition.ui.components.Fmt
import com.fdc.nutrition.ui.components.NumberField

/** Editable state for daily targets, shared by onboarding and the Targets screen. */
class TargetFormState {
    var calories by mutableStateOf("")
    var method by mutableStateOf(MacroMethod.PERCENTAGE)
    var proteinPct by mutableStateOf("25"); var carbPct by mutableStateOf("50"); var fatPct by mutableStateOf("25")
    var proteinG by mutableStateOf(""); var carbG by mutableStateOf(""); var fatG by mutableStateOf("")
    var fibre by mutableStateOf(""); var sugar by mutableStateOf(""); var addedSugar by mutableStateOf(""); var sodium by mutableStateOf("")
    var satFat by mutableStateOf(""); var water by mutableStateOf("")
    val micros = mutableStateMapOf<Nutrient, String>()

    val kcal: Double? get() = Fmt.parse(calories)
    val split: MacroSplit? get() = run {
        val p = Fmt.parse(proteinPct); val c = Fmt.parse(carbPct); val f = Fmt.parse(fatPct)
        if (p != null && c != null && f != null) MacroSplit(p, c, f) else null
    }

    /** Grams shown for the current method (derived from % when percentage-based so totals stay consistent). */
    fun gramsFor(n: Nutrient): Double? {
        if (method == MacroMethod.PERCENTAGE) {
            val k = kcal ?: return null; val s = split?.takeIf { it.isValid } ?: return null
            val m = TargetCalculator.macrosFromSplit(k, s)
            return when (n) { Nutrient.PROTEIN -> m.proteinG; Nutrient.CARBOHYDRATE -> m.carbG; Nutrient.FAT -> m.fatG; else -> null }
        }
        return Fmt.parse(when (n) { Nutrient.PROTEIN -> proteinG; Nutrient.CARBOHYDRATE -> carbG; else -> fatG })
    }

    fun load(t: NutritionTargets) {
        calories = t[Nutrient.ENERGY]?.let { Fmt.num(it, 0) } ?: ""
        method = t.macroMethod
        t.macroSplit?.let { proteinPct = Fmt.num(it.proteinPct); carbPct = Fmt.num(it.carbPct); fatPct = Fmt.num(it.fatPct) }
        proteinG = t[Nutrient.PROTEIN]?.let { Fmt.num(it, 0) } ?: ""; carbG = t[Nutrient.CARBOHYDRATE]?.let { Fmt.num(it, 0) } ?: ""; fatG = t[Nutrient.FAT]?.let { Fmt.num(it, 0) } ?: ""
        loadOthers(t.values, t.waterMl)
    }

    fun apply(p: TargetProposal) {
        calories = Fmt.num(p.estimate.targetKcal, 0)
        proteinPct = Fmt.num(p.split.proteinPct); carbPct = Fmt.num(p.split.carbPct); fatPct = Fmt.num(p.split.fatPct)
        proteinG = Fmt.num(p.targets[Nutrient.PROTEIN] ?: 0.0, 0); carbG = Fmt.num(p.targets[Nutrient.CARBOHYDRATE] ?: 0.0, 0); fatG = Fmt.num(p.targets[Nutrient.FAT] ?: 0.0, 0)
        method = MacroMethod.PERCENTAGE
        loadOthers(p.targets, p.waterMl)
    }

    private fun loadOthers(v: Map<Nutrient, Double>, waterMl: Double) {
        fibre = v[Nutrient.FIBRE]?.let { Fmt.num(it, 0) } ?: ""
        sugar = v[Nutrient.SUGARS]?.let { Fmt.num(it, 0) } ?: ""
        addedSugar = v[Nutrient.ADDED_SUGARS]?.let { Fmt.num(it, 0) } ?: ""
        sodium = v[Nutrient.SODIUM]?.let { Fmt.num(it, 0) } ?: ""
        satFat = v[Nutrient.SATURATED_FAT]?.let { Fmt.num(it, 0) } ?: ""
        water = Fmt.num(waterMl, 0)
        micros.clear()
        Nutrient.MICROS.filter { it != Nutrient.SODIUM }.forEach { n -> v[n]?.let { micros[n] = Fmt.num(it, 1) } }
    }

    fun values(): Map<Nutrient, Double> = buildMap {
        kcal?.let { put(Nutrient.ENERGY, it) }
        listOf(Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT).forEach { n -> gramsFor(n)?.let { put(n, it) } }
        Fmt.parse(fibre)?.let { put(Nutrient.FIBRE, it) }
        Fmt.parse(sugar)?.let { put(Nutrient.SUGARS, it) }
        Fmt.parse(addedSugar)?.let { put(Nutrient.ADDED_SUGARS, it) }
        Fmt.parse(sodium)?.let { put(Nutrient.SODIUM, it) }
        Fmt.parse(satFat)?.let { put(Nutrient.SATURATED_FAT, it) }
        micros.forEach { (n, s) -> Fmt.parse(s)?.let { put(n, it) } }
    }
    fun waterMl(): Double = Fmt.parse(water) ?: 0.0
}

@Composable
fun TargetFormFields(state: TargetFormState, showMicros: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(state.calories, { state.calories = it }, "Daily calories", Modifier.fillMaxWidth().testTag("target_kcal"), suffix = "kcal")
        Text("Macro targets", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(MacroMethod.PERCENTAGE to "% of calories", MacroMethod.GRAMS to "Grams").forEachIndexed { i, (m, l) ->
                SegmentedButton(selected = state.method == m || (m == MacroMethod.GRAMS && state.method == MacroMethod.CUSTOM), onClick = {
                    if (m == MacroMethod.GRAMS && state.method == MacroMethod.PERCENTAGE) {
                        state.proteinG = state.gramsFor(Nutrient.PROTEIN)?.let { Fmt.num(it, 0) } ?: state.proteinG
                        state.carbG = state.gramsFor(Nutrient.CARBOHYDRATE)?.let { Fmt.num(it, 0) } ?: state.carbG
                        state.fatG = state.gramsFor(Nutrient.FAT)?.let { Fmt.num(it, 0) } ?: state.fatG
                    }
                    state.method = m
                }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
            }
        }
        if (state.method == MacroMethod.PERCENTAGE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(state.proteinPct, { state.proteinPct = it }, "Protein", Modifier.weight(1f).testTag("pct_protein"), suffix = "%", supporting = state.gramsFor(Nutrient.PROTEIN)?.let { "${Fmt.num(it, 0)} g" })
                NumberField(state.carbPct, { state.carbPct = it }, "Carbs", Modifier.weight(1f), suffix = "%", supporting = state.gramsFor(Nutrient.CARBOHYDRATE)?.let { "${Fmt.num(it, 0)} g" })
                NumberField(state.fatPct, { state.fatPct = it }, "Fat", Modifier.weight(1f), suffix = "%", supporting = state.gramsFor(Nutrient.FAT)?.let { "${Fmt.num(it, 0)} g" })
            }
            val total = state.split?.total
            if (total != null && kotlin.math.abs(total - 100) > 0.01) Text("Percentages add up to ${Fmt.num(total)}% — they must total 100%.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(state.proteinG, { state.proteinG = it }, "Protein", Modifier.weight(1f).testTag("g_protein"), suffix = "g")
                NumberField(state.carbG, { state.carbG = it }, "Carbs", Modifier.weight(1f).testTag("g_carbs"), suffix = "g")
                NumberField(state.fatG, { state.fatG = it }, "Fat", Modifier.weight(1f).testTag("g_fat"), suffix = "g")
            }
            val k = state.kcal
            val p = state.gramsFor(Nutrient.PROTEIN); val c = state.gramsFor(Nutrient.CARBOHYDRATE); val f = state.gramsFor(Nutrient.FAT)
            if (k != null && p != null && c != null && f != null) {
                val macroKcal = p * 4 + c * 4 + f * 9
                Text("Macros provide ≈ ${Fmt.kcal(macroKcal)} kcal (${if (macroKcal >= k) "+" else ""}${Fmt.kcal(macroKcal - k)} vs calorie target).",
                    style = MaterialTheme.typography.bodySmall, color = if (kotlin.math.abs(macroKcal - k) > k * 0.1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(state.fibre, { state.fibre = it }, "Fibre", Modifier.weight(1f), suffix = "g")
            NumberField(state.water, { state.water = it }, "Water", Modifier.weight(1f), suffix = "ml")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(state.addedSugar, { state.addedSugar = it }, "Added sugar limit", Modifier.weight(1f), suffix = "g")
            NumberField(state.sugar, { state.sugar = it }, "Total sugar limit", Modifier.weight(1f), suffix = "g", supporting = "Optional")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(state.sodium, { state.sodium = it }, "Sodium limit", Modifier.weight(1f), suffix = "mg")
            NumberField(state.satFat, { state.satFat = it }, "Sat. fat limit", Modifier.weight(1f), suffix = "g")
        }
        if (showMicros) {
            Text("Vitamins & minerals", style = MaterialTheme.typography.titleSmall)
            Nutrient.MICROS.filter { it != Nutrient.SODIUM }.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { n -> NumberField(state.micros[n] ?: "", { state.micros[n] = it }, n.displayName, Modifier.weight(1f), suffix = n.unit.symbol) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
