package com.fdc.nutrition.data.remote

import com.fdc.nutrition.data.repository.FoodDraft
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * USDA FoodData Central search API (public domain data). Uses the user's API key if configured,
 * otherwise the rate-limited DEMO_KEY. Results are imported on demand and cached locally.
 */
@Singleton
class UsdaFdcProvider @Inject constructor(
    private val client: OkHttpClient,
    private val profile: ProfileRepository,
    @Named("usdaBaseUrl") private val baseUrl: String,
) : FoodDataProvider {
    override val source = FoodSource.USDA_FDC_API
    override val attribution = "USDA FoodData Central (public domain)"
    @Volatile private var lastRequestAt = 0L

    override suspend fun search(query: String): Result<List<FoodDraft>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext Result.success(emptyList())
        val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastRequestAt = System.currentTimeMillis()
        val key = profile.preferences().usdaApiKey?.ifBlank { null } ?: "DEMO_KEY"
        val url = "${baseUrl.trimEnd('/')}/fdc/v1/foods/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", q).addQueryParameter("pageSize", "25")
            .addQueryParameter("dataType", "Foundation,SR Legacy,Survey (FNDDS),Branded")
            .addQueryParameter("api_key", key).build()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                when {
                    resp.code == 429 -> Result.failure(IOException("USDA API rate limit reached. Add your own free API key in Settings."))
                    !resp.isSuccessful -> Result.failure(IOException("USDA API returned HTTP ${resp.code}."))
                    else -> Result.success(parse(resp.body.string()))
                }
            }
        } catch (e: IOException) {
            Result.failure(OfflineException("Online search unavailable (${e.message ?: "network error"})."))
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 1500L
        private val json = Json { ignoreUnknownKeys = true }
        private val IDS = mapOf(
            1008 to Nutrient.ENERGY, 2047 to Nutrient.ENERGY, 1003 to Nutrient.PROTEIN, 1005 to Nutrient.CARBOHYDRATE, 1004 to Nutrient.FAT,
            1258 to Nutrient.SATURATED_FAT, 1257 to Nutrient.TRANS_FAT, 1079 to Nutrient.FIBRE, 2000 to Nutrient.SUGARS, 1235 to Nutrient.ADDED_SUGARS,
            1253 to Nutrient.CHOLESTEROL, 1093 to Nutrient.SODIUM, 1092 to Nutrient.POTASSIUM, 1087 to Nutrient.CALCIUM, 1089 to Nutrient.IRON,
            1090 to Nutrient.MAGNESIUM, 1095 to Nutrient.ZINC, 1106 to Nutrient.VITAMIN_A, 1162 to Nutrient.VITAMIN_C, 1114 to Nutrient.VITAMIN_D,
            1109 to Nutrient.VITAMIN_E, 1185 to Nutrient.VITAMIN_K, 1178 to Nutrient.VITAMIN_B12, 1190 to Nutrient.FOLATE,
        )

        fun parse(body: String): List<FoodDraft> {
            val root = json.parseToJsonElement(body).jsonObject
            return root["foods"]?.jsonArray.orEmpty().mapNotNull { el ->
                val o = el.jsonObject
                val fdcId = o["fdcId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = o["description"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val type = o["dataType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val values = mutableMapOf<Nutrient, Double>()
                o["foodNutrients"]?.jsonArray.orEmpty().forEach { n ->
                    val no = n.jsonObject
                    val id = no["nutrientId"]?.jsonPrimitive?.intOrNull ?: return@forEach
                    val v = no["value"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
                    val unit = no["unitName"]?.jsonPrimitive?.contentOrNull?.uppercase()
                    val nutrient = IDS[id] ?: return@forEach
                    if (nutrient == Nutrient.ENERGY && unit == "KJ") return@forEach
                    if (nutrient == Nutrient.ENERGY && id == 2047 && values.containsKey(Nutrient.ENERGY)) return@forEach
                    values[nutrient] = v
                }
                if (Nutrient.ENERGY !in values) return@mapNotNull null
                val branded = type == "Branded"
                val upc = o["gtinUpc"]?.jsonPrimitive?.contentOrNull?.takeIf { it.matches(Regex("\\d{6,14}")) }
                val servingSize = o["servingSize"]?.jsonPrimitive?.doubleOrNull
                val servingUnit = o["servingSizeUnit"]?.jsonPrimitive?.contentOrNull?.lowercase()
                val liquid = servingUnit == "ml"
                val servings = if (servingSize != null && servingSize > 0 && servingUnit in setOf("g", "ml", "grm", "mlt"))
                    listOf(ServingSize("usda-serving", QuantityUnit.SERVING, "1 serving (${o["householdServingFullText"]?.jsonPrimitive?.contentOrNull ?: "${servingSize.toInt()} $servingUnit"})", servingSize, true, source = "USDA_FDC_API"))
                else emptyList()
                FoodDraft(
                    name = name.lowercase().replaceFirstChar { it.uppercase() }.takeIf { branded } ?: name,
                    brand = (o["brandName"] ?: o["brandOwner"])?.jsonPrimitive?.contentOrNull,
                    categoryId = if (branded) "packaged" else "international", basis = if (liquid) NutrientBasis.MILLILITRE else NutrientBasis.GRAM,
                    nutrientsPer100 = Nutrients(values), servings = servings, barcode = upc, source = FoodSource.USDA_FDC_API, sourceRef = fdcId,
                    dataQuality = if (branded) DataQuality.BRANDED_LABEL else DataQuality.VERIFIED_DATABASE,
                    notes = "USDA FoodData Central $type, FDC ID $fdcId (public domain).",
                )
            }
        }
    }
}
