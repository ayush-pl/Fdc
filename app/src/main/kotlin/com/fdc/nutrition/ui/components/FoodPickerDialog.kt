package com.fdc.nutrition.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fdc.nutrition.data.repository.RecipeSummary
import com.fdc.nutrition.domain.calc.NutritionCalculator
import com.fdc.nutrition.domain.calc.ServingConverter
import com.fdc.nutrition.domain.calc.ServingOption
import com.fdc.nutrition.domain.model.Food
import com.fdc.nutrition.ui.food.FoodRow
import com.fdc.nutrition.ui.food.FoodSearchField
import com.fdc.nutrition.ui.food.ListHeader
import com.fdc.nutrition.ui.food.RecipeRow
import kotlinx.coroutines.delay

/**
 * Full-screen picker: search foods (and optionally recipes), then choose an amount.
 * Used by the recipe builder (ingredients) and the meal planner.
 */
@Composable
fun FoodPickerDialog(
    title: String,
    searchFoods: suspend (String) -> List<Food>,
    loadFood: suspend (String) -> Food?,
    onDismiss: () -> Unit,
    onPickFood: (Food, Double, ServingOption) -> Unit,
    searchRecipes: (suspend (String) -> List<RecipeSummary>)? = null,
    onPickRecipe: ((RecipeSummary, Double) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var foods by remember { mutableStateOf<List<Food>>(emptyList()) }
    var recipes by remember { mutableStateOf<List<RecipeSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<Food?>(null) }
    var selectedRecipe by remember { mutableStateOf<RecipeSummary?>(null) }
    var qty by remember { mutableStateOf("100") }
    var option by remember { mutableStateOf<ServingOption?>(null) }
    var pendingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        delay(250)
        foods = if (query.trim().length >= 2) searchFoods(query) else emptyList()
        recipes = if (query.trim().length >= 2 && searchRecipes != null) searchRecipes(query) else emptyList()
    }
    LaunchedEffect(pendingId) {
        pendingId?.let { id -> loadFood(id)?.let { f -> selected = f; val d = ServingConverter.defaultOption(f); option = d; qty = if (d.isFoodSpecific) "1" else "100" } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().imePadding().padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
                }
                header?.let { it(); Spacer(Modifier.height(8.dp)) }
                val f = selected; val r = selectedRecipe
                if (f != null) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(qty, { qty = it }, "Quantity", Modifier.weight(0.4f).testTag("picker_qty"))
                        UnitDropdown(ServingConverter.optionsFor(f), option, { option = it }, Modifier.weight(0.6f))
                    }
                    val q = Fmt.parse(qty)?.takeIf { it > 0 }
                    val o = option
                    if (q != null && o != null) {
                        val n = NutritionCalculator.forFood(f, o, q)
                        Text("${Fmt.num(o.baseAmount(q))} ${f.basis.symbol} · ${Fmt.kcal(n.energyKcal)} kcal · P ${Fmt.grams(n.proteinG)} C ${Fmt.grams(n.carbohydrateG)} F ${Fmt.grams(n.fatG)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { selected = null; pendingId = null }) { Text("Back") }
                        Button(onClick = { if (q != null && o != null) { onPickFood(f, q, o); onDismiss() } }, enabled = q != null && o != null, modifier = Modifier.weight(1f).testTag("picker_confirm")) { Text("Add") }
                    }
                } else if (r != null && onPickRecipe != null) {
                    Text(r.recipe.name, style = MaterialTheme.typography.titleMedium)
                    NumberField(qty, { qty = it }, "Servings", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { selectedRecipe = null }) { Text("Back") }
                        Button(onClick = { Fmt.parse(qty)?.takeIf { it > 0 }?.let { onPickRecipe(r, it); onDismiss() } }, modifier = Modifier.weight(1f)) { Text("Add") }
                    }
                } else {
                    FoodSearchField(query, { query = it }, {}, placeholder = "Search ingredients")
                    LazyColumn {
                        if (recipes.isNotEmpty()) { item { ListHeader("Recipes") }; items(recipes, key = { "r" + it.recipe.id }) { rr -> RecipeRow(rr) { selectedRecipe = rr; qty = "1" } } }
                        if (foods.isNotEmpty()) item { ListHeader("Foods") }
                        items(foods, key = { it.id }) { food -> FoodRow(food, { pendingId = food.id }) }
                        if (query.length >= 2 && foods.isEmpty() && recipes.isEmpty()) item { Text("No matches", Modifier.padding(16.dp)) }
                    }
                }
            }
        }
    }
}
