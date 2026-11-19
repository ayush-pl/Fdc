package com.fdc.nutrition.ui.recipes

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.fdc.nutrition.data.repository.FoodRepository
import com.fdc.nutrition.data.repository.IngredientDraft
import com.fdc.nutrition.data.repository.RecipeDraft
import com.fdc.nutrition.data.repository.RecipeRepository
import com.fdc.nutrition.domain.calc.RecipeCalculator
import com.fdc.nutrition.domain.calc.RecipeNutrition
import com.fdc.nutrition.domain.calc.ServingOption
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class EditableIngredient(val id: String?, val food: Food, val quantity: Double, val option: ServingOption, val note: String? = null) {
    fun toDomain(order: Int) = RecipeIngredient(id ?: "", food.id, food.displayName, quantity, option.unit, option.label, option.amountPerUnit, food.basis, food.nutrientsPer100, food.preparation, food.densityGPerMl, order, note)
}

@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val recipes: RecipeRepository,
    val foods: FoodRepository,
    @ApplicationContext private val context: Context,
) : BaseViewModel() {
    private val recipeId = savedState.toRoute<RecipeEditorRoute>().recipeId
    var name by mutableStateOf(""); var description by mutableStateOf(""); var instructions by mutableStateOf("")
    var servings by mutableStateOf("2"); var finalWeight by mutableStateOf(""); var imageUri by mutableStateOf<String?>(null)
    var favourite by mutableStateOf(false)
    val ingredients = mutableStateListOf<EditableIngredient>()
    var errors by mutableStateOf<List<String>>(emptyList())
    var saving by mutableStateOf(false)
    val isEdit = recipeId != null

    init {
        recipeId?.let { id -> viewModelScope.launch {
            val r = recipes.getRecipe(id) ?: return@launch
            name = r.name; description = r.description ?: ""; instructions = r.instructions ?: ""; servings = Fmt.num(r.servings, 2)
            finalWeight = r.finalWeightGrams?.let { Fmt.num(it) } ?: ""; imageUri = r.imageUri; favourite = r.isFavourite
            val foodMap = foods.foodsForIds(r.ingredients.map { it.foodId })
            r.ingredients.forEach { i -> foodMap[i.foodId]?.let { f -> ingredients += EditableIngredient(i.id, f, i.quantity, ServingOption("saved", i.unit, i.unitLabel, i.amountPerUnit, isFoodSpecific = true), i.note) } }
        } }
    }

    fun nutrition(): RecipeNutrition? {
        val s = Fmt.parse(servings)?.takeIf { it > 0 } ?: return null
        if (ingredients.isEmpty()) return null
        return RecipeCalculator.calculate(ingredients.mapIndexed { i, e -> e.toDomain(i) }, s, Fmt.parse(finalWeight))
    }

    fun setImage(uri: Uri) = launchSafe {
        val dest = withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "recipe_images").apply { mkdirs() }
            val f = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            f
        }
        imageUri = Uri.fromFile(dest).toString()
    }

    fun save(onDone: (String) -> Unit) {
        if (saving) return
        val s = Fmt.parse(servings) ?: run { errors = listOf("Servings must be a number"); return }
        val fw = finalWeight.takeIf { it.isNotBlank() }?.let { Fmt.parse(it) ?: run { errors = listOf("Final weight must be a number"); return } }
        val draft = RecipeDraft(recipeId, name, description, instructions, s, fw, imageUri, favourite, ingredients.map { IngredientDraft(it.id, it.food.id, it.quantity, it.option, it.note) })
        errors = recipes.validate(draft)
        if (errors.isNotEmpty()) return
        saving = true
        viewModelScope.launch {
            try { val id = recipes.save(draft); message("Recipe saved"); onDone(id) } catch (e: Exception) { errors = listOf(e.message ?: "Couldn't save") } finally { saving = false }
        }
    }
}

