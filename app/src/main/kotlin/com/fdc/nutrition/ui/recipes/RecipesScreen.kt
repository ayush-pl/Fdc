package com.fdc.nutrition.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.fdc.nutrition.data.repository.RecipeRepository
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipesViewModel @Inject constructor(recipes: RecipeRepository) : BaseViewModel() {
    val showArchived = MutableStateFlow(false)
    val recipes = showArchived.flatMapLatest { recipes.observeRecipes(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun RecipesScreen(nav: NavHostController, vm: RecipesViewModel = hiltViewModel()) {
    val list by vm.recipes.collectAsStateWithLifecycle()
    val archived by vm.showArchived.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackTopBar("Recipes", nav) { FilterChip(archived, { vm.showArchived.value = !archived }, { Text("Archived") }) } },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { nav.navigate(RecipeEditorRoute()) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New recipe") }, modifier = Modifier.testTag("new_recipe")) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { pad ->
        val l = list ?: run { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (l.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.MenuBook, if (archived) "No archived recipes" else "No recipes yet", "Combine ingredients and the app calculates nutrition per serving and per 100 g.") }
            items(l, key = { it.recipe.id }) { r ->
                AppCard(onClick = { nav.navigate(RecipeDetailRoute(r.recipe.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        r.recipe.imageUri?.let { AsyncImage(it, null, Modifier.size(56.dp).padding(end = 12.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(r.recipe.name, style = MaterialTheme.typography.titleMedium)
                            Text("${r.recipe.ingredients.size} ingredients · ${Fmt.num(r.recipe.servings)} servings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${Fmt.kcal(r.nutrition.perServing.energyKcal)} kcal / serving", style = MaterialTheme.typography.labelLarge)
                        }
                        if (r.recipe.isFavourite) Icon(Icons.Filled.Favorite, "Favourite", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
