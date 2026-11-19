package com.fdc.nutrition.ui.food

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.repository.*
import com.fdc.nutrition.domain.calc.*
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.Fmt
import com.fdc.nutrition.ui.navigation.FoodDetailRoute
import com.fdc.nutrition.ui.navigation.MODE_BROWSE
import com.fdc.nutrition.ui.navigation.MODE_PLAN
import com.fdc.nutrition.ui.suggestedMeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class FoodDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    private val plans: PlanRepository,
    private val db: AppDatabase,
    profiles: ProfileRepository,
) : BaseViewModel() {
    private val route = savedState.toRoute<FoodDetailRoute>()
    val mode = route.mode
    var date by mutableStateOf(route.date?.let(LocalDate::ofEpochDay) ?: LocalDate.now())
    var mealId by mutableStateOf(route.mealId ?: suggestedMeal())
    var quantity by mutableStateOf("")
    var option by mutableStateOf<ServingOption?>(null)
    var saving by mutableStateOf(false)
    var done by mutableStateOf(false)
    private var initialised = false

    val food: StateFlow<Food?> = foods.observeFood(route.foodId).onEach { f -> if (f != null && !initialised) init(f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val meals = diary.observeMeals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val profile = profiles.observeProfile().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private suspend fun init(f: Food) {
        initialised = true
        val opts = ServingConverter.optionsFor(f)
        val last = db.diaryDao().lastForFood(f.id)
        val lastOpt = last?.let { l -> opts.firstOrNull { it.unit == l.unit && kotlin.math.abs(it.amountPerUnit - l.amountPerUnit) < 1e-6 } }
        if (lastOpt != null) { option = lastOpt; quantity = Fmt.qty(last.quantity) }
        else {
            val def = ServingConverter.defaultOption(f)
            option = def
            quantity = if (def.isFoodSpecific) "1" else "100"
        }
    }

    fun options(f: Food) = ServingConverter.optionsFor(f)

    fun selectOption(o: ServingOption) {
        val prev = option
        option = o
        // Keep the same amount when switching between plain units (e.g. 150 g → 0.15 kg).
        val q = Fmt.parse(quantity)
        if (prev != null && q != null && !prev.isFoodSpecific && !o.isFoodSpecific) quantity = Fmt.qty(q * prev.amountPerUnit / o.amountPerUnit)
        else if (o.isFoodSpecific) quantity = "1"
    }

    fun parsedQuantity(): Double? = Fmt.parse(quantity)?.takeIf { it > 0 }

    /** Live nutrition for the current quantity. */
    fun nutrients(f: Food): Nutrients? {
        val q = parsedQuantity() ?: return null
        val o = option ?: return null
        return NutritionCalculator.forFood(f, o, q)
    }

    fun compatibility(f: Food): Pair<Compatibility, Set<String>>? {
        val p = profile.value ?: return null
        val diet = DietCompatibility.forDiet(f, p.dietaryPreference)
        val (allergen, hits) = DietCompatibility.allergenStatus(f, p.allergies)
        val worst = listOf(diet, allergen).let { if (Compatibility.INCOMPATIBLE in it) Compatibility.INCOMPATIBLE else if (Compatibility.UNKNOWN in it) Compatibility.UNKNOWN else Compatibility.COMPATIBLE }
        return worst to hits
    }

    fun add(onAdded: () -> Unit) {
        val q = parsedQuantity() ?: run { message("Enter a quantity greater than zero"); return }
        val o = option ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                if (mode == MODE_PLAN) plans.planFood(date, mealId, route.foodId, q, o)
                else diary.logFood(date, mealId, route.foodId, q, o)
                val mealName = meals.value.firstOrNull { it.id == mealId }?.name ?: ""
                message(if (mode == MODE_PLAN) "Planned for $mealName" else "Added to $mealName")
                done = true
                onAdded()
            } catch (e: Exception) { message(e.message ?: "Couldn't save") } finally { saving = false }
        }
    }

    fun toggleFavourite() = launchSafe { val f = food.value ?: return@launchSafe; foods.setFavourite(f.id, !f.isFavourite) }

    fun addServing(label: String, amount: Double, unit: QuantityUnit) = launchSafe {
        foods.addServing(route.foodId, unit, label, amount)
        message("Serving size saved")
    }

    fun deleteServing(id: String) = launchSafe { foods.deleteServing(route.foodId, id) }

    fun duplicateAsCustom(onCreated: (String) -> Unit) = launchSafe { onCreated(foods.duplicateAsCustom(route.foodId)) }

    fun deleteOrArchive(onDone: () -> Unit) = launchSafe {
        val deleted = foods.deleteOrArchive(route.foodId)
        message(if (deleted) "Food deleted" else "Food archived — it stays in your history")
        onDone()
    }

    fun unarchive() = launchSafe { foods.archive(route.foodId, false); message("Food restored") }

    val isBrowse get() = mode == MODE_BROWSE
}
