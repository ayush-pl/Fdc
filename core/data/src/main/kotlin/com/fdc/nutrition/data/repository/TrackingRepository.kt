package com.fdc.nutrition.data.repository

import androidx.room.withTransaction
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.toDomain
import com.fdc.nutrition.data.db.toEntity
import com.fdc.nutrition.data.sync.ChangeTracker
import com.fdc.nutrition.data.sync.EntityType
import com.fdc.nutrition.data.util.TimeProvider
import com.fdc.nutrition.domain.calc.Ids
import com.fdc.nutrition.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaterRepository @Inject constructor(private val db: AppDatabase, private val time: TimeProvider, private val changes: ChangeTracker) {
    private val dao = db.waterDao()
    fun observeForDate(date: LocalDate): Flow<List<WaterEntry>> = dao.observeForDate(date).map { l -> l.map { it.toDomain() } }
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<WaterEntry>> = dao.observeRange(from, to).map { l -> l.map { it.toDomain() } }
    suspend fun range(from: LocalDate, to: LocalDate) = dao.range(from, to).map { it.toDomain() }

    suspend fun add(date: LocalDate, amountMl: Double, beverage: BeverageType = BeverageType.WATER): String {
        if (!(amountMl > 0 && amountMl <= 5000)) throw ValidationException("Amount must be between 1 and 5,000 ml.")
        val e = WaterEntry(Ids.new(), date, amountMl, beverage, time.now())
        db.withTransaction { dao.upsert(e.toEntity()); changes.upserted(EntityType.WATER, e.id) }
        return e.id
    }

    suspend fun remove(id: String) = db.withTransaction { dao.delete(id); changes.deleted(EntityType.WATER, id) }

    companion object {
        /** Hydration total: plain water only, unless the user opted to count all beverages. */
        fun hydrationTotal(entries: List<WaterEntry>, countBeverages: Boolean): Double =
            entries.filter { countBeverages || it.beverage == BeverageType.WATER }.sumOf { it.amountMl }

        fun dailyTotals(entries: List<WaterEntry>, countBeverages: Boolean): Map<LocalDate, Double> =
            entries.groupBy { it.date }.mapValues { hydrationTotal(it.value, countBeverages) }.filterValues { it > 0 }
    }
}

@Singleton
class BodyRepository @Inject constructor(private val db: AppDatabase, private val time: TimeProvider, private val changes: ChangeTracker) {
    private val dao = db.bodyDao()
    fun observeAll(): Flow<List<BodyMeasurement>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    fun observeWeights(): Flow<List<BodyMeasurement>> = dao.observeWeights().map { l -> l.map { it.toDomain() } }

    fun validate(type: MeasurementType, value: Double, date: LocalDate, customName: String?): List<String> = buildList {
        when (type) {
            MeasurementType.WEIGHT -> if (value !in 20.0..400.0) add("Weight must be between 20 and 400 kg.")
            MeasurementType.BODY_FAT -> if (value !in 2.0..70.0) add("Body fat must be between 2% and 70%.")
            else -> if (value !in 5.0..300.0) add("Measurement must be between 5 and 300 cm.")
        }
        if (date.isAfter(time.today())) add("Date can't be in the future.")
        if (type == MeasurementType.CUSTOM && customName.isNullOrBlank()) add("Name the custom measurement.")
    }

    /** Saves a record; values are canonical (kg / cm / %). */
    suspend fun save(id: String?, date: LocalDate, type: MeasurementType, value: Double, customName: String? = null, note: String? = null): String {
        val errors = validate(type, value, date, customName)
        if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("\n"))
        val now = time.now()
        val existing = id?.let { dao.get(it) }
        val m = BodyMeasurement(existing?.id ?: Ids.new(), date, type, customName?.trim()?.ifBlank { null }, value, note?.ifBlank { null }, existing?.createdAt ?: now, now)
        db.withTransaction {
            dao.upsert(m.toEntity())
            changes.upserted(EntityType.BODY, m.id)
            if (type == MeasurementType.WEIGHT) syncProfileWeight()
        }
        return m.id
    }

    suspend fun delete(id: String) = db.withTransaction {
        val m = dao.get(id)
        dao.delete(id); changes.deleted(EntityType.BODY, id)
        if (m?.type == MeasurementType.WEIGHT) syncProfileWeight()
    }

    /** Keeps the profile's "current weight" equal to the latest weight record. */
    private suspend fun syncProfileWeight() {
        val p = db.profileDao().profile() ?: return
        val latest = dao.latestWeight()?.value
        if (latest != p.currentWeightKg) db.profileDao().upsertProfile(p.copy(currentWeightKg = latest, updatedAt = time.now()))
    }
}
