package com.fdc.nutrition.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fdc.nutrition.data.backup.ConflictStrategy
import com.fdc.nutrition.data.backup.BackupFormatException
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.repository.*
import com.fdc.nutrition.domain.calc.*
import com.fdc.nutrition.domain.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * End-to-end checks of the 32 acceptance scenarios against the real Room database seeded with the
 * bundled USDA data. UI-only aspects (theme rendering) are covered in the app module's UI tests.
 */
@RunWith(AndroidJUnit4::class)
class AcceptanceScenariosTest {
    private lateinit var server: MockWebServer
    private lateinit var g: TestGraph
    private val tol = 1e-9

    @Before fun setUp() = runBlocking<Unit> {
        server = MockWebServer(); server.start()
        g = TestGraph(offBaseUrl = server.url("/").toString())
        g.seeder.ensureSeeded()
    }
    @After fun tearDown() { g.db.close(); server.shutdown() }

    private fun profile() = UserProfile("Asha", g.today.minusYears(30), 165.0, Sex.FEMALE, 68.0, null, 60.0, ActivityLevel.LIGHT, Goal.LOSE, 0.5,
        DietaryPreference.VEGETARIAN, setOf("peanuts"), null, Instant.EPOCH, Instant.EPOCH)

    private suspend fun grams(foodId: String) = ServingConverter.optionsFor(g.foods.getFood(foodId)!!).first { it.unit == QuantityUnit.GRAM }
    private suspend fun rice() = g.foods.search("rice white cooked").first { it.name == "Rice, white, cooked" }

    @Test fun scenarios_1_to_3_profile_and_targets() = runBlocking<Unit> {
        g.profile.saveProfile(profile())
        val p = g.profile.observeProfile().first()!!
        assertThat(p.displayName).isEqualTo("Asha")
        assertThat(p.startWeightKg).isEqualTo(68.0)
        assertThat(g.db.bodyDao().all().single().value).isEqualTo(68.0) // initial weigh-in recorded

        val proposal = g.profile.propose(p).getOrThrow()
        // Mifflin–St Jeor female: 10*68 + 6.25*165 - 5*30 - 161 = 1400.25; ×1.375; −550
        assertThat(proposal.estimate.bmrKcal).isWithin(tol).of(1400.25)
        // TDEE 1925.3; a 0.5 kg/week deficit (550 kcal) exceeds the 25% cap (481.3) → 1444 → rounded 1440, with a warning
        assertThat(proposal.estimate.targetKcal).isEqualTo(1440.0)
        assertThat(proposal.estimate.warnings).isNotEmpty()
        assertThat(proposal.targets[Nutrient.IRON]).isEqualTo(18.0)
        g.profile.saveTargets(mapOf(Nutrient.ENERGY to 2200.0, Nutrient.PROTEIN to 137.5, Nutrient.CARBOHYDRATE to 275.0, Nutrient.FAT to 2200 * 0.25 / 9), 2500.0,
            MacroMethod.PERCENTAGE, MacroSplit(25.0, 50.0, 25.0), TargetOrigin.CUSTOM)
        val t = g.profile.observeCurrentTarget().first()!!
        assertThat(t.calories).isEqualTo(2200.0)
        val macroKcal = NutritionCalculator.energyFromMacros(t[Nutrient.PROTEIN]!!, t[Nutrient.CARBOHYDRATE]!!, t[Nutrient.FAT]!!)
        assertThat(macroKcal).isWithin(1e-6).of(2200.0)
        // Inconsistent percentage-based macros are rejected
        assertThrows(ValidationException::class.java) { runBlocking { g.profile.saveTargets(mapOf(Nutrient.ENERGY to 2200.0, Nutrient.PROTEIN to 10.0, Nutrient.CARBOHYDRATE to 10.0, Nutrient.FAT to 10.0), 2000.0, MacroMethod.PERCENTAGE, MacroSplit(25.0, 50.0, 25.0), TargetOrigin.CUSTOM) } }
        // Gram-based custom targets (spec example) are accepted
        g.profile.saveTargets(mapOf(Nutrient.ENERGY to 2200.0, Nutrient.PROTEIN to 150.0, Nutrient.CARBOHYDRATE to 250.0, Nutrient.FAT to 65.0), 2500.0, MacroMethod.GRAMS, null, TargetOrigin.CUSTOM)
        assertThat(g.profile.observeCurrentTarget().first()!![Nutrient.PROTEIN]).isEqualTo(150.0)
    }

