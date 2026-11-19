package com.fdc.nutrition.data.di

import android.content.Context
import androidx.room.Room
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides @Named("offBaseUrl") fun offBaseUrl(): String = "https://world.openfoodfacts.org"
    @Provides @Named("usdaBaseUrl") fun usdaBaseUrl(): String = "https://api.nal.usda.gov"
}
