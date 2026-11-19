package com.fdc.nutrition

import com.fdc.nutrition.data.repository.DiaryRepository
import com.fdc.nutrition.data.repository.FoodRepository
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.data.seed.FoodSeeder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Access to singletons from non-injected code (used by UI tests to prepare state). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun seeder(): FoodSeeder
    fun profiles(): ProfileRepository
    fun foods(): FoodRepository
    fun diary(): DiaryRepository
}