    @Test fun scenarios_4_to_13_search_log_totals_and_recalculation() = runBlocking<Unit> {
        // 4–5 search and view nutrition
        val rice = g.foods.getFood(rice().id)!!
        assertThat(rice.dataQuality).isEqualTo(DataQuality.VERIFIED_DATABASE)
        assertThat(rice.preparation).isEqualTo(PreparationState.COOKED)
        val per100 = rice.nutrientsPer100
        assertThat(per100.energyKcal).isEqualTo(130.0)

        // 6–7 add 150 g cooked rice to lunch and verify the calculation
        val riceEntry = g.diary.logFood(g.today, Meal.LUNCH, rice.id, 150.0, grams(rice.id))
        val e = g.diary.entry(riceEntry)!!
        assertThat(e.nutrients.energyKcal!!).isWithin(tol).of(195.0)
        assertThat(e.nutrients.proteinG!!).isWithin(tol).of(per100.proteinG!! * 1.5)
        assertThat(e.nutrients.carbohydrateG!!).isWithin(tol).of(per100.carbohydrateG!! * 1.5)
        assertThat(e.nutrients.fatG!!).isWithin(tol).of(per100.fatG!! * 1.5)

        // 8 dal to lunch (1 cup via food-specific serving)
        val dal = g.foods.search("dal").first { it.name.startsWith("Dal") }.let { g.foods.getFood(it.id)!! }
        val cup = ServingConverter.optionsFor(dal).first { it.unit == QuantityUnit.CUP }
        val dalEntry = g.diary.logFood(g.today, Meal.LUNCH, dal.id, 1.0, cup)
        // 9 breakfast and dinner
        val roti = g.foods.search("roti").first { it.name.startsWith("Roti") }.let { g.foods.getFood(it.id)!! }
        val piece = ServingConverter.optionsFor(roti).first { it.label.contains("medium") }
        g.diary.logFood(g.today, Meal.DINNER, roti.id, 2.0, piece)
        val egg = g.foods.search("egg hard boiled").first { it.name == "Egg, hard-boiled" }
        g.diary.logFood(g.today, Meal.BREAKFAST, egg.id, 100.0, grams(egg.id))

        // 10–11 totals equal the sum of entries
        val entries = g.diary.observeEntries(g.today).first()
        assertThat(entries).hasSize(4)
        val totals = NutrientAggregator.sumEntries(entries)
        val expectedKcal = 195.0 + dal.nutrientsPer100.energyKcal!! * cup.amountPerUnit / 100 + roti.nutrientsPer100.energyKcal!! * 2 * piece.amountPerUnit / 100 +
            g.foods.getFood(egg.id)!!.nutrientsPer100.energyKcal!!
        assertThat(totals.calories).isWithin(1e-6).of(expectedKcal)
        assertThat(totals[Nutrient.PROTEIN].amount).isWithin(1e-6).of(entries.sumOf { it.nutrients.proteinG!! })
        assertThat(totals[Nutrient.PROTEIN].isComplete).isTrue()

        // 12–13 modify quantity → automatic recalculation
        g.diary.updateEntry(riceEntry, quantity = 200.0)
        assertThat(g.diary.entry(riceEntry)!!.nutrients.energyKcal!!).isWithin(tol).of(260.0)
        val newTotals = NutrientAggregator.sumEntries(g.diary.observeEntries(g.today).first())
        assertThat(newTotals.calories - totals.calories).isWithin(1e-6).of(65.0)
        // move between meals, duplicate, copy previous meal
        g.diary.moveEntries(listOf(dalEntry), Meal.DINNER)
        assertThat(g.diary.entry(dalEntry)!!.mealId).isEqualTo(Meal.DINNER)
        g.diary.duplicate(riceEntry)
        assertThat(g.diary.copyMeal(g.today, Meal.DINNER, g.today.plusDays(1), Meal.DINNER)).isEqualTo(2)
    }

