package com.fdc.nutrition.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.fdc.nutrition.data.seed.SeedState
import com.fdc.nutrition.ui.AppState
import com.fdc.nutrition.ui.body.BodyScreen
import com.fdc.nutrition.ui.components.Banner
import com.fdc.nutrition.ui.components.BannerKind
import com.fdc.nutrition.ui.components.LoadingBox
import com.fdc.nutrition.ui.diary.CalendarScreen
import com.fdc.nutrition.ui.diary.DiaryScreen
import com.fdc.nutrition.ui.diary.EntryEditScreen
import com.fdc.nutrition.ui.diary.QuickAddScreen
import com.fdc.nutrition.ui.diary.SavedMealsScreen
import com.fdc.nutrition.ui.food.AddFoodScreen
import com.fdc.nutrition.ui.food.BarcodeScannerScreen
import com.fdc.nutrition.ui.food.CompareFoodsScreen
import com.fdc.nutrition.ui.food.FoodDetailScreen
import com.fdc.nutrition.ui.food.FoodEditorScreen
import com.fdc.nutrition.ui.food.FoodLibraryScreen
import com.fdc.nutrition.ui.home.HomeScreen
import com.fdc.nutrition.ui.nutrition.NutritionScreen
import com.fdc.nutrition.ui.onboarding.SetupScreen
import com.fdc.nutrition.ui.plan.PlannerScreen
import com.fdc.nutrition.ui.profile.*
import com.fdc.nutrition.ui.recipes.RecipeDetailScreen
import com.fdc.nutrition.ui.recipes.RecipeEditorScreen
import com.fdc.nutrition.ui.recipes.RecipesScreen
import com.fdc.nutrition.ui.water.WaterScreen
import java.time.LocalDate
import kotlin.reflect.KClass

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No snackbar host") }

private data class TopLevel(val route: Any, val cls: KClass<*>, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel(HomeRoute, HomeRoute::class, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    TopLevel(FoodRoute, FoodRoute::class, "Food", Icons.Outlined.Restaurant, Icons.Filled.Restaurant),
    TopLevel(DiaryRoute(), DiaryRoute::class, "Diary", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook),
    TopLevel(NutritionRoute, NutritionRoute::class, "Nutrition", Icons.Outlined.Insights, Icons.Filled.Insights),
    TopLevel(ProfileRoute, ProfileRoute::class, "Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

fun LocalDate.toArg(): Long = toEpochDay()
fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)

@Composable
fun FdcRoot(state: AppState) {
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        when {
            !state.loaded -> Surface(Modifier.fillMaxSize()) { LoadingBox() }
            state.seed is SeedState.Failed && !state.isSetUp -> Surface(Modifier.fillMaxSize()) {
                Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Banner("Couldn't prepare the food database: ${(state.seed as SeedState.Failed).error}", BannerKind.ERROR)
                }
            }
            else -> AppNavHost(state, snackbar)
        }
    }
}

@Composable
fun AppNavHost(state: AppState, snackbar: SnackbarHostState, nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val showBottomBar = TOP_LEVEL.any { t -> dest?.hierarchy?.any { it.hasRoute(t.cls) } == true }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                TOP_LEVEL.forEach { t ->
                    val selected = dest?.hierarchy?.any { it.hasRoute(t.cls) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                        label = { Text(t.label) },
                        modifier = Modifier.testTag("tab_${t.label.lowercase()}"),
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val seeding = state.seed is SeedState.Running
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (seeding) Banner("Preparing the food database for offline use…", BannerKind.INFO, Modifier.padding(8.dp))
            NavHost(
                navController = nav, startDestination = if (state.isSetUp) HomeRoute else SetupRoute, modifier = Modifier.weight(1f),
                enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
            ) {
                composable<SetupRoute> { SetupScreen(onDone = { nav.navigate(HomeRoute) { popUpTo(0) { inclusive = true } } }) }
                composable<HomeRoute> { HomeScreen(nav) }
                composable<FoodRoute> { FoodLibraryScreen(nav) }
                composable<DiaryRoute> { DiaryScreen(nav) }
                composable<NutritionRoute> { NutritionScreen(nav) }
                composable<ProfileRoute> { ProfileScreen(nav) }

                composable<AddFoodRoute> { AddFoodScreen(nav) }
                composable<FoodDetailRoute> { FoodDetailScreen(nav) }
                composable<FoodEditorRoute> { FoodEditorScreen(nav) }
                composable<ScannerRoute> { BarcodeScannerScreen(nav) }
                composable<CompareRoute> { CompareFoodsScreen(nav) }
                composable<EntryEditRoute> { EntryEditScreen(nav) }
                composable<QuickAddRoute> { QuickAddScreen(nav) }
                composable<CalendarRoute> { CalendarScreen(nav) }
                composable<SavedMealsRoute> { SavedMealsScreen(nav) }
                composable<RecipesRoute> { RecipesScreen(nav) }
                composable<RecipeEditorRoute> { RecipeEditorScreen(nav) }
                composable<RecipeDetailRoute> { RecipeDetailScreen(nav) }
                composable<PlannerRoute> { PlannerScreen(nav) }
                composable<WaterRoute> { WaterScreen(nav) }
                composable<BodyRoute> { BodyScreen(nav) }
                composable<ProfileEditRoute> { ProfileEditScreen(nav) }
                composable<TargetsRoute> { TargetsScreen(nav) }
                composable<SettingsRoute> { SettingsScreen(nav) }
                composable<RemindersRoute> { RemindersScreen(nav) }
                composable<DataRoute> { DataScreen(nav) }
                composable<SyncRoute> { SyncScreen(nav) }
                composable<AboutRoute> { AboutScreen(nav) }
            }
        }
    }
}

/** Standard top app bar with back navigation for secondary screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, nav: NavHostController, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        windowInsets = TopAppBarDefaults.windowInsets,
    )
}

/** Collects one-off UI messages from a ViewModel into the shared snackbar. */
@Composable
fun CollectMessages(messages: kotlinx.coroutines.flow.Flow<String>) {
    val snackbar = LocalSnackbar.current
    LaunchedEffect(messages) { messages.collect { snackbar.showSnackbar(it) } }
}