@Composable
fun RecipeEditorScreen(nav: NavHostController, vm: RecipeEditorViewModel = hiltViewModel()) {
    CollectMessages(vm.messages)
    var picking by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::setImage) }
    Scaffold(topBar = { BackTopBar(if (vm.isEdit) "Edit recipe" else "New recipe", nav) }, contentWindowInsets = WindowInsets.statusBars,
        bottomBar = { Button(onClick = { vm.save { id -> nav.navigate(RecipeDetailRoute(id)) { popUpTo<RecipeEditorRoute> { inclusive = true } } } }, enabled = !vm.saving,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp).testTag("save_recipe")) { Text("Save recipe") } }) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Recipe name *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("recipe_name"))
            OutlinedTextField(vm.description, { vm.description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(vm.servings, { vm.servings = it }, "Servings *", Modifier.weight(1f).testTag("recipe_servings"))
                NumberField(vm.finalWeight, { vm.finalWeight = it }, "Cooked weight", Modifier.weight(1f).testTag("recipe_final_weight"), suffix = "g", supporting = "Optional, weigh the pot")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                vm.imageUri?.let { AsyncImage(it, "Recipe photo", Modifier.size(72.dp)) }
                TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(6.dp)); Text(if (vm.imageUri == null) "Add photo" else "Change photo") }
                Spacer(Modifier.weight(1f))
                FilterChip(vm.favourite, { vm.favourite = !vm.favourite }, { Text("Favourite") })
            }
            Text("Ingredients", style = MaterialTheme.typography.titleMedium)
            vm.ingredients.forEachIndexed { i, ing ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ing.food.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                            Text("${Fmt.qty(ing.quantity)} ${ing.option.label} = ${Fmt.num(ing.option.baseAmount(ing.quantity))} ${ing.food.basis.symbol}${if (ing.food.preparation != PreparationState.NOT_APPLICABLE) " · ${ing.food.preparation.displayName.lowercase()}" else ""} · ${Fmt.kcal(ing.food.nutrientsPer100.energyKcal?.let { it * ing.option.baseAmount(ing.quantity) / 100 })} kcal",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.ingredients.removeAt(i) }) { Icon(Icons.Filled.Delete, "Remove ingredient") }
                    }
                }
            }
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().testTag("add_ingredient")) { Icon(Icons.Filled.Add, null); Text("Add ingredient") }
            vm.nutrition()?.let { n ->
                AppCard {
                    Text("Calculated nutrition", style = MaterialTheme.typography.titleSmall)
                    Text("Total: ${Fmt.kcal(n.total.energyKcal)} kcal · P ${Fmt.grams(n.total.proteinG)} g · C ${Fmt.grams(n.total.carbohydrateG)} g · F ${Fmt.grams(n.total.fatG)} g", modifier = Modifier.testTag("recipe_total"))
                    Text("Per serving: ${Fmt.kcal(n.perServing.energyKcal)} kcal · P ${Fmt.grams(n.perServing.proteinG)} g · C ${Fmt.grams(n.perServing.carbohydrateG)} g · F ${Fmt.grams(n.perServing.fatG)} g", modifier = Modifier.testTag("recipe_per_serving"))
                    n.per100g?.let { Text("Per 100 g: ${Fmt.kcal(it.energyKcal)} kcal (based on ${if (n.usesRecordedFinalWeight) "cooked weight" else "raw ingredient weight"} of ${Fmt.num(n.effectiveWeightG ?: 0.0)} g)") }
                    if (!n.usesRecordedFinalWeight) Text("Tip: weigh the finished dish and enter the cooked weight for accurate per-100 g values (water is lost or gained in cooking).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(vm.instructions, { vm.instructions = it }, label = { Text("Preparation instructions") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            vm.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (picking) FoodPickerDialog(
        title = "Add ingredient", searchFoods = { vm.foods.search(it, limit = 60) }, loadFood = { vm.foods.getFood(it) }, onDismiss = { picking = false },
        onPickFood = { f, q, o -> vm.ingredients += EditableIngredient(null, f, q, o) },
    )
}
