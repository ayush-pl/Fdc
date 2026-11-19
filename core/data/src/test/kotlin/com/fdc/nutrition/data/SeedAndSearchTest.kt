package com.fdc.nutrition.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fdc.nutrition.data.seed.SeedState
import com.fdc.nutrition.domain.calc.ServingConverter
import com.fdc.nutrition.domain.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeedAndSearchTest {
    private val g = TestGraph()
    @After fun close() = g.db.close()

    @Test fun seedsTheBundledDatabaseAndIsIdempotent() = runBlocking {
        val first = g.seeder.ensureSeeded()
        assertThat(first).isInstanceOf(SeedState.Done::class.java)
        val count = (first as SeedState.Done).foodCount
        assertThat(count).isGreaterThan(10_000)
        val second = g.seeder.ensureSeeded() as SeedState.Done
        assertThat(second.foodCount).isEqualTo(count)
        assertThat(g.db.diaryDao().allMeals().map { it.id }).containsAtLeast(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER, Meal.MORNING_SNACK, Meal.EVENING_SNACK)
        assertThat(g.db.foodDao().categories().size).isAtLeast(21)
    }

    @Test fun searchFindsIndianFoodsByNameAndAlias() = runBlocking {
        g.seeder.ensureSeeded()
        val rice = g.foods.search("rice cooked")
        assertThat(rice).isNotEmpty()
        assertThat(rice.take(5).map { it.name }).contains("Rice, white, cooked")
        for (q in listOf("roti", "dal", "paneer", "dosa", "idli", "poha", "upma", "khichdi", "sambar", "curd", "buttermilk", "biryani", "sprouts", "besan chilla", "dhokla", "thepla", "rajma", "chole", "paratha", "chapati")) {
            val results = g.foods.search(q)
            assertWithMessage(q, results.isNotEmpty())
        }
        assertThat(g.foods.search("dahi").first().name).contains("yogurt")
        assertThat(g.foods.search("chawal").map { it.name }).contains("Rice, white, cooked")
    }

    @Test fun seededFoodsKeepMissingNutrientsAsUnknown() = runBlocking {
        g.seeder.ensureSeeded()
        val roti = g.foods.search("roti").first { it.name.startsWith("Roti") }
        val full = g.foods.getFood(roti.id)!!
        assertThat(full.dataQuality).isEqualTo(DataQuality.GENERIC_ESTIMATE)
        assertThat(full.nutrientsPer100.has(Nutrient.TRANS_FAT)).isFalse() // FNDDS has no trans fat values
        assertThat(full.nutrientsPer100.energyKcal).isNotNull()
        val pieces = ServingConverter.optionsFor(full).filter { it.unit == QuantityUnit.PIECE }
        assertThat(pieces.map { it.label }).contains("1 medium chappatti or roti (7\")")
    }

    private fun assertWithMessage(q: String, ok: Boolean) = com.google.common.truth.Truth.assertWithMessage("search %s", q).that(ok).isTrue()

    companion object {
        @JvmStatic @BeforeClass fun noop() {}
    }
}