    @Test fun scenarios_14_15_custom_food_and_favourite_and_archive() = runBlocking<Unit> {
        val id = g.foods.saveFood(FoodDraft(name = "Grandma's laddoo", brand = null, categoryId = "homemade", basis = NutrientBasis.GRAM,
            nutrientsPer100 = Nutrients.of(Nutrient.ENERGY to 450.0, Nutrient.PROTEIN to 8.0, Nutrient.CARBOHYDRATE to 55.0, Nutrient.FAT to 22.0),
            servings = listOf(ServingSize("", QuantityUnit.PIECE, "1 laddoo", 35.0, isDefault = true))))
        val f = g.foods.getFood(id)!!
        assertThat(f.isCustom).isTrue()
        assertThat(f.dataQuality).isEqualTo(DataQuality.USER_ENTERED)
        assertThat(g.foods.search("laddoo").first().id).isEqualTo(id)
        // validation
        assertThrows(ValidationException::class.java) { runBlocking { g.foods.saveFood(FoodDraft(name = "", brand = null, categoryId = "homemade", basis = NutrientBasis.GRAM, nutrientsPer100 = Nutrients.EMPTY, servings = emptyList())) } }
        // favourite
        g.foods.setFavourite(id, true)
        assertThat(g.foods.observeFavourites().first().map { it.id }).containsExactly(id)
        // referenced foods are archived, never deleted
        g.diary.logFood(g.today, Meal.EVENING_SNACK, id, 1.0, ServingConverter.optionsFor(f).first { it.unit == QuantityUnit.PIECE })
        assertThat(g.foods.deleteOrArchive(id)).isFalse()
        assertThat(g.foods.getFood(id)!!.isArchived).isTrue()
        assertThat(g.foods.search("laddoo")).isEmpty()
        assertThat(g.diary.observeEntries(g.today).first().single().name).isEqualTo("Grandma's laddoo")
        // the database also refuses a raw delete of a referenced food
        assertThrows(Exception::class.java) { runBlocking { g.db.foodDao().deleteFood(id) } }
    }

    @Test fun scenarios_16_to_18_recipe_builder() = runBlocking<Unit> {
        val paneer = g.foods.search("paneer").first { it.name == "Paneer" }.let { g.foods.getFood(it.id)!! }
        val onion = g.foods.search("onion raw").first { it.name == "Onion, raw" }.let { g.foods.getFood(it.id)!! }
        val tomato = g.foods.search("tomato raw").first { it.name == "Tomato, raw" }.let { g.foods.getFood(it.id)!! }
        val oil = g.foods.search("soybean oil").first { it.name == "Soybean oil" }.let { g.foods.getFood(it.id)!! }
        val ings = listOf(paneer to 150.0, onion to 50.0, tomato to 80.0, oil to 5.0)
        val id = g.recipes.save(RecipeDraft(null, "Homemade Paneer Bhurji", null, "Crumble paneer, sauté with onion and tomato.", 2.0, 240.0, null, false,
            ings.map { (f, gr) -> IngredientDraft(null, f.id, gr, ServingConverter.optionsFor(f).first { it.unit == QuantityUnit.GRAM }) }))
        val r = g.recipes.observeRecipe(id).first()!!
        val expectedKcal = ings.sumOf { (f, gr) -> f.nutrientsPer100.energyKcal!! * gr / 100 }
        val expectedProtein = ings.sumOf { (f, gr) -> f.nutrientsPer100.proteinG!! * gr / 100 }
        assertThat(r.nutrition.total.energyKcal!!).isWithin(1e-6).of(expectedKcal)
        assertThat(r.nutrition.total.proteinG!!).isWithin(1e-6).of(expectedProtein)
        assertThat(r.nutrition.perServing.energyKcal!!).isWithin(1e-6).of(expectedKcal / 2)
        assertThat(r.nutrition.per100g!!.energyKcal!!).isWithin(1e-6).of(expectedKcal / 240 * 100) // uses recorded cooked weight
        // 18 log one serving
        val entryId = g.diary.logRecipe(g.today, Meal.DINNER, id, 1.0)
        val entry = g.diary.entry(entryId)!!
        assertThat(entry.nutrients.energyKcal!!).isWithin(1e-6).of(expectedKcal / 2)
        // editing the recipe later does not change the logged entry
        g.recipes.save(RecipeDraft(id, "Homemade Paneer Bhurji", null, null, 2.0, 240.0, null, false,
            listOf(IngredientDraft(null, paneer.id, 300.0, ServingConverter.optionsFor(paneer).first { it.unit == QuantityUnit.GRAM }))))
        assertThat(g.diary.entry(entryId)!!.nutrients.energyKcal!!).isWithin(1e-6).of(expectedKcal / 2)
        // referenced recipe is archived, not deleted
        assertThat(g.recipes.deleteOrArchive(id)).isFalse()
    }

