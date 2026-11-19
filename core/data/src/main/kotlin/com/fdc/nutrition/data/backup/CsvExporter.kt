package com.fdc.nutrition.data.backup

import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.toDomain
import com.fdc.nutrition.domain.calc.NutrientAggregator
import com.fdc.nutrition.domain.calc.RecipeCalculator
import com.fdc.nutrition.domain.calc.effectiveOn
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.data.repository.RecipeRepository
import java.io.OutputStream
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CSV export. Numbers are written with full precision ("." decimal separator); unknown nutrient
 * values are left empty (never 0). Units are in each column header.
 */
@Singleton
class CsvExporter @Inject constructor(private val db: AppDatabase, private val recipes: RecipeRepository) {
    private val nutrientHeaders = Nutrient.entries.map { "${it.key}" }

    private fun esc(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    private fun Writer.row(vararg v: Any?) { write(v.joinToString(",") { esc(it) }); write("\n") }
    private fun Writer.row(v: List<Any?>) { write(v.joinToString(",") { esc(it) }); write("\n") }

    suspend fun writeDiary(w: Writer) {
        val meals = db.diaryDao().allMeals().associate { it.id to it.name }
        w.row(listOf("date", "meal", "type", "food_name", "brand", "quantity", "unit", "amount_in_basis", "basis_unit", "data_quality", "note") + nutrientHeaders)
        db.diaryDao().all().map { it.toDomain() }.forEach { e ->
            val n = e.nutrients
            w.row(listOf(e.date, meals[e.mealId] ?: e.mealId, e.type, e.name, e.brand, e.quantity, e.unitLabel, e.baseAmount, e.basis.symbol, e.dataQuality, e.note) + Nutrient.entries.map { n[it] })
        }
    }

    suspend fun writeDailyTotals(w: Writer) {
        val targets = db.profileDao().targets().map { it.toDomain() }
        val water = db.waterDao().all().groupBy { it.date }.mapValues { (_, l) -> l.filter { it.beverage.name == "WATER" }.sumOf { it.amountMl } }
        w.row(listOf("date", "entries", "water_ml", "target_kcal") + nutrientHeaders + Nutrient.entries.map { "${it.key}_coverage" })
        db.diaryDao().all().map { it.toDomain() }.groupBy { it.date }.toSortedMap().forEach { (d, list) ->
            val t = NutrientAggregator.sumEntries(list)
            val target = targets.effectiveOn(d)
            w.row(listOf(d, list.size, water[d], target?.calories) + Nutrient.entries.map { t.known(it) } + Nutrient.entries.map { "%.2f".format(java.util.Locale.ROOT, t[it].coverage) })
        }
    }

    suspend fun writeRecipes(w: Writer) {
        w.row(listOf("recipe", "servings", "final_weight_g", "ingredient", "ingredient_quantity", "ingredient_unit", "ingredient_amount_in_basis") + nutrientHeaders.map { "per_serving_$it" })
        db.recipeDao().all().forEach { r ->
            val recipe = recipes.getRecipe(r.id) ?: return@forEach
            val n = RecipeCalculator.calculate(recipe)
            recipe.ingredients.forEachIndexed { i, ing ->
                w.row(listOf(recipe.name, recipe.servings, recipe.finalWeightGrams, ing.foodName, ing.quantity, ing.unitLabel, ing.baseAmount) + if (i == 0) Nutrient.entries.map { n.perServing[it] } else Nutrient.entries.map { null })
            }
        }
    }

    suspend fun writePlans(w: Writer) {
        val meals = db.diaryDao().allMeals().associate { it.id to it.name }
        w.row(listOf("date", "meal", "food_or_recipe", "quantity", "unit", "logged_as_eaten") + nutrientHeaders)
        db.planDao().all().filter { !it.isSkipped }.sortedBy { it.date }.map { it.toDomain() }.forEach { p ->
            w.row(listOf(p.date, meals[p.mealId] ?: p.mealId, p.name, p.quantity, p.unitLabel, p.isLogged) + Nutrient.entries.map { p.nutrients[it] })
        }
    }

    suspend fun writeWater(w: Writer) {
        w.row("date", "logged_at", "amount_ml", "beverage")
        db.waterDao().all().forEach { w.row(it.date, it.loggedAt, it.amountMl, it.beverage) }
    }

    suspend fun writeBody(w: Writer) {
        w.row("date", "type", "custom_name", "value", "unit", "note")
        db.bodyDao().all().forEach { w.row(it.date, it.type, it.customName, it.value, when { it.type.name == "WEIGHT" -> "kg"; it.type.name == "BODY_FAT" -> "%"; else -> "cm" }, it.note) }
    }

    /** Writes a ZIP containing one CSV per data set. */
    suspend fun writeZip(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            val files: List<Pair<String, suspend (Writer) -> Unit>> = listOf(
                "food_diary.csv" to ::writeDiary, "daily_totals.csv" to ::writeDailyTotals, "recipes.csv" to ::writeRecipes,
                "meal_plans.csv" to ::writePlans, "water.csv" to ::writeWater, "body_measurements.csv" to ::writeBody,
            )
            for ((name, writer) in files) {
                zip.putNextEntry(ZipEntry(name))
                val w = zip.writer(Charsets.UTF_8)
                writer(w); w.flush()
                zip.closeEntry()
            }
        }
    }
}
