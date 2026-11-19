package com.fdc.nutrition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A chart point. `value == null` means "no data" and is drawn as a gap, never as zero. */
data class ChartPoint(val label: String, val value: Double?)

private fun niceBounds(values: List<Double>, target: Double?, zeroBased: Boolean): Pair<Double, Double> {
    val all = values + listOfNotNull(target)
    if (all.isEmpty()) return 0.0 to 1.0
    var lo = if (zeroBased) 0.0 else all.min()
    var hi = all.max()
    if (hi - lo < 1e-9) { hi += 1.0; lo = if (zeroBased) 0.0 else lo - 1.0 }
    val pad = (hi - lo) * 0.08
    return (if (zeroBased) lo else lo - pad) to hi + pad
}

@Composable
fun ChartFrame(title: String?, selected: String?, empty: Boolean, modifier: Modifier, height: Dp, content: @Composable BoxScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null || selected != null) Row(verticalAlignment = Alignment.CenterVertically) {
            title?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)) }
            selected?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            if (empty) Text("No data in this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) else content()
        }
    }
}

@Composable
private fun AxisLabels(points: List<ChartPoint>) {
    if (points.isEmpty()) return
    Row(Modifier.fillMaxWidth()) {
        val idx = listOf(0, points.size / 2, points.size - 1).distinct()
        idx.forEachIndexed { i, p ->
            Text(points[p].label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f), textAlign = when (i) { 0 -> androidx.compose.ui.text.style.TextAlign.Start; idx.lastIndex -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Center })
        }
    }
}

@Composable
fun LineChart(
    points: List<ChartPoint>, color: Color, modifier: Modifier = Modifier, title: String? = null, target: Double? = null,
    valueFormat: (Double) -> String = { Fmt.num(it) }, height: Dp = 160.dp, zeroBased: Boolean = false,
) {
    val values = points.mapNotNull { it.value }
    var sel by remember(points) { mutableStateOf<Int?>(null) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    val desc = "${title ?: "Chart"}: ${values.size} data points" + (values.lastOrNull()?.let { ", latest ${valueFormat(it)}" } ?: "")
    ChartFrame(title, sel?.let { i -> points[i].value?.let { "${points[i].label}: ${valueFormat(it)}" } ?: "${points[i].label}: no data" }, values.isEmpty(), modifier, height) {
        val (lo, hi) = niceBounds(values, target, zeroBased)
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = desc }.pointerInput(points) {
            detectTapGestures { o -> if (points.size > 1) sel = ((o.x / size.width) * (points.size - 1)).toInt().coerceIn(0, points.lastIndex) }
        }) {
            val w = size.width; val h = size.height
            fun x(i: Int) = if (points.size == 1) w / 2 else i * w / (points.size - 1)
            fun y(v: Double) = (h - ((v - lo) / (hi - lo) * h)).toFloat()
            repeat(4) { g -> val gy = h * g / 3f; drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 1f) }
            target?.let { t -> drawLine(targetColor, Offset(0f, y(t)), Offset(w, y(t)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))) }
            // Segments: only connect consecutive known points, leaving gaps for missing days.
            var path: Path? = null
            val fill = Path()
            points.forEachIndexed { i, p ->
                val v = p.value
                if (v == null) { path?.let { drawPath(it, color, style = Stroke(5f, cap = StrokeCap.Round)) }; path = null }
                else {
                    val px = x(i); val py = y(v)
                    if (path == null) path = Path().apply { moveTo(px, py) } else path!!.lineTo(px, py)
                }
            }
            path?.let { drawPath(it, color, style = Stroke(5f, cap = StrokeCap.Round)) }
            points.forEachIndexed { i, p -> p.value?.let { drawCircle(color, if (sel == i) 9f else 5f, Offset(x(i), y(it))) } }
            sel?.let { i -> drawLine(targetColor.copy(alpha = 0.4f), Offset(x(i), 0f), Offset(x(i), h), 2f) }
            if (values.size == 1 && points.size > 1) Unit
            fill.reset()
        }
        Column(Modifier.align(Alignment.TopStart)) {
            Text(valueFormat(hi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(valueFormat(lo), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.BottomStart))
    }
    AxisLabels(points)
}

@Composable
fun BarChart(
    points: List<ChartPoint>, color: Color, modifier: Modifier = Modifier, title: String? = null, target: Double? = null,
    valueFormat: (Double) -> String = { Fmt.num(it, 0) }, height: Dp = 160.dp, overColor: Color? = null,
) {
    val values = points.mapNotNull { it.value }
    var sel by remember(points) { mutableStateOf<Int?>(null) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    val desc = "${title ?: "Bar chart"}: ${values.size} of ${points.size} periods with data"
    ChartFrame(title, sel?.let { i -> points[i].value?.let { "${points[i].label}: ${valueFormat(it)}" } ?: "${points[i].label}: not logged" }, values.isEmpty(), modifier, height) {
        val (_, hi) = niceBounds(values, target, true)
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = desc }.pointerInput(points) {
            detectTapGestures { o -> sel = ((o.x / size.width) * points.size).toInt().coerceIn(0, points.lastIndex) }
        }) {
            val n = points.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = (slot * 0.62f).coerceAtMost(48f)
            points.forEachIndexed { i, p ->
                val left = i * slot + (slot - bw) / 2
                val v = p.value
                if (v == null) {
                    // Unlogged: a faint stub so gaps are visible but clearly not "zero intake".
                    drawRoundRect(track, Offset(left, size.height - 6f), Size(bw, 6f), CornerRadius(3f))
                } else {
                    val bh = (v / hi * size.height).toFloat().coerceAtLeast(3f)
                    val c = if (overColor != null && target != null && v > target * 1.1) overColor else color
                    drawRoundRect(if (sel == null || sel == i) c else c.copy(alpha = 0.45f), Offset(left, size.height - bh), Size(bw, bh), CornerRadius(8f))
                }
            }
            target?.let { t -> val ty = (size.height - t / hi * size.height).toFloat(); drawLine(targetColor, Offset(0f, ty), Offset(size.width, ty), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))) }
        }
        Text(valueFormat(hi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.TopStart))
    }
    AxisLabels(points)
}

/** Horizontal stacked bar of macro energy split. */
@Composable
fun MacroSplitBar(proteinKcal: Double, carbKcal: Double, fatKcal: Double, colors: Triple<Color, Color, Color>, modifier: Modifier = Modifier) {
    val total = (proteinKcal + carbKcal + fatKcal).takeIf { it > 0 } ?: return
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        var x = 0f
        listOf(proteinKcal to colors.first, carbKcal to colors.second, fatKcal to colors.third).forEach { (v, c) ->
            val w = (v / total * size.width).toFloat()
            drawRect(c, Offset(x, 0f), Size(w, size.height)); x += w
        }
    }
}
