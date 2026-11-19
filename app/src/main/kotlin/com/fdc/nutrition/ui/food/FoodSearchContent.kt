package com.fdc.nutrition.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fdc.nutrition.data.repository.FoodDraft
import com.fdc.nutrition.data.repository.RecentItem
import com.fdc.nutrition.data.repository.RecipeSummary
import com.fdc.nutrition.data.repository.SavedMeal
import com.fdc.nutrition.domain.calc.Compatibility
import com.fdc.nutrition.domain.model.Food
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.domain.model.PreparationState
import com.fdc.nutrition.ui.components.*

@Composable
fun FoodSearchField(query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search foods, brands or dishes") {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query, onValueChange = onQuery, modifier = modifier.fillMaxWidth().testTag("food_search"),
        placeholder = { Text(placeholder) }, singleLine = true, shape = MaterialTheme.shapes.extraLarge,
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear search") } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit(); focus.clearFocus() }),
    )
}

@Composable
fun FoodRow(food: Food, onClick: () -> Unit, compat: Compatibility? = null, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).testTag("food_${food.id}"),
        headlineContent = { Text(food.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(buildString {
                    food.brand?.let { append(it); append(" · ") }
                    append("${Fmt.kcal(food.nutrientsPer100.energyKcal)} kcal / 100 ${food.basis.symbol}")
                    if (food.preparation == PreparationState.RAW || food.preparation == PreparationState.COOKED) append(" · ${food.preparation.displayName.lowercase()}")
                }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MacroSummaryRow(food.nutrientsPer100.proteinG, food.nutrientsPer100.carbohydrateG, food.nutrientsPer100.fatG)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                QualityBadge(food.dataQuality)
                when (compat) {
                    Compatibility.INCOMPATIBLE -> Pill("Not for your diet", MaterialTheme.colorScheme.error)
                    else -> Unit
                }
                if (food.isFavourite) Icon(Icons.Filled.Favorite, "Favourite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun RecentRow(item: RecentItem, onOpen: () -> Unit, onQuickAdd: (() -> Unit)?) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(item.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${Fmt.qty(item.entry.quantity)} ${item.entry.unitLabel} · ${Fmt.kcal(item.entry.nutrients.energyKcal)} kcal · logged ${item.timesLogged}×") },
        trailingContent = onQuickAdd?.let { { FilledTonalIconButton(onClick = it, modifier = Modifier.testTag("quick_relog_${item.entry.foodId ?: item.entry.recipeId}")) { Icon(Icons.Filled.Add, "Add again with the same amount") } } },
    )
}

@Composable
fun RecipeRow(r: RecipeSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(r.recipe.name) },
        supportingContent = { Text("${Fmt.kcal(r.nutrition.perServing.energyKcal)} kcal per serving · ${Fmt.num(r.recipe.servings)} servings") },
        trailingContent = { QualityBadge(com.fdc.nutrition.domain.model.DataQuality.CALCULATED) },
    )
}

@Composable
fun OnlineResultRow(d: FoodDraft, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Outlined.Public, null) },
        headlineContent = { Text(d.name, maxLines = 2) },
        supportingContent = { Text("${d.brand?.let { "$it · " } ?: ""}${Fmt.kcal(d.nutrientsPer100.energyKcal)} kcal / 100 ${d.basis.symbol} · USDA") },
        trailingContent = { QualityBadge(d.dataQuality) },
    )
}

