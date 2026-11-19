package com.fdc.nutrition

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.fdc.nutrition.domain.model.*
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Drives the real app (real Hilt graph, Room database seeded from the bundled asset) through the
 * critical food-logging flows. Runs on the JVM with Robolectric; screenshots are written to
 * app/build/screenshots for visual review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FoodLoggingUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val app get() = ApplicationProvider.getApplicationContext<FdcApplication>()
    private val ep get() = EntryPointAccessors.fromApplication(app, AppEntryPoint::class.java)

    @Before fun init() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, androidx.work.Configuration.Builder().setWorkerFactory(app.workerFactory).build())
        runBlocking { ep.seeder().ensureSeeded() }
    }
    @After fun close() { if (::scenario.isInitialized) scenario.close() }

    private fun setUpProfile(theme: ThemeMode = ThemeMode.LIGHT) = runBlocking {
        val p = ep.profiles()
        p.saveProfile(UserProfile("Asha", LocalDate.now().minusYears(30), 165.0, Sex.FEMALE, 68.0, null, 60.0, ActivityLevel.LIGHT, Goal.LOSE, 0.5,
            DietaryPreference.VEGETARIAN, emptySet(), null, Instant.now(), Instant.now()))
        p.saveTargets(mapOf(Nutrient.ENERGY to 2200.0, Nutrient.PROTEIN to 150.0, Nutrient.CARBOHYDRATE to 250.0, Nutrient.FAT to 65.0, Nutrient.FIBRE to 30.0), 2500.0,
            MacroMethod.GRAMS, null, TargetOrigin.CUSTOM)
        p.updatePreferences { it.copy(theme = theme) }
    }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }

    /** Polls [condition] while draining the main looper (coroutines resumed from Room/IO threads). */
    private fun waitUntil(timeout: Long = 15_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(30)
        }
        runCatching { shot("FAILED_${System.currentTimeMillis()}") }
        throw AssertionError("Condition not met within $timeout ms")
    }

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 15_000) =
        waitUntil(timeout) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        val bmp = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun onboardingCreatesProfileAndTargets() {
        launch()
        waitFor(hasTestTag("setup_name"))
        shot("01_onboarding")
        compose.onNodeWithTag("setup_name").performTextInput("Ravi")
        compose.onNodeWithTag("setup_height").performTextInput("175")
        compose.onNodeWithTag("setup_weight").performTextInput("80")
        compose.onNodeWithTag("setup_next").performClick()
        compose.onNodeWithTag("goal_LOSE").performClick()
        compose.onNodeWithTag("setup_next").performClick()
        compose.onNodeWithTag("setup_next").performClick()
        waitFor(hasTestTag("target_kcal"))
        shot("02_onboarding_targets")
        compose.onNodeWithTag("setup_finish").performClick()
        waitFor(hasTestTag("calorie_ring"))
        runBlocking {
            assertThat(ep.profiles().profile()!!.displayName).isEqualTo("Ravi")
            val t = ep.profiles().observeCurrentTarget().first()!!
            assertThat(t.calories).isGreaterThan(1500.0)
            // percentage-based macros are consistent with the calorie target
            val kcal = t[Nutrient.PROTEIN]!! * 4 + t[Nutrient.CARBOHYDRATE]!! * 4 + t[Nutrient.FAT]!! * 9
            assertThat(kcal).isWithin(1.0).of(t.calories!!)
        }
    }

    @Test fun searchLogEditAndRecalculateRice() {
        setUpProfile()
        launch()
        waitFor(hasTestTag("home_add_food"))
        shot("03_home_empty")
        compose.onNodeWithTag("home_add_food").performClick()
        waitFor(hasTestTag("food_search"))
        compose.onNodeWithTag("food_search").performTextInput("rice white cooked")
        waitFor(hasText("Rice, white, cooked"))
        shot("04_search_results")
        compose.onAllNodesWithText("Rice, white, cooked")[0].performClick()
        waitFor(hasTestTag("quantity_field"))
        compose.onNodeWithTag("quantity_field").performTextReplacement("150")
        compose.onNodeWithTag("unit_dropdown").performClick()
        compose.onAllNodesWithText("g", substring = false)[0].performClick()
        waitUntil(5_000) { compose.onAllNodesWithText("195 kcal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("live_protein").assertTextEquals("4 g") // 4.035 g, rounded only for display
        shot("05_food_detail_150g")
        compose.onNodeWithTag("add_food_button").performClick()
        waitUntil(10_000) { runBlocking { ep.diary().observeEntries(LocalDate.now()).first().isNotEmpty() } }
        val entry = runBlocking { ep.diary().observeEntries(LocalDate.now()).first().single() }
        assertThat(entry.quantity).isEqualTo(150.0)
        assertThat(entry.nutrients.energyKcal!!).isWithin(1e-9).of(195.0)

        // Diary shows it; edit the quantity and verify recalculation
        // After adding, the app stays on the search screen for fast multi-item logging; go back home.
        waitFor(hasTestTag("food_search"))
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasTestTag("tab_diary"))
        compose.onNodeWithTag("tab_diary").performClick()
        waitFor(hasTestTag("entry_${entry.id}"))
        compose.onNodeWithTag("diary_total_kcal").assertTextEquals("195 kcal")
        shot("06_diary")
        compose.onNodeWithTag("entry_${entry.id}").performClick()
        waitFor(hasTestTag("edit_quantity"))
        compose.onNodeWithTag("edit_quantity").performTextReplacement("200")
        compose.onNodeWithTag("edit_live_kcal").assertTextEquals("260 kcal")
        compose.onNodeWithTag("save_entry").performClick()
        waitUntil(10_000) { runBlocking { ep.diary().entry(entry.id)!!.quantity == 200.0 } }
        waitFor(hasTestTag("diary_total_kcal").and(hasText("260 kcal")))
    }

    @Test fun quickAddCaloriesKeepsMacrosUnknown() {
        setUpProfile()
        launch()
        waitFor(hasTestTag("tab_diary"))
        compose.onNodeWithTag("tab_diary").performClick()
        waitFor(hasTestTag("diary_add"))
        compose.onNodeWithTag("diary_add").performClick()
        waitFor(hasTestTag("quick_add_chip"))
        compose.onNodeWithTag("quick_add_chip").performClick()
        waitFor(hasTestTag("quick_kcal"))
        compose.onNodeWithTag("quick_kcal").performTextInput("450")
        compose.onNodeWithTag("quick_save").performClick()
        waitUntil(10_000) { runBlocking { ep.diary().observeEntries(LocalDate.now()).first().isNotEmpty() } }
        val e = runBlocking { ep.diary().observeEntries(LocalDate.now()).first().single() }
        assertThat(e.isCalorieOnly).isTrue()
        assertThat(e.nutrients.proteinG).isNull()
    }

    @Test fun themeSwitchAppliesDarkAndLight() {
        setUpProfile(ThemeMode.LIGHT)
        launch()
        waitFor(hasTestTag("tab_profile"))
        fun bgLuminance(): Double {
            val b = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
            val px = b.getPixel(b.width / 2, b.height / 3)
            return (0.299 * android.graphics.Color.red(px) + 0.587 * android.graphics.Color.green(px) + 0.114 * android.graphics.Color.blue(px)) / 255
        }
        assertThat(bgLuminance()).isGreaterThan(0.7)
        compose.onNodeWithTag("tab_profile").performClick()
        waitFor(hasTestTag("settings_row"))
        compose.onNodeWithTag("settings_row").performClick()
        waitFor(hasTestTag("theme_DARK"))
        compose.onNodeWithTag("theme_DARK").performClick()
        waitUntil(5_000) { runBlocking { ep.profiles().preferences().theme == ThemeMode.DARK } }
        compose.waitForIdle()
        shot("07_settings_dark")
        assertThat(bgLuminance()).isLessThan(0.3)
        compose.onNodeWithTag("theme_LIGHT").performClick()
        waitUntil(5_000) { runBlocking { ep.profiles().preferences().theme == ThemeMode.LIGHT } }
        compose.waitForIdle()
        assertThat(bgLuminance()).isGreaterThan(0.7)
    }
}