    @Test fun scenarios_19_20_barcode_found_and_unknown() = runBlocking<Unit> {
        val code = "5000159407236"
        server.enqueue(MockResponse().setBody("""{"code":"$code","status":1,"product":{"product_name":"Choco Bar","brands":"Acme","quantity":"45 g","product_quantity":45,
            "serving_size":"1 bar (45 g)","serving_quantity":45,"allergens_tags":["en:milk","en:soybeans"],"ingredients_analysis_tags":["en:non-vegan","en:vegetarian"],
            "states_tags":["en:ingredients-completed"],"ingredients_text":"sugar, cocoa butter, milk","nutriments":{"energy-kcal_100g":520,"proteins_100g":7.2,"carbohydrates_100g":58,"fat_100g":28.5,"sugars_100g":50,"salt_100g":0.25}}}"""))
        val outcome = g.imports.lookupBarcode(code)
        assertThat(outcome).isInstanceOf(BarcodeOutcome.Remote::class.java)
        val draft = (outcome as BarcodeOutcome.Remote).result.draft
        assertThat(draft.nutrientsPer100.energyKcal).isEqualTo(520.0)
        assertThat(draft.nutrientsPer100[Nutrient.SODIUM]!!).isWithin(1e-9).of(100.0) // 0.25 g salt / 2.5 * 1000
        assertThat(draft.nutrientsPer100.has(Nutrient.FIBRE)).isFalse() // not on the label → unknown
        assertThat(draft.allergens).containsExactly("milk", "soybeans")
        assertThat(draft.dietClass).isEqualTo(DietClass.VEGETARIAN)
        // user corrects then saves locally; a second scan resolves offline from the local DB
        val id = g.imports.saveImported(draft.copy(nutrientsPer100 = draft.nutrientsPer100.with(Nutrient.FIBRE, 3.0)))
        assertThat(g.imports.lookupBarcode(code)).isEqualTo(BarcodeOutcome.Local(id))
        assertThat(g.imports.saveImported(draft)).isEqualTo(id) // duplicate detection

        // unknown barcode: never fabricated
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"status":0,"status_verbose":"product not found"}"""))
        assertThat(g.imports.lookupBarcode("0000000000017")).isEqualTo(BarcodeOutcome.NotFound)
        server.enqueue(MockResponse().setBody("""{"status":0,"status_verbose":"product not found"}"""))
        assertThat(g.imports.lookupBarcode("0000000000024")).isEqualTo(BarcodeOutcome.NotFound)
    }

    @Test fun scenarios_21_to_24_water_weight_and_weekly_analytics() = runBlocking<Unit> {
        g.profile.saveProfile(profile())
        g.profile.saveTargets(mapOf(Nutrient.ENERGY to 2000.0, Nutrient.PROTEIN to 100.0, Nutrient.CARBOHYDRATE to 250.0, Nutrient.FAT to 66.7), 2000.0, MacroMethod.GRAMS, null, TargetOrigin.CUSTOM, g.today.minusDays(30))
        // 21 water — tea is not counted as water by default
        g.water.add(g.today, 250.0); g.water.add(g.today, 500.0); g.water.add(g.today, 200.0, BeverageType.TEA)
        val w = g.water.observeForDate(g.today).first()
        assertThat(WaterRepository.hydrationTotal(w, false)).isEqualTo(750.0)
        assertThat(WaterRepository.hydrationTotal(w, true)).isEqualTo(950.0)
        g.water.remove(w.first { it.beverage == BeverageType.TEA }.id)
        // 22–23 weight records and progression
        g.body.save(null, g.today.minusDays(14), MeasurementType.WEIGHT, 67.0)
        g.body.save(null, g.today.minusDays(7), MeasurementType.WEIGHT, 66.2)
        g.body.save(null, g.today, MeasurementType.WEIGHT, 65.5)
        g.body.save(null, g.today, MeasurementType.WAIST, 82.0)
        assertThrows(ValidationException::class.java) { runBlocking { g.body.save(null, g.today, MeasurementType.WEIGHT, 5.0) } }
        val prog = g.analytics.observeWeightProgress().first()
        assertThat(prog.startKg).isEqualTo(68.0); assertThat(prog.currentKg).isEqualTo(65.5); assertThat(prog.changeKg!!).isWithin(1e-9).of(-2.5)
        assertThat(prog.progressPercent!!).isWithin(1e-9).of(2.5 / 8 * 100)
        assertThat(g.profile.profile()!!.currentWeightKg).isEqualTo(65.5)
        // 24 weekly analytics from stored data
        val rice = rice()
        for (i in 0..6) if (i != 3) g.diary.logFood(g.today.minusDays(i.toLong()), Meal.LUNCH, rice.id, 1000.0 + i * 100, grams(rice.id))
        val (from, to) = g.analytics.resolve(AnalyticsRange.WEEK)
        val report = g.analytics.observeReport(from, to).first()
        assertThat(report.totalDays).isEqualTo(7)
        assertThat(report.loggedDays).isEqualTo(6)
        val expected = listOf(0, 1, 2, 4, 5, 6).map { (1000.0 + it * 100) * 1.3 }.average()
        assertThat(report.averages[Nutrient.ENERGY]!!).isWithin(1e-6).of(expected)
        assertThat(report.averageWaterMl).isEqualTo(750.0)
        assertThat(report.topFoods.first().count).isEqualTo(6)
    }

    @Test fun scenarios_25_26_edit_history_and_snapshots() = runBlocking<Unit> {
        val id = g.foods.saveFood(FoodDraft(name = "Test muesli", brand = "Home", categoryId = "grains", basis = NutrientBasis.GRAM,
            nutrientsPer100 = Nutrients.of(Nutrient.ENERGY to 380.0, Nutrient.PROTEIN to 10.0, Nutrient.CARBOHYDRATE to 60.0, Nutrient.FAT to 8.0), servings = emptyList()))
        val past = g.today.minusDays(20)
        val entryId = g.diary.logFood(past, Meal.BREAKFAST, id, 50.0, grams(id))
        // the food definition changes later
        g.foods.saveFood(FoodDraft(id = id, name = "Test muesli", brand = "Home", categoryId = "grains", basis = NutrientBasis.GRAM,
            nutrientsPer100 = Nutrients.of(Nutrient.ENERGY to 500.0, Nutrient.PROTEIN to 12.0, Nutrient.CARBOHYDRATE to 60.0, Nutrient.FAT to 20.0), servings = emptyList()))
        assertThat(g.diary.entry(entryId)!!.nutrients.energyKcal!!).isWithin(tol).of(190.0) // snapshot kept
        // editing the historical quantity recalculates from the snapshot, not the new definition
        g.diary.updateEntry(entryId, quantity = 100.0)
        assertThat(g.diary.entry(entryId)!!.nutrients.energyKcal!!).isWithin(tol).of(380.0)
        // only an explicit correction applies the new values
        g.diary.refreshSnapshot(entryId)
        assertThat(g.diary.entry(entryId)!!.nutrients.energyKcal!!).isWithin(tol).of(500.0)
    }

    @Test fun scenario_27_theme_preference_persists() = runBlocking<Unit> {
        assertThat(g.profile.preferences().theme).isEqualTo(ThemeMode.SYSTEM)
        g.profile.updatePreferences { it.copy(theme = ThemeMode.DARK) }
        assertThat(g.profile.observePreferences().first().theme).isEqualTo(ThemeMode.DARK)
        g.profile.updatePreferences { it.copy(theme = ThemeMode.LIGHT) }
        assertThat(g.profile.preferences().theme).isEqualTo(ThemeMode.LIGHT)
    }

    @Test fun scenario_28_close_and_reopen_keeps_records() = runBlocking<Unit> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = File(ctx.cacheDir, "reopen-test.db").also { it.delete() }
        val g1 = TestGraph(db = Room.databaseBuilder(ctx, AppDatabase::class.java, file.absolutePath).build())
        g1.seeder.seedDefaults()
        val qid = g1.diary.quickAdd(g1.today, Meal.LUNCH, "Thali", 750.0)
        g1.water.add(g1.today, 300.0)
        g1.db.close()
        val g2 = TestGraph(db = Room.databaseBuilder(ctx, AppDatabase::class.java, file.absolutePath).build())
        assertThat(g2.diary.entry(qid)!!.nutrients.energyKcal).isEqualTo(750.0)
        assertThat(g2.water.observeForDate(g2.today).first().single().amountMl).isEqualTo(300.0)
        g2.db.close(); file.delete()
    }

    @Test fun scenario_29_works_offline() = runBlocking<Unit> {
        server.shutdown() // no network
        val offline = TestGraph(db = g.db, offBaseUrl = "http://127.0.0.1:9", usdaBaseUrl = "http://127.0.0.1:9")
        val rice = offline.foods.search("rice").first()
        offline.diary.logFood(offline.today, Meal.LUNCH, rice.id, 100.0, ServingConverter.optionsFor(offline.foods.getFood(rice.id)!!).first { it.unit == QuantityUnit.GRAM })
        assertThat(offline.diary.observeEntries(offline.today).first()).hasSize(1)
        val r = offline.imports.lookupBarcode("8901234567890")
        assertThat(r).isInstanceOf(BarcodeOutcome.Unavailable::class.java) // clear "lookup unavailable", nothing invented
        assertThat(offline.usda.search("apple").isFailure).isTrue()
        server = MockWebServer() // for tearDown
    }

    @Test fun scenario_30_export_csv() = runBlocking<Unit> {
        val rice = rice()
        g.diary.logFood(g.today, Meal.LUNCH, rice.id, 150.0, grams(rice.id))
        g.diary.quickAdd(g.today, Meal.DINNER, "Party, food", 900.0)
        g.water.add(g.today, 500.0)
        val out = ByteArrayOutputStream()
        g.csv.writeZip(out)
        val files = mutableMapOf<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z -> generateSequence { z.nextEntry }.forEach { files[it.name] = z.readBytes().decodeToString() } }
        assertThat(files.keys).containsAtLeast("food_diary.csv", "daily_totals.csv", "recipes.csv", "meal_plans.csv", "water.csv", "body_measurements.csv")
        val diary = files["food_diary.csv"]!!.lines()
        assertThat(diary[0]).startsWith("date,meal,type,food_name")
        assertThat(diary.first { it.contains("Rice, white, cooked") }).contains("195.0")
        val quick = diary.first { it.contains("Party, food") }
        assertThat(quick).contains("\"Party, food\"") // escaped
        assertThat(quick).contains("900.0,,,") // energy known; protein/carbs unknown → empty, not 0
        assertThat(files["water.csv"]!!).contains("500.0")
    }

    @Test fun scenario_31_restore_backup_without_corruption() = runBlocking<Unit> {
        g.profile.saveProfile(profile())
        val rice = rice()
        val keepId = g.diary.logFood(g.today, Meal.LUNCH, rice.id, 150.0, grams(rice.id))
        val customId = g.foods.saveFood(FoodDraft(name = "Chutney mix", brand = null, categoryId = "homemade", basis = NutrientBasis.GRAM, nutrientsPer100 = Nutrients.of(Nutrient.ENERGY to 120.0), servings = emptyList()))
        val customEntry = g.diary.logFood(g.today, Meal.LUNCH, customId, 20.0, grams(customId))
        g.water.add(g.today, 400.0)
        val backupText = g.backup.export("test")

        // local changes after the backup
        g.diary.updateEntry(keepId, quantity = 300.0)
        g.diary.quickAdd(g.today, Meal.DINNER, "After backup", 500.0)

        val preview = g.backup.preview(backupText)
        assertThat(preview.conflicting).isAtLeast(1)
        assertThat(preview.invalid).isEmpty()
        val r = g.backup.restore(backupText, ConflictStrategy.KEEP_EXISTING)
        assertThat(r.replaced).isEqualTo(0)
        assertThat(g.diary.entry(keepId)!!.quantity).isEqualTo(300.0) // not silently overwritten
        assertThat(g.diary.observeEntries(g.today).first()).hasSize(3)

        // into a fresh database: everything comes back, references intact
        val fresh = TestGraph()
        fresh.seeder.ensureSeeded()
        val r2 = fresh.backup.restore(backupText)
        assertThat(r2.invalid).isEmpty()
        assertThat(fresh.diary.entry(keepId)!!.quantity).isEqualTo(150.0)
        assertThat(fresh.diary.entry(customEntry)!!.foodId).isEqualTo(customId)
        assertThat(fresh.profile.profile()!!.displayName).isEqualTo("Asha")
        assertThat(fresh.water.observeForDate(fresh.today).first().single().amountMl).isEqualTo(400.0)
        // restoring the same file again changes nothing
        val r3 = fresh.backup.restore(backupText)
        assertThat(r3.inserted).isEqualTo(0); assertThat(r3.replaced).isEqualTo(0); assertThat(r3.unchanged).isEqualTo(r2.inserted + r2.unchanged)
        fresh.db.close()

        // explicit replace strategy
        g.backup.restore(backupText, ConflictStrategy.REPLACE_WITH_BACKUP)
        assertThat(g.diary.entry(keepId)!!.quantity).isEqualTo(150.0)

        // invalid files are rejected before touching data
        assertThrows(BackupFormatException::class.java) { g.backup.parse("{\"hello\":1}") }
        assertThrows(BackupFormatException::class.java) { g.backup.parse(backupText.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")) }
        // individually invalid records are skipped and reported
        val corrupted = backupText.replaceFirst("\"quantity\": 150.0", "\"quantity\": -5.0")
        val r4 = TestGraph().let { t -> t.seeder.seedDefaults(); t.backup.restore(corrupted).also { t.db.close() } }
        assertThat(r4.invalid).hasSize(1)
    }

    @Test fun scenario_32_missing_nutrient_data_is_not_zero() = runBlocking<Unit> {
        val q = g.diary.quickAdd(g.today, Meal.LUNCH, "Street food", 400.0)
        val rice = rice()
        g.diary.logFood(g.today, Meal.LUNCH, rice.id, 100.0, grams(rice.id))
        val totals = NutrientAggregator.sumEntries(g.diary.observeEntries(g.today).first())
        assertThat(totals.calories).isWithin(tol).of(530.0)
        val protein = totals[Nutrient.PROTEIN]
        assertThat(protein.knownCount).isEqualTo(1)
        assertThat(protein.missingCount).isEqualTo(1)
        assertThat(g.diary.entry(q)!!.isCalorieOnly).isTrue()
        assertThat(g.diary.entry(q)!!.nutrients[Nutrient.PROTEIN]).isNull()
        // FNDDS roti has no trans-fat value → the day's trans fat is unknown, not 0
        val roti = g.foods.search("roti").first { it.name.startsWith("Roti") }
        val onlyRoti = TestGraph(db = g.db)
        onlyRoti.diary.logFood(g.today.plusDays(1), Meal.DINNER, roti.id, 40.0, grams(roti.id))
        val t2 = NutrientAggregator.sumEntries(onlyRoti.diary.observeEntries(g.today.plusDays(1)).first())
        assertThat(t2[Nutrient.TRANS_FAT].isUnknown).isTrue()
        assertThat(t2.known(Nutrient.TRANS_FAT)).isNull()
    }

    @Test fun planned_meals_are_never_counted_until_logged_and_log_is_idempotent() = runBlocking<Unit> {
        val rice = rice()
        val day = g.today.plusDays(1)
        val pid = g.plans.planFood(day, Meal.LUNCH, rice.id, 200.0, grams(rice.id))
        assertThat(g.diary.observeEntries(day).first()).isEmpty()
        val e1 = g.plans.logPlanned(pid)
        val e2 = g.plans.logPlanned(pid)
        assertThat(e1).isEqualTo(e2)
        assertThat(g.diary.observeEntries(day).first()).hasSize(1)
        // modifying the plan afterwards doesn't touch the diary
        g.plans.updatePlanned(pid, quantity = 500.0)
        assertThat(g.diary.entry(e1)!!.quantity).isEqualTo(200.0)
        // copy day, recurring schedule and templates
        assertThat(g.plans.copyDay(day, day.plusDays(1))).isEqualTo(1)
        g.plans.addRecurringFood(Meal.BREAKFAST, rice.id, 100.0, grams(rice.id), setOf(day.dayOfWeek), day, null)
        g.plans.materialize(day, day.plusDays(13)); g.plans.materialize(day, day.plusDays(13))
        assertThat(g.plans.observeRange(day, day.plusDays(13)).first().count { it.recurrenceId != null }).isEqualTo(2)
        val week = PlanRepository.weekStart(day)
        val tpl = g.plans.saveWeekAsTemplate(week, "Week A", true)
        assertThat(g.plans.applyTemplate(tpl, week.plusWeeks(2))).isAtLeast(1)
    }
}
