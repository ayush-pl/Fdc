package com.fdc.nutrition.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodEditorScreen(nav: NavHostController, vm: FoodEditorViewModel = hiltViewModel()) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    var showAll by remember { mutableStateOf(false) }
    val onSaved: (String) -> Unit = { id ->
        val r = vm.route
        if (r.date != null && r.mealId != null) nav.navigate(FoodDetailRoute(id, r.date, r.mealId, MODE_LOG)) { popUpTo<FoodEditorRoute> { inclusive = true } }
        else if (vm.isEdit) nav.popBackStack()
        else nav.navigate(FoodDetailRoute(id)) { popUpTo<FoodEditorRoute> { inclusive = true } }
    }
    Scaffold(
        topBar = { BackTopBar(if (vm.isEdit) "Edit food" else if (vm.isImport) "Review product" else "New custom food", nav) },
        bottomBar = {
            Button(onClick = { vm.save(onSaved = onSaved) }, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp).testTag("save_food")) { Text("Save food") }
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            vm.reviewNote?.let { Banner(it, BannerKind.INFO) }
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Food name *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("food_name"))
            OutlinedTextField(vm.brand, { vm.brand = it }, label = { Text("Brand (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DropdownField("Category", cats, cats.firstOrNull { it.id == vm.categoryId }, { "${it.emoji} ${it.name}" }, { vm.categoryId = it.id }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField("Measured by", NutrientBasis.entries.filter { it != NutrientBasis.SERVING }, vm.basis, { if (it == NutrientBasis.GRAM) "Weight (g)" else "Volume (ml)" }, { vm.basis = it }, Modifier.weight(1f))
                DropdownField("State", PreparationState.entries, vm.preparation, { if (it == PreparationState.NOT_APPLICABLE) "Not specified" else it.displayName }, { vm.preparation = it }, Modifier.weight(1f))
            }
            OutlinedTextField(vm.barcode, { s -> vm.barcode = s.filter { it.isDigit() } }, label = { Text("Barcode (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Nutrition values", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!vm.perServing, { vm.perServing = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Per 100 ${vm.basis.symbol}") }
                SegmentedButton(vm.perServing, { vm.perServing = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Per serving") }
            }
            if (vm.perServing) NumberField(vm.labelServing, { vm.labelServing = it }, "Serving size on label", Modifier.fillMaxWidth(), suffix = vm.basis.symbol, supporting = "Values will be converted to per 100 ${vm.basis.symbol}")
            Text("Leave a field blank if the label doesn't list it — blank means unknown, not zero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val core = listOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT)
            val shown = if (showAll) Nutrient.entries else core + listOf(Nutrient.FIBRE, Nutrient.SUGARS, Nutrient.SATURATED_FAT, Nutrient.SODIUM)
            shown.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { n ->
                        NumberField(vm.values[n] ?: "", { vm.values[n] = it }, n.displayName + if (n == Nutrient.ENERGY) " *" else "", Modifier.weight(1f).testTag("nutrient_input_${n.key}"), suffix = n.unit.symbol)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer nutrients" else "Add vitamins, minerals & more") }

            Text("Serving sizes", style = MaterialTheme.typography.titleMedium)
            Text("Add sizes like \"1 piece = 35 g\" or \"1 bowl = 180 g\" specific to this food.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.servings.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DropdownField("Unit", QuantityUnit.entries.filter { !it.isPlainMetricOrImperial }, row.unit, { it.symbol }, { row.unit = it }, Modifier.weight(0.9f))
                    OutlinedTextField(row.label, { row.label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.weight(1.2f))
                    NumberField(row.amount, { row.amount = it }, vm.basis.symbol, Modifier.weight(0.8f))
                    IconButton(onClick = { vm.servings.remove(row) }) { Icon(Icons.Filled.Delete, "Remove serving") }
                }
            }
            OutlinedButton(onClick = { vm.servings.add(ServingRow()) }) { Icon(Icons.Filled.Add, null); Text("Add serving size") }

            Text("Diet & allergens", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { DietClass.entries.forEach { d -> FilterChip(vm.dietClass == d, { vm.dietClass = d }, { Text(d.displayName) }) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(vm.allergenKnown, { vm.allergenKnown = it })
                Text("I've checked the allergen information", style = MaterialTheme.typography.bodyMedium)
            }
            if (vm.allergenKnown) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Allergens.ALL.forEach { a -> FilterChip(a in vm.allergens, { if (a in vm.allergens) vm.allergens.remove(a) else vm.allergens.add(a) }, { Text(Allergens.displayName(a)) }) }
            }
            OutlinedTextField(vm.notes, { vm.notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            vm.warnings.forEach { Banner(it, BannerKind.WARNING) }
            vm.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (vm.duplicates.isNotEmpty()) AlertDialog(
        onDismissRequest = { vm.duplicates = emptyList() },
        title = { Text("Similar food exists") },
        text = { Text("You already have: " + vm.duplicates.joinToString { it.displayName } + ". Save a new one anyway?") },
        confirmButton = { TextButton(onClick = { vm.duplicates = emptyList(); vm.save(force = true, onSaved = onSaved) }) { Text("Save anyway") } },
        dismissButton = { TextButton(onClick = { val id = vm.duplicates.first().id; vm.duplicates = emptyList(); nav.navigate(FoodDetailRoute(id, vm.route.date, vm.route.mealId, if (vm.route.date != null) MODE_LOG else MODE_BROWSE)) }) { Text("Use existing") } },
    )
}