/**
 * Body of the search screens: results when a query is entered; otherwise tabs for recent, popular,
 * favourites, custom foods, recipes and saved meals, plus category browsing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodSearchBody(
    vm: FoodSearchViewModel,
    s: FoodSearchState,
    onFood: (Food) -> Unit,
    onRecipe: (RecipeSummary) -> Unit,
    onRecent: (RecentItem) -> Unit,
    onOnline: (FoodDraft) -> Unit,
    allowQuickRelog: Boolean,
    header: LazyListScope.() -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        header()
        if (s.query.trim().length >= 2) {
            if (s.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            if (s.recipeResults.isNotEmpty()) {
                item { ListHeader("Your recipes") }
                items(s.recipeResults, key = { "r" + it.recipe.id }) { RecipeRow(it) { onRecipe(it) } }
            }
            item { ListHeader("${s.results.size} foods${s.categoryId?.let { c -> " in ${s.categories.firstOrNull { it.id == c }?.name}" } ?: ""}") }
            items(s.results, key = { it.id }) { f -> FoodRow(f, { onFood(f) }, vm.compatibility(f)) }
            if (!s.searching && s.results.isEmpty() && s.recipeResults.isEmpty()) item {
                EmptyState(Icons.Outlined.SearchOff, "No local matches", "Try a simpler word (e.g. \"dal\"), search online, or create a custom food.")
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    when {
                        s.onlineLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        s.onlineError != null -> Banner(s.onlineError, BannerKind.OFFLINE)
                        s.onlineResults != null -> {
                            ListHeader("Online results (USDA FoodData Central)")
                            if (s.onlineResults.isEmpty()) Text("No online results.")
                        }
                        else -> OutlinedButton(onClick = vm::searchOnline, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.TravelExplore, null); Spacer(Modifier.width(8.dp)); Text("Search USDA online") }
                    }
                }
            }
            s.onlineResults?.let { list -> items(list, key = { "o" + it.sourceRef }) { d -> OnlineResultRow(d) { onOnline(d) } } }
        } else {
            if (s.history.isNotEmpty()) item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::clearHistory) { Text("Clear") }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.history) { h -> AssistChip(onClick = { vm.setQuery(h) }, label = { Text(h) }, leadingIcon = { Icon(Icons.Outlined.History, null, Modifier.size(16.dp)) }) }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FoodTab.entries) { t -> FilterChip(s.tab == t && s.categoryId == null, { vm.setCategory(null); vm.setTab(t) }, { Text(t.label) }, modifier = Modifier.testTag("tab_food_${t.name}")) }
                }
            }
            if (s.categoryId != null) {
                item { ListHeader(s.categories.firstOrNull { it.id == s.categoryId }?.let { "${it.emoji} ${it.name}" } ?: "") }
                items(s.categoryFoods, key = { it.id }) { f -> FoodRow(f, { onFood(f) }, vm.compatibility(f)) }
            } else when (s.tab) {
                FoodTab.RECENT -> {
                    if (s.recent.isEmpty()) item { EmptyState(Icons.Outlined.History, "No recent foods yet", "Foods you log will appear here for one-tap logging.") }
                    if (s.frequent.isNotEmpty()) {
                        item { ListHeader("Most frequent") }
                        items(s.frequent, key = { "f" + it.entry.id }) { r -> RecentRow(r, { onRecent(r) }, if (allowQuickRelog) ({ vm.relog(r) }) else null) }
                    }
                    if (s.recent.isNotEmpty()) item { ListHeader("Recently logged") }
                    items(s.recent, key = { "r" + it.entry.id }) { r -> RecentRow(r, { onRecent(r) }, if (allowQuickRelog) ({ vm.relog(r) }) else null) }
                }
                FoodTab.POPULAR -> items(s.popular, key = { it.id }) { f -> FoodRow(f, { onFood(f) }, vm.compatibility(f)) }
                FoodTab.FAVOURITES -> {
                    if (s.favourites.isEmpty()) item { EmptyState(Icons.Outlined.FavoriteBorder, "No favourites", "Tap the heart on any food to save it here.") }
                    items(s.favourites, key = { it.id }) { f -> FoodRow(f, { onFood(f) }, vm.compatibility(f)) }
                }
                FoodTab.MINE -> {
                    if (s.mine.isEmpty()) item { EmptyState(Icons.Outlined.Edit, "No custom foods", "Create foods with your own nutrition values, or scan a barcode.") }
                    items(s.mine, key = { it.id }) { f -> FoodRow(f, { onFood(f) }, vm.compatibility(f)) }
                }
                FoodTab.RECIPES -> {
                    if (s.recipes.isEmpty()) item { EmptyState(Icons.Outlined.MenuBook, "No recipes", "Build recipes from ingredients in the Recipes section.") }
                    items(s.recipes, key = { it.recipe.id }) { RecipeRow(it) { onRecipe(it) } }
                }
                FoodTab.MEALS -> {
                    if (s.savedMeals.isEmpty()) item { EmptyState(Icons.Outlined.BookmarkBorder, "No saved meals", "In the diary, use a meal's menu → \"Save as favourite meal\".") }
                    items(s.savedMeals, key = { it.id }) { m -> SavedMealRow(m, if (allowQuickRelog) ({ vm.logSavedMeal(m) }) else null) }
                }
            }
            item {
                ListHeader("Browse by category · ${"%,d".format(s.foodCount)} foods offline")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.categories.forEach { c -> FilterChip(s.categoryId == c.id, { vm.setCategory(c.id) }, { Text("${c.emoji} ${c.name}") }) }
                }
            }
        }
    }
}

@Composable
fun SavedMealRow(m: SavedMeal, onLog: (() -> Unit)?) {
    ListItem(
        leadingContent = { Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(m.name) },
        supportingContent = { Text("${m.items.size} items · ${Fmt.kcal(m.kcal)} kcal · ${m.items.joinToString { it.item.name }.take(60)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = onLog?.let { { FilledTonalIconButton(onClick = it) { Icon(Icons.Filled.Add, "Log this meal") } } },
    )
}

@Composable
fun ListHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp))
}
