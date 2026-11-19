package com.fdc.nutrition.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.fdc.nutrition.data.db.entity.FoodBarcodeEntity
import com.fdc.nutrition.data.db.entity.FoodCategoryEntity
import com.fdc.nutrition.data.db.entity.FoodEntity
import com.fdc.nutrition.data.db.entity.FoodFavouriteEntity
import com.fdc.nutrition.data.db.entity.FoodNutrientsEntity
import com.fdc.nutrition.data.db.entity.FoodServingEntity
import com.fdc.nutrition.data.db.entity.FoodWithNutrients
import com.fdc.nutrition.data.db.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface FoodDao {
    // ---- categories
    @Upsert suspend fun upsertCategories(items: List<FoodCategoryEntity>)
    @Query("SELECT * FROM food_categories ORDER BY sort_order") fun observeCategories(): Flow<List<FoodCategoryEntity>>
    @Query("SELECT * FROM food_categories ORDER BY sort_order") suspend fun categories(): List<FoodCategoryEntity>

    // ---- writes
    @Upsert suspend fun upsertFoods(items: List<FoodEntity>)
    @Upsert suspend fun upsertFood(item: FoodEntity)
    @Update suspend fun updateFood(item: FoodEntity)
    @Upsert suspend fun upsertNutrients(items: List<FoodNutrientsEntity>)
    @Upsert suspend fun upsertNutrient(item: FoodNutrientsEntity)
    @Upsert suspend fun upsertServings(items: List<FoodServingEntity>)
    @Query("DELETE FROM food_servings WHERE food_id = :foodId") suspend fun deleteServingsFor(foodId: String)
    @Query("DELETE FROM food_servings WHERE food_id IN (:foodIds) AND (source IS NULL OR source != 'USER')") suspend fun deleteSourceServingsFor(foodIds: List<String>)
    @Query("DELETE FROM food_servings WHERE id = :id") suspend fun deleteServing(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBarcode(item: FoodBarcodeEntity)
    @Query("DELETE FROM foods WHERE id = :id") suspend fun deleteFood(id: String)
    @Query("UPDATE foods SET is_archived = :archived, updated_at = :now WHERE id = :id") suspend fun setArchived(id: String, archived: Boolean, now: Instant)

    // ---- reads
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM foods JOIN food_nutrients ON food_nutrients.food_id = foods.id WHERE foods.id = :id")
    suspend fun get(id: String): FoodWithNutrients?

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM foods JOIN food_nutrients ON food_nutrients.food_id = foods.id WHERE foods.id = :id")
    fun observe(id: String): Flow<FoodWithNutrients?>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM foods JOIN food_nutrients ON food_nutrients.food_id = foods.id WHERE foods.id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<FoodWithNutrients>

    @Query("SELECT * FROM food_servings WHERE food_id = :foodId ORDER BY sort_order") suspend fun servings(foodId: String): List<FoodServingEntity>
    @Query("SELECT * FROM food_servings WHERE food_id = :foodId ORDER BY sort_order") fun observeServings(foodId: String): Flow<List<FoodServingEntity>>
    @Query("SELECT * FROM food_servings WHERE food_id IN (:foodIds) ORDER BY sort_order") suspend fun servingsFor(foodIds: List<String>): List<FoodServingEntity>
    @Query("SELECT barcode FROM food_barcodes WHERE food_id = :foodId") suspend fun barcodes(foodId: String): List<String>
    @Query("SELECT food_id FROM food_barcodes WHERE barcode = :barcode") suspend fun foodIdForBarcode(barcode: String): String?

    /** FTS prefix search; [match] is an FTS4 MATCH expression such as `"pan*" "bhur*"`. */
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """SELECT foods.*, food_nutrients.* FROM foods_fts
           JOIN foods ON foods.rowid = foods_fts.rowid
           JOIN food_nutrients ON food_nutrients.food_id = foods.id
           WHERE foods_fts MATCH :match AND foods.is_archived = 0
             AND (:categoryId IS NULL OR foods.category_id = :categoryId)
           LIMIT :limit"""
    )
    suspend fun search(match: String, categoryId: String?, limit: Int): List<FoodWithNutrients>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """SELECT * FROM foods JOIN food_nutrients ON food_nutrients.food_id = foods.id
           WHERE foods.is_archived = 0 AND foods.category_id = :categoryId
           ORDER BY foods.is_popular DESC, foods.is_custom DESC, foods.name COLLATE NOCASE LIMIT :limit"""
    )
    fun observeByCategory(categoryId: String, limit: Int): Flow<List<FoodWithNutrients>>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM foods JOIN food_nutrients ON food_nutrients.food_id = foods.id WHERE foods.is_popular = 1 AND foods.is_archived = 0 ORDER BY foods.name COLLATE NOCASE")
    fun observePopular(): Flow<List<FoodWithNutrients>>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM foods JOIN food_nutrients ON food_nutrients.food_id = foods.id WHERE foods.is_custom = 1 AND foods.is_archived = :archived ORDER BY foods.updated_at DESC")
    fun observeCustom(archived: Boolean = false): Flow<List<FoodWithNutrients>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """SELECT foods.*, food_nutrients.* FROM food_favourites
           JOIN foods ON foods.id = food_favourites.food_id
           JOIN food_nutrients ON food_nutrients.food_id = foods.id
           WHERE foods.is_archived = 0 ORDER BY food_favourites.created_at DESC"""
    )
    fun observeFavourites(): Flow<List<FoodWithNutrients>>

    @Query("SELECT food_id FROM food_favourites") fun observeFavouriteIds(): Flow<List<String>>
    @Query("SELECT EXISTS(SELECT 1 FROM food_favourites WHERE food_id = :foodId)") fun observeIsFavourite(foodId: String): Flow<Boolean>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addFavourite(item: FoodFavouriteEntity)
    @Query("DELETE FROM food_favourites WHERE food_id = :foodId") suspend fun removeFavourite(foodId: String)

    @Query("SELECT * FROM foods WHERE source = :source AND source_ref = :ref LIMIT 1") suspend fun findBySource(source: String, ref: String): FoodEntity?
    @Query("SELECT * FROM foods WHERE is_archived = 0 AND name = :name COLLATE NOCASE AND IFNULL(brand, '') = IFNULL(:brand, '') COLLATE NOCASE")
    suspend fun findByNameAndBrand(name: String, brand: String?): List<FoodEntity>

    @Query(
        """SELECT (SELECT COUNT(*) FROM diary_entries WHERE food_id = :id) + (SELECT COUNT(*) FROM recipe_ingredients WHERE food_id = :id)
           + (SELECT COUNT(*) FROM planned_meals WHERE food_id = :id) + (SELECT COUNT(*) FROM saved_meal_items WHERE food_id = :id)
           + (SELECT COUNT(*) FROM meal_plan_items WHERE food_id = :id) + (SELECT COUNT(*) FROM plan_recurrences WHERE food_id = :id)"""
    )
    suspend fun referenceCount(id: String): Int

    @Query("SELECT COUNT(*) FROM foods") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM foods WHERE is_archived = 0") fun observeCount(): Flow<Int>
    @Query("SELECT id FROM foods WHERE seed_version IS NOT NULL") suspend fun seededIds(): List<String>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM foods JOIN food_nutrients ON food_nutrients.food_id = foods.id WHERE foods.is_custom = 1")
    suspend fun allCustom(): List<FoodWithNutrients>

    // ---- search history
    @Upsert suspend fun upsertSearch(item: SearchHistoryEntity)
    @Query("SELECT * FROM search_history WHERE query = :q") suspend fun findSearch(q: String): SearchHistoryEntity?
    @Query("SELECT * FROM search_history ORDER BY last_used_at DESC LIMIT :limit") fun observeSearchHistory(limit: Int = 10): Flow<List<SearchHistoryEntity>>
    @Query("DELETE FROM search_history") suspend fun clearSearchHistory()
    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY last_used_at DESC LIMIT 50)") suspend fun trimSearchHistory()

    @Transaction
    suspend fun insertFoodGraph(food: FoodEntity, nutrients: FoodNutrientsEntity, servings: List<FoodServingEntity>, barcodes: List<FoodBarcodeEntity>) {
        upsertFood(food)
        upsertNutrient(nutrients)
        deleteServingsFor(food.id)
        upsertServings(servings)
        barcodes.forEach { upsertBarcode(it) }
    }
}
