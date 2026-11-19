package com.fdc.nutrition.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.domain.model.NutrientGroup
import com.fdc.nutrition.domain.model.Nutrients
import com.fdc.nutrition.ui.components.Fmt

/** Full nutrient listing. Unknown values show "—" (not 0) and the completeness is stated. */
@Composable
fun NutrientTable(n: Nutrients, modifier: Modifier = Modifier, targets: Map<Nutrient, Double>? = null) {
    Column(modifier) {
        val known = Nutrient.entries.count { n.has(it) }
        Text("$known of ${Nutrient.entries.size} nutrients available", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        NutrientGroup.entries.forEach { g ->
            val list = Nutrient.entries.filter { it.group == g }
            Text(when (g) { NutrientGroup.ENERGY -> "Energy"; NutrientGroup.MACRO -> "Macronutrients"; NutrientGroup.MACRO_DETAIL -> "Details"; NutrientGroup.MINERAL -> "Minerals"; NutrientGroup.VITAMIN -> "Vitamins" },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            list.forEach { nutrient ->
                val v = n[nutrient]
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).testTag("nutrient_${nutrient.key}")) {
                    Text(nutrient.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        fontWeight = if (g == NutrientGroup.ENERGY || g == NutrientGroup.MACRO) FontWeight.SemiBold else FontWeight.Normal)
                    Text(Fmt.nutrient(v, nutrient), style = MaterialTheme.typography.bodyMedium, color = if (v == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    targets?.get(nutrient)?.let { t -> if (v != null && t > 0) Text("  ${(v / t * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp)) else Spacer(Modifier.width(44.dp)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}
