package com.fdc.nutrition.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.remote.OpenFoodFactsProvider
import com.fdc.nutrition.data.remote.UsdaFdcProvider
import com.fdc.nutrition.data.repository.*
import com.fdc.nutrition.data.seed.FoodSeeder
import com.fdc.nutrition.data.seed.SeedAssetSource
import com.fdc.nutrition.data.sync.ChangeTracker
import com.fdc.nutrition.data.util.FixedTimeProvider
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Manually wired object graph over a real Room database for integration tests. */
class TestGraph(
    val context: Context = ApplicationProvider.getApplicationContext(),
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build(),
    offBaseUrl: String = "http://localhost:1",
    usdaBaseUrl: String = "http://localhost:1",
    val today: LocalDate = LocalDate.of(2026, 3, 10),
) {
    val time = FixedTimeProvider(Clock.fixed(Instant.parse("${today}T07:30:00Z"), ZoneId.of("UTC")))
    val changes = ChangeTracker(db, time)
    val seeder = FoodSeeder(object : SeedAssetSource(context) {
        override fun open(): java.io.InputStream = java.io.File("src/main/assets/seed/foods.json.gz").inputStream()
    }, db)
    val foods = FoodRepository(db, time, changes)
    val recipes = RecipeRepository(db, foods, time, changes)
    val diary = DiaryRepository(db, foods, recipes, time, changes)
    val profile = ProfileRepository(db, time, changes)
    val water = WaterRepository(db, time, changes)
    val body = BodyRepository(db, time, changes)
    val plans = PlanRepository(db, foods, diary, time, changes)
    val analytics = AnalyticsRepository(diary, water, body, profile, time, db)
    val http = OkHttpClient()
    val off = OpenFoodFactsProvider(http, offBaseUrl)
    val usda = UsdaFdcProvider(http, profile, usdaBaseUrl)
    val imports = ProductImportRepository(foods, off, usda)
}
