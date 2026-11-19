package com.fdc.nutrition.ui.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.fdc.nutrition.data.repository.*
import com.fdc.nutrition.domain.calc.DietCompatibility
import com.fdc.nutrition.domain.calc.Compatibility
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.navigation.AddFoodRoute
import com.fdc.nutrition.ui.navigation.MODE_BROWSE
import com.fdc.nutrition.ui.navigation.MODE_LOG
import com.fdc.nutrition.ui.suggestedMeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

enum class FoodTab(val label: String) { RECENT("Recent"), POPULAR("Popular"), FAVOURITES("Favourites"), MINE("My foods"), RECIPES("Recipes"), MEALS("Saved meals") }

data class FoodSearchState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Food> = emptyList(),
    val recipeResults: List<RecipeSummary> = emptyList(),
    val tab: FoodTab = FoodTab.RECENT,
    val categoryId: String? = null,
    val categories: List<FoodCategory> = emptyList(),
    val categoryFoods: List<Food> = emptyList(),
    val recent: List<RecentItem> = emptyList(),
    val frequent: List<RecentItem> = emptyList(),
    val popular: List<Food> = emptyList(),
    val favourites: List<Food> = emptyList(),
    val mine: List<Food> = emptyList(),
    val recipes: List<RecipeSummary> = emptyList(),
    val savedMeals: List<SavedMeal> = emptyList(),
    val history: List<String> = emptyList(),
    val meals: List<Meal> = emptyList(),
    val profile: UserProfile? = null,
    val onlineResults: List<FoodDraft>? = null,
    val onlineError: String? = null,
    val onlineLoading: Boolean = false,
    val foodCount: Int = 0,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val foods: FoodRepository,
    private val recipes: RecipeRepository,
    private val diary: DiaryRepository,
    private val imports: ProductImportRepository,
    private val drafts: PendingDraftStore,
    profiles: ProfileRepository,
) : BaseViewModel() {
    private val route: AddFoodRoute? = runCatching { savedState.toRoute<AddFoodRoute>() }.getOrNull()
    val mode: String = route?.mode ?: MODE_BROWSE
    val date: LocalDate = route?.date?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
    private val _meal = MutableStateFlow(route?.mealId ?: suggestedMeal())
    val mealId: StateFlow<String> = _meal

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val tab = MutableStateFlow(if (mode == MODE_BROWSE) FoodTab.POPULAR else FoodTab.RECENT)
    private val online = MutableStateFlow(Triple<List<FoodDraft>?, String?, Boolean>(null, null, false))

    private val searchResults = combine(query.debounce(250).map { it.trim() }.distinctUntilChanged(), category) { q, c -> q to c }
        .flatMapLatest { (q, c) ->
            flow {
                if (q.length < 2) emit(Triple(false, emptyList<Food>(), emptyList<RecipeSummary>()))
                else { emit(Triple(true, emptyList(), emptyList())); emit(Triple(false, foods.search(q, c), if (c == null) recipes.search(q) else emptyList())) }
            }
        }

    private val categoryFoods = category.flatMapLatest { c -> if (c == null) flowOf(emptyList()) else foods.observeByCategory(c) }

    private val lists = combine(diary.observeRecent(25), diary.observeFrequent(10), foods.observePopular(), foods.observeFavourites(), foods.observeCustom()) { r, f, p, fav, mine -> Lists(r, f, p, fav, mine) }
    private val extras = combine(recipes.observeRecipes(), diary.observeSavedMeals(), foods.observeSearchHistory(), diary.observeMeals(), profiles.observeProfile()) { rc, sm, h, m, pr -> Extras(rc, sm, h, m, pr) }

    val state: StateFlow<FoodSearchState> = combine(
        combine(query, searchResults, tab, category) { q, sr, t, c -> Quad(q, sr, t, c) },
        combine(foods.observeCategories(), categoryFoods, foods.observeFoodCount()) { a, b, n -> Triple(a, b, n) },
        lists, extras, online,
    ) { (q, sr, t, c), (cats, catFoods, count), l, e, on ->
        FoodSearchState(
            query = q, searching = sr.first, results = sr.second, recipeResults = sr.third, tab = t, categoryId = c, categories = cats,
            categoryFoods = catFoods, recent = l.recent, frequent = l.frequent, popular = l.popular, favourites = l.favourites, mine = l.mine,
            recipes = e.recipes, savedMeals = e.saved, history = e.history, meals = e.meals, profile = e.profile,
            onlineResults = on.first, onlineError = on.second, onlineLoading = on.third, foodCount = count,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FoodSearchState())

    fun setQuery(q: String) { query.value = q; online.value = Triple(null, null, false) }
    fun setTab(t: FoodTab) { tab.value = t }
    fun setCategory(c: String?) { category.value = if (category.value == c) null else c }
    fun setMeal(m: String) { _meal.value = m }
    fun commitSearch() = launchSafe { foods.recordSearch(query.value) }
    fun clearHistory() = launchSafe { foods.clearSearchHistory() }

    fun compatibility(f: Food): Compatibility? {
        val p = state.value.profile ?: return null
        val diet = DietCompatibility.forDiet(f, p.dietaryPreference)
        val (allergen, _) = DietCompatibility.allergenStatus(f, p.allergies)
        return when {
            diet == Compatibility.INCOMPATIBLE || allergen == Compatibility.INCOMPATIBLE -> Compatibility.INCOMPATIBLE
            diet == Compatibility.UNKNOWN || allergen == Compatibility.UNKNOWN -> Compatibility.UNKNOWN
            else -> Compatibility.COMPATIBLE
        }
    }

    fun relog(item: RecentItem) = launchSafe {
        diary.relog(item.entry, date, mealId.value)
        message("Added ${item.entry.name} to ${state.value.meals.firstOrNull { it.id == mealId.value }?.name ?: "diary"}")
    }

    fun logSavedMeal(m: SavedMeal) = launchSafe {
        val n = diary.logSavedMeal(m.id, date, mealId.value)
        message("Added $n items from ${m.name}")
    }

    fun searchOnline() {
        val q = query.value.trim()
        if (q.length < 2) return
        online.value = Triple(null, null, true)
        launchSafe {
            imports.searchOnline(q)
                .onSuccess { online.value = Triple(it, null, false) }
                .onFailure { online.value = Triple(null, it.message ?: "Online search unavailable", false) }
        }
    }

    /** Stores an online result for review; returns the key for the editor route. */
    fun stageDraft(d: FoodDraft): String = drafts.put(d, "Review the values before saving. Missing nutrients stay blank (unknown).")

    val isLogging get() = mode == MODE_LOG

    private data class Lists(val recent: List<RecentItem>, val frequent: List<RecentItem>, val popular: List<Food>, val favourites: List<Food>, val mine: List<Food>)
    private data class Extras(val recipes: List<RecipeSummary>, val saved: List<SavedMeal>, val history: List<String>, val meals: List<Meal>, val profile: UserProfile?)
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
