package com.fdc.nutrition.data.remote

import com.fdc.nutrition.data.repository.FoodDraft
import com.fdc.nutrition.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Open Food Facts product lookup (https://world.openfoodfacts.org, data under ODbL 1.0).
 * Only nutrient fields present on the product are imported; absent fields stay unknown.
 * Requests identify the app with a generic User-Agent, per OFF API guidelines.
 */
@Singleton
class OpenFoodFactsProvider @Inject constructor(
    private val client: OkHttpClient,
    @Named("offBaseUrl") private val baseUrl: String,
) : FoodDataProvider {
    override val source = FoodSource.OPEN_FOOD_FACTS
    override val attribution = "Product data © Open Food Facts contributors, ODbL 1.0"
    private val cache = ConcurrentHashMap<String, LookupResult>()

    override suspend fun lookupBarcode(barcode: String): LookupResult {
        val code = barcode.trim()
        if (!code.matches(Regex("\\d{6,14}"))) return LookupResult.NotFound
        cache[code]?.let { return it }
        val url = "${baseUrl.trimEnd('/')}/api/v2/product/$code.json?fields=$FIELDS"
        val result = withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { resp ->
                    when {
                        resp.code == 404 -> LookupResult.NotFound
                        resp.code == 429 -> LookupResult.Unavailable("Open Food Facts rate limit reached. Please try again in a minute.")
                        !resp.isSuccessful -> LookupResult.Unavailable("Open Food Facts returned HTTP ${resp.code}.")
                        else -> parse(code, resp.body.string())
                    }
                }
            } catch (e: IOException) {
                LookupResult.Unavailable("Online lookup unavailable (${e.message ?: "network error"}). You can enter the product manually.")
            }
        }
        if (result !is LookupResult.Unavailable) cache[code] = result
        return result
    }

    companion object {
        const val USER_AGENT = "FDCNutrition-Android/1.0 (personal nutrition tracker)"
        private const val FIELDS = "code,product_name,product_name_en,generic_name,brands,quantity,product_quantity,product_quantity_unit,serving_size,serving_quantity,serving_quantity_unit,nutriments,allergens_tags,ingredients_analysis_tags,states_tags,ingredients_text,image_front_small_url,nutrition_data_per,categories_tags"

        private val json = Json { ignoreUnknownKeys = true }

        /** Maps OFF nutriment keys (values per 100 g/ml in g, or kcal) to app nutrients and unit factors. */
        private val MAP = listOf(
            Nutrient.PROTEIN to ("proteins_100g" to 1.0), Nutrient.CARBOHYDRATE to ("carbohydrates_100g" to 1.0), Nutrient.FAT to ("fat_100g" to 1.0),
            Nutrient.SATURATED_FAT to ("saturated-fat_100g" to 1.0), Nutrient.TRANS_FAT to ("trans-fat_100g" to 1.0), Nutrient.FIBRE to ("fiber_100g" to 1.0),
            Nutrient.SUGARS to ("sugars_100g" to 1.0), Nutrient.ADDED_SUGARS to ("added-sugars_100g" to 1.0), Nutrient.CHOLESTEROL to ("cholesterol_100g" to 1000.0),
            Nutrient.POTASSIUM to ("potassium_100g" to 1000.0), Nutrient.CALCIUM to ("calcium_100g" to 1000.0), Nutrient.IRON to ("iron_100g" to 1000.0),
            Nutrient.MAGNESIUM to ("magnesium_100g" to 1000.0), Nutrient.ZINC to ("zinc_100g" to 1000.0), Nutrient.VITAMIN_A to ("vitamin-a_100g" to 1e6),
            Nutrient.VITAMIN_C to ("vitamin-c_100g" to 1000.0), Nutrient.VITAMIN_D to ("vitamin-d_100g" to 1e6), Nutrient.VITAMIN_E to ("vitamin-e_100g" to 1000.0),
            Nutrient.VITAMIN_K to ("vitamin-k_100g" to 1e6), Nutrient.VITAMIN_B12 to ("vitamin-b12_100g" to 1e6), Nutrient.FOLATE to ("folates_100g" to 1e6),
        )

        fun parse(code: String, body: String): LookupResult {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return LookupResult.Unavailable("Unexpected response from Open Food Facts.")
            if (root["status"]?.jsonPrimitive?.intOrNull == 0) return LookupResult.NotFound
            val p = root["product"]?.jsonObject ?: return LookupResult.NotFound
            fun str(k: String) = p[k]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
            fun num(o: JsonObject?, k: String): Double? = o?.get(k)?.jsonPrimitive?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }?.takeIf { it.isFinite() && it >= 0 }
            val name = str("product_name") ?: str("product_name_en") ?: str("generic_name")
            val nut = p["nutriments"]?.jsonObject
            val values = mutableMapOf<Nutrient, Double>()
            (num(nut, "energy-kcal_100g") ?: num(nut, "energy-kj_100g")?.let { it / 4.184 })?.let { values[Nutrient.ENERGY] = it }
            (num(nut, "sodium_100g") ?: num(nut, "salt_100g")?.let { it / 2.5 })?.let { values[Nutrient.SODIUM] = it * 1000.0 }
            MAP.forEach { (n, kv) -> num(nut, kv.first)?.let { values[n] = it * kv.second } }
            if (name == null && values.isEmpty()) return LookupResult.NotFound

            val quantityText = str("quantity")?.lowercase().orEmpty()
            val qtyUnit = str("product_quantity_unit")?.lowercase()
            val liquid = qtyUnit == "ml" || Regex("\\d\\s*(ml|cl|l)\\b").containsMatchIn(quantityText) || str("nutrition_data_per") == "100ml"
            val basis = if (liquid) NutrientBasis.MILLILITRE else NutrientBasis.GRAM
            val servings = mutableListOf<ServingSize>()
            num(p.jsonObject, "serving_quantity")?.takeIf { it > 0 }?.let {
                servings += ServingSize("off-serving", QuantityUnit.SERVING, "1 serving (${str("serving_size") ?: "${it.toInt()} ${basis.symbol}"})", it, isDefault = true, portionKind = PortionKind.AS_LISTED, source = "OPEN_FOOD_FACTS")
            }
            num(p.jsonObject, "product_quantity")?.takeIf { it > 0 && it < 5000 }?.let {
                servings += ServingSize("off-pack", QuantityUnit.PIECE, "1 pack (${it.toInt()} ${basis.symbol})", it, isDefault = servings.isEmpty(), portionKind = PortionKind.AS_LISTED, source = "OPEN_FOOD_FACTS")
            }
            val allergenTags = p["allergens_tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.substringAfter(':') }?.toSet().orEmpty()
            val states = p["states_tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty()
            val allergenKnown = allergenTags.isNotEmpty() || ("en:ingredients-completed" in states && !str("ingredients_text").isNullOrBlank())
            val analysis = p["ingredients_analysis_tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty()
            val diet = when {
                "en:vegan" in analysis -> DietClass.VEGAN
                "en:non-vegetarian" in analysis -> DietClass.NON_VEGETARIAN
                "en:vegetarian" in analysis && allergenKnown -> if ("eggs" in allergenTags) DietClass.EGG else DietClass.VEGETARIAN
                else -> DietClass.UNKNOWN
            }
            val missingCore = listOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT).filter { it !in values }
            val draft = FoodDraft(
                name = name ?: "Unnamed product $code", brand = str("brands")?.split(',')?.firstOrNull()?.trim(), categoryId = "packaged", basis = basis,
                nutrientsPer100 = Nutrients(values), servings = servings, barcode = code, dietClass = diet, allergens = allergenTags,
                allergenInfoKnown = allergenKnown, source = FoodSource.OPEN_FOOD_FACTS, sourceRef = code, dataQuality = DataQuality.BRANDED_LABEL,
                notes = "Imported from Open Food Facts (ODbL). Community-contributed label data — please verify against the package.",
            )
            return LookupResult.Found(draft, str("image_front_small_url"),
                if (missingCore.isEmpty()) null else "Missing on the product page: ${missingCore.joinToString { it.displayName.lowercase() }}. Please enter them from the label.")
        }
    }
}
