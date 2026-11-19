package com.fdc.nutrition.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.Migrations
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Builds a genuine version-1 database from the exported Room schema (schemas/.../1.json), fills it
 * with data, then opens it with the current AppDatabase and its migrations. Room validates the
 * migrated schema against the current entities on open, so a missing/incorrect migration fails here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun createFromSchema(file: File, version: Int) {
        val schema = Json.parseToJsonElement(File("schemas/com.fdc.nutrition.data.db.AppDatabase/$version.json").readText()).jsonObject["database"]!!.jsonObject
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(file.absolutePath).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    schema["entities"]!!.jsonArray.forEach { e ->
                        val o = e.jsonObject; val table = o["tableName"]!!.jsonPrimitive.content
                        db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                        o["indices"]?.jsonArray?.forEach { i -> db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
                    }
                    schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema["identityHash"]!!.jsonPrimitive.content}')")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase.apply {
            execSQL("INSERT INTO meals (id, name, sort_order, is_default, is_archived) VALUES ('lunch','Lunch',2,1,0)")
            execSQL("INSERT INTO diary_entries (id, date, meal_id, type, food_id, recipe_id, name, brand, quantity, unit, unit_label, amount_per_unit, basis, reference_amount, s_energy_kcal, data_quality, preparation, note, planned_source_key, logged_at, created_at, updated_at) " +
                "VALUES ('e1', ${LocalDate.of(2026, 1, 5).toEpochDay()}, 'lunch', 'QUICK_ADD', NULL, NULL, 'Old entry', NULL, 1, 'SERVING', 'entry', 1, 'SERVING', 1, 640, 'USER_ENTERED', 'NOT_APPLICABLE', NULL, NULL, 1, 1, 1)")
        }
        helper.close()
    }

    @Test fun migrate1To2_keepsDataAndMatchesCurrentSchema() = runBlocking<Unit> {
        val file = File(ctx.cacheDir, "migration-test.db").also { it.delete() }
        createFromSchema(file, 1)
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, file.absolutePath).addMigrations(*Migrations.ALL).build()
        val e = db.diaryDao().get("e1")!!
        assertThat(e.name).isEqualTo("Old entry")
        assertThat(e.snapshot.energyKcal).isEqualTo(640.0)
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_diary_entries_logged_at'").use { assertThat(it.count).isEqualTo(1) }
        db.close(); file.delete()
    }
}
