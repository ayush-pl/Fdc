package com.fdc.nutrition.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.fdc.nutrition.ui.components.Fmt
import com.fdc.nutrition.ui.components.MealChips
import com.fdc.nutrition.ui.navigation.*

/** Fast logging (mode LOG) or planning (mode PLAN): pick meal, then search or tap a recent item. */
@Composable
fun AddFoodScreen(nav: NavHostController, vm: FoodSearchViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val meal by vm.mealId.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    val dateArg = vm.date.toArg()
    val planning = vm.mode == MODE_PLAN
    Scaffold(
        topBar = { BackTopBar(if (planning) "Plan for ${Fmt.day(vm.date)}" else "Add to ${Fmt.day(vm.date)}", nav) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { pad ->
        Column(Modifier.padding(pad).imePadding()) {
            MealChips(s.meals, meal, vm::setMeal, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            FoodSearchField(s.query, vm::setQuery, vm::commitSearch, Modifier.padding(horizontal = 16.dp))
            if (!planning) Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { nav.navigate(ScannerRoute(dateArg, meal)) }, label = { Text("Scan") }, leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, null) })
                AssistChip(onClick = { nav.navigate(QuickAddRoute(dateArg, meal)) }, label = { Text("Quick calories") }, leadingIcon = { Icon(Icons.Outlined.Bolt, null) }, modifier = Modifier.testTag("quick_add_chip"))
                AssistChip(onClick = { nav.navigate(FoodEditorRoute(date = dateArg, mealId = meal)) }, label = { Text("Custom") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
            }
            FoodSearchBody(
                vm, s,
                onFood = { vm.commitSearch(); nav.navigate(FoodDetailRoute(it.id, dateArg, meal, vm.mode)) },
                onRecipe = { nav.navigate(RecipeDetailRoute(it.recipe.id, dateArg, meal, vm.mode)) },
                onRecent = { r -> r.entry.foodId?.let { nav.navigate(FoodDetailRoute(it, dateArg, meal, vm.mode)) } ?: r.entry.recipeId?.let { nav.navigate(RecipeDetailRoute(it, dateArg, meal, vm.mode)) } },
                onOnline = { nav.navigate(FoodEditorRoute(draftKey = vm.stageDraft(it), date = dateArg, mealId = meal)) },
                allowQuickRelog = !planning,
            )
        }
    }
}
