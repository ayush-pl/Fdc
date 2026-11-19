package com.fdc.nutrition.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit schema migrations. Every version bump must add a Migration here and a test in
 * MigrationTest; destructive fallback is intentionally NOT enabled so user data is never wiped.
 */
object Migrations {
    /** v2: index diary entries by log time (used by "recent foods" queries). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_diary_entries_logged_at` ON `diary_entries` (`logged_at`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
