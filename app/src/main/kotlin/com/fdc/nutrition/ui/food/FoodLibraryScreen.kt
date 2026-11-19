package com.fdc.nutrition.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.fdc.nutrition.ui.navigation.*

/** Food tab: browse and manage the food database. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLibraryScreen(nav: NavHostController, vm: FoodSearchViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Food") }, actions = {
                IconButton(onClick = { nav.navigate(ScannerRoute()) }) { Icon(Icons.Outlined.QrCodeScanner, "Scan barcode") }
                IconButton(onClick = { nav.navigate(CompareRoute()) }) { Icon(Icons.Outlined.CompareArrows, "Compare foods") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate(FoodEditorRoute()) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Custom food") }, modifier = Modifier.testTag("create_custom_food"))
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { pad ->
        Column(Modifier.padding(pad)) {
            FoodSearchField(s.query, vm::setQuery, vm::commitSearch, Modifier.padding(horizontal = 16.dp))
            FoodSearchBody(
                vm, s,
                onFood = { vm.commitSearch(); nav.navigate(FoodDetailRoute(it.id)) },
                onRecipe = { nav.navigate(RecipeDetailRoute(it.recipe.id)) },
                onRecent = { r -> r.entry.foodId?.let { nav.navigate(FoodDetailRoute(it)) } ?: r.entry.recipeId?.let { nav.navigate(RecipeDetailRoute(it)) } },
                onOnline = { nav.navigate(FoodEditorRoute(draftKey = vm.stageDraft(it))) },
                allowQuickRelog = false,
            )
        }
    }
}
