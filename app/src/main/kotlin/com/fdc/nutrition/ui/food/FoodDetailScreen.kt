package com.fdc.nutrition.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.fdc.nutrition.domain.calc.Compatibility
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.ui.theme.LocalMacroPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodDetailScreen(nav: NavHostController, vm: FoodDetailViewModel = hiltViewModel()) {
    val food by vm.food.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    var menu by remember { mutableStateOf(false) }
    var addServing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val palette = LocalMacroPalette.current

    Scaffold(
        topBar = {
            BackTopBar(food?.name?.take(28) ?: "Food", nav) {
                food?.let { f ->
                    IconButton(onClick = vm::toggleFavourite, modifier = Modifier.testTag("favourite_toggle")) {
                        Icon(if (f.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (f.isFavourite) "Remove favourite" else "Add favourite", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Compare with…") }, { menu = false; nav.navigate(CompareRoute(f.id)) })
                        DropdownMenuItem({ Text("Add serving size") }, { menu = false; addServing = true })
                        if (f.isCustom) {
                            DropdownMenuItem({ Text("Edit") }, { menu = false; nav.navigate(FoodEditorRoute(foodId = f.id)) })
                            if (f.isArchived) DropdownMenuItem({ Text("Restore from archive") }, { menu = false; vm.unarchive() })
                            else DropdownMenuItem({ Text("Delete / archive") }, { menu = false; confirmDelete = true })
                        } else DropdownMenuItem({ Text("Make editable copy") }, { menu = false; vm.duplicateAsCustom { nav.navigate(FoodEditorRoute(foodId = it)) } })
                    }
                }
            }
        },
        bottomBar = {
            food?.let {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { vm.add { nav.popBackStack() } }, enabled = !vm.saving && vm.parsedQuantity() != null,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp).testTag("add_food_button"),
                    ) {
                        val mealName = meals.firstOrNull { it.id == vm.mealId }?.name ?: ""
                        Text(if (vm.mode == MODE_PLAN) "Plan for $mealName" else "Add to $mealName · ${Fmt.day(vm.date)}")
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { pad ->
        val f = food ?: run { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val n = vm.nutrients(f)
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(f.name, style = MaterialTheme.typography.titleLarge)
                f.brand?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QualityBadge(f.dataQuality)
                    if (f.preparation != PreparationState.NOT_APPLICABLE) Pill(f.preparation.displayName, MaterialTheme.colorScheme.secondary)
                    if (f.isArchived) Pill("Archived", MaterialTheme.colorScheme.error)
                }
            }
            if (f.isArchived) Banner("This food is archived. It's hidden from search but kept for your history.", BannerKind.INFO)
            AppCard {
                Text("Amount", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    NumberField(vm.quantity, { vm.quantity = it }, "Quantity", Modifier.weight(0.4f).testTag("quantity_field"),
                        error = if (vm.quantity.isNotBlank() && vm.parsedQuantity() == null) "Invalid" else null)
                    UnitDropdown(vm.options(f), vm.option, vm::selectOption, Modifier.weight(0.6f))
                }
                vm.option?.let { o ->
                    val q = vm.parsedQuantity()
                    if (q != null) Text(
                        "= ${Fmt.num(o.baseAmount(q))} ${f.basis.symbol}" + when (o.portionKind) {
                            PortionKind.COOKED_WEIGHT -> " (cooked weight)"; PortionKind.RAW_WEIGHT -> " (raw weight)"; PortionKind.EDIBLE_PORTION -> " (edible portion)"; PortionKind.PREPARED_SERVING -> " (as prepared)"; else -> ""
                        } + if (f.preparation == PreparationState.COOKED) " · values are for cooked food" else if (f.preparation == PreparationState.RAW) " · values are for raw food" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("base_amount"),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Meal", style = MaterialTheme.typography.titleSmall)
                MealChips(meals, vm.mealId, { vm.mealId = it })
                if (vm.isBrowse) TextButton(onClick = { pickDate = true }) { Text("Date: ${Fmt.day(vm.date)}") }
            }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${Fmt.kcal(n?.energyKcal)} kcal", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("live_kcal"))
                        Text("for this amount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MacroColumn("Protein", n?.proteinG, palette.protein, "live_protein")
                    MacroColumn("Carbs", n?.carbohydrateG, palette.carbs, "live_carbs")
                    MacroColumn("Fat", n?.fatG, palette.fat, "live_fat")
                }
                if (n != null) {
                    Spacer(Modifier.height(8.dp))
                    MacroSplitBar((n.proteinG ?: 0.0) * 4, (n.carbohydrateG ?: 0.0) * 4, (n.fatG ?: 0.0) * 9, Triple(palette.protein, palette.carbs, palette.fat))
                }
            }
            vm.compatibility(f)?.let { (c, hits) ->
                when (c) {
                    Compatibility.INCOMPATIBLE -> Banner(if (hits.isNotEmpty()) "Contains ${hits.joinToString { Allergens.displayName(it) }} — listed in your allergies." else "Not compatible with your ${vm.profile.value?.dietaryPreference?.displayName?.lowercase()} preference (${f.dietClass.displayName.lowercase()}).", BannerKind.ERROR)
                    Compatibility.UNKNOWN -> Banner("Diet or allergen information is incomplete for this food — check ingredients yourself.", BannerKind.WARNING)
                    Compatibility.COMPATIBLE -> Unit
                }
            }
            AppCard {
                Text("Nutrition for this amount", style = MaterialTheme.typography.titleSmall)
                NutrientTable(n ?: Nutrients.EMPTY)
            }
            AppCard {
                Text("Per 100 ${f.basis.symbol}", style = MaterialTheme.typography.titleSmall)
                Text("${Fmt.kcal(f.nutrientsPer100.energyKcal)} kcal · P ${Fmt.grams(f.nutrientsPer100.proteinG)} g · C ${Fmt.grams(f.nutrientsPer100.carbohydrateG)} g · F ${Fmt.grams(f.nutrientsPer100.fatG)} g", style = MaterialTheme.typography.bodyMedium)
                val userServings = f.servings.filter { it.source == "USER" }
                if (userServings.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp)); Text("Your serving sizes", style = MaterialTheme.typography.labelLarge)
                    userServings.forEach { s -> Row(verticalAlignment = Alignment.CenterVertically) { Text("${s.label} = ${Fmt.num(s.amount)} ${f.basis.symbol}", Modifier.weight(1f)); TextButton({ vm.deleteServing(s.id) }) { Text("Remove") } } }
                }
            }
            AppCard {
                Text("Source", style = MaterialTheme.typography.titleSmall)
                Text(f.source.displayName + (f.sourceRef?.let { if (f.source.name.startsWith("USDA")) " · FDC ID $it" else " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                Text(f.dataQuality.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                f.sourceUpdatedAt?.let { Text("Source data dated ${Fmt.long(it.atZone(java.time.ZoneOffset.UTC).toLocalDate())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Diet: ${f.dietClass.displayName} · Allergens: ${if (!f.allergenInfoKnown) "unknown" else if (f.allergens.isEmpty()) "none declared" else f.allergens.joinToString { Allergens.displayName(it) }}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                f.notes?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (addServing) AddServingDialog(food?.basis ?: NutrientBasis.GRAM, onDismiss = { addServing = false }) { label, amount, unit -> vm.addServing(label, amount, unit) }
    if (confirmDelete) ConfirmDialog("Remove this food?", "If it's used in your diary, recipes or plans it will be archived instead of deleted so your history stays intact.", "Remove", true, { vm.deleteOrArchive { nav.popBackStack() } }, { confirmDelete = false })
    if (pickDate) DatePickerDialogField(vm.date, { vm.date = it }, { pickDate = false })
}

@Composable
private fun MacroColumn(label: String, v: Double?, color: androidx.compose.ui.graphics.Color, tag: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
        Text(if (v == null) "—" else "${Fmt.grams(v)} g", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.testTag(tag))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AddServingDialog(basis: NutrientBasis, onDismiss: () -> Unit, onSave: (String, Double, QuantityUnit) -> Unit) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(QuantityUnit.BOWL) }
    val units = listOf(QuantityUnit.BOWL, QuantityUnit.PLATE, QuantityUnit.PIECE, QuantityUnit.SLICE, QuantityUnit.CUP, QuantityUnit.TABLESPOON, QuantityUnit.TEASPOON, QuantityUnit.SERVING, QuantityUnit.CUSTOM)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add serving size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weigh your own bowl, piece or spoon once and save it — sizes differ between households.", style = MaterialTheme.typography.bodySmall)
                DropdownField("Type", units, unit, { it.displayName }, { unit = it })
                OutlinedTextField(label, { label = it }, label = { Text("Name, e.g. \"my steel katori\"") }, singleLine = true)
                NumberField(amount, { amount = it }, "Weighs", suffix = basis.symbol)
            }
        },
        confirmButton = {
            TextButton(onClick = { Fmt.parse(amount)?.let { a -> onSave(label.ifBlank { "1 ${unit.symbol}" }, a, unit); onDismiss() } }, enabled = Fmt.parse(amount)?.let { it > 0 } == true) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
