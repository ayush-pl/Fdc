package com.fdc.nutrition.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fdc.nutrition.domain.calc.NutrientTotal
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.domain.model.TargetKind
import com.fdc.nutrition.ui.theme.LocalMacroPalette

/** Circular progress ring; values over target draw a second lap in the warning colour. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    stroke: Dp = 14.dp,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    overColor: Color = LocalMacroPalette.current.over,
    center: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 2f), tween(700), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = this.size.minDimension - sw
            val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * animated.coerceAtMost(1f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
            if (animated > 1f) drawArc(overColor, -90f, 360f * (animated - 1f).coerceAtMost(1f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
fun CalorieRing(consumed: Double, target: Double?, modifier: Modifier = Modifier, size: Dp = 170.dp) {
    val palette = LocalMacroPalette.current
    val remaining = target?.let { it - consumed }
    val fraction = if (target != null && target > 0) (consumed / target).toFloat() else 0f
    ProgressRing(
        fraction, palette.calories, modifier.semantics { contentDescription = "Calories ${Fmt.kcal(consumed)} of ${Fmt.kcal(target)}" }, size = size,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (remaining != null) {
                Text(Fmt.kcal(kotlin.math.abs(remaining)), style = MaterialTheme.typography.headlineMedium)
                Text(if (remaining >= 0) "kcal left" else "kcal over", style = MaterialTheme.typography.labelMedium,
                    color = if (remaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant else palette.over)
            } else {
                Text(Fmt.kcal(consumed), style = MaterialTheme.typography.headlineMedium)
                Text("kcal eaten", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LinearBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

/**
 * A nutrient's progress toward its target. Shows coverage when some entries lacked data so
 * a partial total is never mistaken for a complete one.
 */
@Composable
fun NutrientProgressRow(nutrient: Nutrient, total: NutrientTotal?, target: Double?, color: Color, modifier: Modifier = Modifier, compact: Boolean = false) {
    val value = total?.valueOrNull
    val over = value != null && target != null && value > target
    val barColor = when {
        nutrient.targetKind == TargetKind.LIMIT && over -> LocalMacroPalette.current.over
        else -> color
    }
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(nutrient.displayName, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                buildString {
                    append(Fmt.nutrient(value, nutrient, unit = target == null))
                    if (target != null) append(" / ${Fmt.nutrient(target, nutrient)}")
                },
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearBar(if (value != null && target != null && target > 0) (value / target).toFloat() else 0f, barColor, height = if (compact) 6.dp else 8.dp)
        val sub = buildList {
            if (value != null && target != null) {
                val rem = target - value
                add(when (nutrient.targetKind) {
                    TargetKind.LIMIT -> if (rem >= 0) "${Fmt.nutrient(rem, nutrient)} under limit" else "${Fmt.nutrient(-rem, nutrient)} over limit"
                    else -> if (rem >= 0) "${Fmt.nutrient(rem, nutrient)} remaining" else "${Fmt.nutrient(-rem, nutrient)} over"
                })
                add("${(value / target * 100).toInt()}%")
            }
            if (total != null && total.missingCount > 0) add(if (total.isUnknown) "no data" else "data for ${total.knownCount}/${total.itemCount} items")
        }
        if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
            color = if (total != null && total.missingCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MacroSummaryRow(protein: Double?, carbs: Double?, fat: Double?, modifier: Modifier = Modifier) {
    val p = LocalMacroPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        MacroDot("P", protein, p.protein); MacroDot("C", carbs, p.carbs); MacroDot("F", fat, p.fat)
    }
}

@Composable
private fun MacroDot(label: String, v: Double?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(3.dp))
        Text("$label ${Fmt.grams(v)}${if (v != null) "g" else ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
