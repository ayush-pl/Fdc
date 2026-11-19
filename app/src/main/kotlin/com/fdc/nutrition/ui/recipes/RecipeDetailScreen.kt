package com.fdc.nutrition.ui.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.fdc.nutrition.data.repository.DiaryRepository
import com.fdc.nutrition.data.repository.PlanRepository
import com.fdc.nutrition.data.repository.RecipeRepository
import com.fdc.nutrition.domain.calc.ServingOption
import com.fdc.nutrition.domain.model.Nutrients
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.food.NutrientTable
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.ui.suggestedMeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(savedState: SavedStateHandle, private val recipes: RecipeRepository, private val diary: DiaryRepository, private val plans: PlanRepository) : BaseViewModel() {
    val route = savedState.toRoute<RecipeDetailRoute>()
    val recipe = recipes.observeRecipe(route.recipeId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val meals = diary.observeMeals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var mealId by mutableStateOf(route.mealId ?: suggestedMeal())
    var date by mutableStateOf(route.date?.let(LocalDate::ofEpochDay) ?: LocalDate.now())
    var quantity by mutableStateOf("1")
    var options by mutableStateOf<List<ServingOption>>(emptyList())
    var option by mutableStateOf<ServingOption?>(null)
    var saving by mutableStateOf(false)

    init { viewModelScope.launch { recipe.collect { if (it != null && it.recipe.ingredients.isNotEmpty()) runCatching { diary.recipeLogBasis(route.recipeId) }.onSuccess { b -> options = b.options; if (option == null || options.none { o -> o.key == option?.key }) option = b.options.first() } } } }

    fun preview(): Nutrients? {
        val r = recipe.value ?: return null
        val q = Fmt.parse(quantity)?.takeIf { it > 0 } ?: return null
        val o = option ?: return null
        val n = r.nutrition
        return if (o.unit == com.fdc.nutrition.domain.model.QuantityUnit.SERVING && n.per100g == null) n.perServing.scale(q)
        else n.per100g?.scale(o.baseAmount(q) / 100.0) ?: n.perServing.scale(q)
    }

    fun log(onDone: () -> Unit) {
        val q = Fmt.parse(quantity)?.takeIf { it > 0 } ?: run { message("Enter servings"); return }
        if (saving) return
        saving = true
        launchSafe {
            try {
                if (route.mode == MODE_PLAN) plans.planRecipe(date, mealId, route.recipeId, q, option) else diary.logRecipe(date, mealId, route.recipeId, q, option)
                message(if (route.mode == MODE_PLAN) "Added to plan" else "Logged ${Fmt.qty(q)} ${option?.label ?: "serving"}")
                onDone()
            } finally { saving = false }
        }
    }
    fun toggleFavourite() = launchSafe { recipe.value?.let { recipes.setFavourite(it.recipe.id, !it.recipe.isFavourite) } }
    fun duplicate(onDone: (String) -> Unit) = launchSafe { onDone(recipes.duplicate(route.recipeId)) }
    fun archive(onDone: () -> Unit) = launchSafe { val deleted = recipes.deleteOrArchive(route.recipeId); message(if (deleted) "Recipe deleted" else "Recipe archived (it's used in your diary or plans)"); onDone() }
    fun unarchive() = launchSafe { recipes.setArchived(route.recipeId, false) }
}

@Composable
fun RecipeDetailScreen(nav: NavHostController, vm: RecipeDetailViewModel = hiltViewModel()) {
    val summary by vm.recipe.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var per by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            BackTopBar(summary?.recipe?.name?.take(26) ?: "Recipe", nav) {
                summary?.let { s ->
                    IconButton(onClick = vm::toggleFavourite) { Icon(if (s.recipe.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Edit") }, { menu = false; nav.navigate(RecipeEditorRoute(s.recipe.id)) })
                        DropdownMenuItem({ Text("Duplicate") }, { menu = false; vm.duplicate { nav.navigate(RecipeEditorRoute(it)) } })
                        if (s.recipe.isArchived) DropdownMenuItem({ Text("Restore") }, { menu = false; vm.unarchive() })
                        else DropdownMenuItem({ Text("Delete / archive") }, { menu = false; confirm = true })
                    }
                }
            }
        },
        bottomBar = { Button(onClick = { vm.log { nav.popBackStack() } }, enabled = !vm.saving && summary?.recipe?.ingredients?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp).testTag("log_recipe")) {
            Text(if (vm.route.mode == MODE_PLAN) "Add to plan" else "Log to ${meals.firstOrNull { it.id == vm.mealId }?.name ?: ""} · ${Fmt.day(vm.date)}") } },
        contentWindowInsets = WindowInsets.statusBars,
    ) { pad ->
        val s = summary ?: run { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val n = s.nutrition
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            s.recipe.imageUri?.let { AsyncImage(it, "Recipe photo", Modifier.fillMaxWidth().height(180.dp)) }
            Text(s.recipe.name, style = MaterialTheme.typography.titleLarge)
            s.recipe.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            AppCard {
                Text("Log", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(vm.quantity, { vm.quantity = it }, "Amount", Modifier.weight(0.4f).testTag("recipe_qty"))
                    UnitDropdown(vm.options, vm.option, { vm.option = it }, Modifier.weight(0.6f))
                }
                MealChips(meals, vm.mealId, { vm.mealId = it })
                Text("${Fmt.kcal(vm.preview()?.energyKcal)} kcal for this amount", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("recipe_log_kcal"))
            }
            AppCard {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Per serving", "Per 100 g", "Whole recipe").forEachIndexed { i, l -> SegmentedButton(per == i, { per = i }, SegmentedButtonDefaults.itemShape(i, 3), enabled = i != 1 || n.per100g != null) { Text(l) } }
                }
                Spacer(Modifier.height(8.dp))
                Text(when (per) { 0 -> "1 of ${Fmt.num(s.recipe.servings)} servings${n.servingWeightG?.let { " (≈${Fmt.num(it, 0)} g)" } ?: ""}"; 1 -> "Based on ${if (n.usesRecordedFinalWeight) "recorded cooked weight" else "raw ingredient weight"} ${Fmt.num(n.effectiveWeightG ?: 0.0)} g"; else -> "Total of all ingredients" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NutrientTable(when (per) { 0 -> n.perServing; 1 -> n.per100g ?: Nutrients.EMPTY; else -> n.total })
                val partial = com.fdc.nutrition.domain.model.Nutrient.entries.filter { n.totals[it].missingCount > 0 && !n.totals[it].isUnknown }
                if (partial.isNotEmpty()) Text("Partial data (some ingredients lack values): ${partial.joinToString { it.displayName }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            AppCard {
                Text("Ingredients", style = MaterialTheme.typography.titleSmall)
                s.recipe.ingredients.forEach { i -> Text("• ${Fmt.qty(i.quantity)} ${i.unitLabel} ${i.foodName}${if (i.preparation != com.fdc.nutrition.domain.model.PreparationState.NOT_APPLICABLE) " (${i.preparation.displayName.lowercase()})" else ""}", style = MaterialTheme.typography.bodyMedium) }
            }
            s.recipe.instructions?.let { AppCard { Text("Instructions", style = MaterialTheme.typography.titleSmall); Text(it) } }
            Banner("Editing this recipe later won't change servings you've already logged.", BannerKind.INFO)
        }
    }
    if (confirm) ConfirmDialog("Remove recipe?", "If it's in your diary or plans it will be archived instead.", "Remove", true, { vm.archive { nav.popBackStack() } }, { confirm = false })
}
