package com.fdc.nutrition.data.repository

import androidx.room.withTransaction
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.entity.ReminderEntity
import com.fdc.nutrition.data.db.entity.ReminderType
import com.fdc.nutrition.data.db.toDomain
import com.fdc.nutrition.data.db.toEntity
import com.fdc.nutrition.data.sync.ChangeTracker
import com.fdc.nutrition.data.sync.EntityType
import com.fdc.nutrition.data.util.TimeProvider
import com.fdc.nutrition.domain.calc.EnergyEstimate
import com.fdc.nutrition.domain.calc.EnergyInputs
import com.fdc.nutrition.domain.calc.Ids
import com.fdc.nutrition.domain.calc.TargetCalculator
import com.fdc.nutrition.domain.calc.effectiveOn
import com.fdc.nutrition.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class TargetProposal(val estimate: EnergyEstimate, val targets: Map<Nutrient, Double>, val waterMl: Double, val split: MacroSplit)

@Singleton
class ProfileRepository @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeProvider,
    private val changes: ChangeTracker,
) {
    private val dao = db.profileDao()

    fun observeProfile(): Flow<UserProfile?> = dao.observeProfile().map { it?.toDomain() }
    suspend fun profile(): UserProfile? = dao.profile()?.toDomain()
    fun observePreferences(): Flow<UserPreferences> = dao.observePreferences().map { it.toDomain() }
    suspend fun preferences(): UserPreferences = dao.preferences().toDomain()
    fun observeTargets(): Flow<List<NutritionTargets>> = dao.observeTargets().map { l -> l.map { it.toDomain() } }
    suspend fun targets(): List<NutritionTargets> = dao.targets().map { it.toDomain() }
    fun observeTargetOn(date: LocalDate): Flow<NutritionTargets?> = observeTargets().map { it.effectiveOn(date) }
    fun observeCurrentTarget(): Flow<NutritionTargets?> = observeTargets().map { it.effectiveOn(time.today()) }

    fun validateProfile(p: UserProfile): List<String> = buildList {
        if (p.displayName.isBlank()) add("Please enter a name.")
        p.heightCm?.let { if (it !in 50.0..250.0) add("Height must be between 50 and 250 cm.") }
        listOfNotNull(p.currentWeightKg, p.targetWeightKg, p.startWeightKg).forEach { if (it !in 20.0..400.0) add("Weights must be between 20 and 400 kg.") }
        p.dateOfBirth?.let { if (it.isAfter(time.today()) || it.isBefore(time.today().minusYears(120))) add("Please enter a valid date of birth.") }
        if (p.weeklyChangeKg !in 0.0..1.0) add("Weekly change must be between 0 and 1 kg.")
    }.distinct()

    /** Saves the profile. A changed current weight is also recorded as a dated body-weight entry. */
    suspend fun saveProfile(p: UserProfile) {
        val errors = validateProfile(p)
        if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("\n"))
        db.withTransaction {
            val old = dao.profile()
            val now = time.now()
            val profile = p.copy(createdAt = old?.createdAt ?: now, updatedAt = now, startWeightKg = p.startWeightKg ?: old?.startWeightKg ?: p.currentWeightKg)
            dao.upsertProfile(profile.toEntity())
            changes.upserted(EntityType.PROFILE, "me")
            val w = p.currentWeightKg
            if (w != null && w != old?.currentWeightKg) {
                val id = Ids.new()
                db.bodyDao().upsert(com.fdc.nutrition.data.db.entity.BodyMeasurementEntity(id, time.today(), MeasurementType.WEIGHT, null, w, null, now, now))
                changes.upserted(EntityType.BODY, id)
            }
        }
    }

    suspend fun savePreferences(p: UserPreferences) {
        if (p.waterQuickAmountsMl.any { it !in 10..5000 }) throw ValidationException("Quick water amounts must be 10–5000 ml.")
        dao.upsertPreferences(p.toEntity())
        changes.enabled = p.syncEnabled
    }

    suspend fun updatePreferences(f: (UserPreferences) -> UserPreferences) = savePreferences(f(preferences()))

    /** Calculates suggested targets from the profile. Returns null if required data is missing. */
    fun propose(p: UserProfile, split: MacroSplit = MacroSplit.DEFAULT): Result<TargetProposal> = runCatching {
        val age = p.ageOn(time.today()) ?: throw ValidationException("Date of birth is required to estimate energy needs.")
        val h = p.heightCm ?: throw ValidationException("Height is required.")
        val w = p.currentWeightKg ?: throw ValidationException("Current weight is required.")
        val est = TargetCalculator.estimate(EnergyInputs(age, p.sex, h, w, p.activityLevel, p.goal, p.weeklyChangeKg))
        val macros = TargetCalculator.macrosFromSplit(est.targetKcal, split)
        val values = TargetCalculator.defaultNutrientTargets(est.targetKcal, p.sex, age) + mapOf(
            Nutrient.ENERGY to est.targetKcal, Nutrient.PROTEIN to macros.proteinG, Nutrient.CARBOHYDRATE to macros.carbG, Nutrient.FAT to macros.fatG,
        )
        TargetProposal(est, values, TargetCalculator.defaultWaterMl(p.sex), split)
    }

    fun validateTargets(values: Map<Nutrient, Double>, waterMl: Double, method: MacroMethod, split: MacroSplit?): List<String> = buildList {
        val kcal = values[Nutrient.ENERGY]
        if (kcal == null || kcal !in 800.0..6000.0) add("Calorie target must be between 800 and 6,000 kcal.")
        values.forEach { (n, v) -> if (v < 0 || !v.isFinite()) add("${n.displayName} target must be zero or more.") }
        if (waterMl !in 0.0..10000.0) add("Water target must be between 0 and 10,000 ml.")
        if (method == MacroMethod.PERCENTAGE && (split == null || !split.isValid)) add("Macro percentages must add up to 100%.")
        if (method == MacroMethod.PERCENTAGE && split != null && split.isValid && kcal != null) {
            val m = TargetCalculator.macrosFromSplit(kcal, split)
            if (kotlin.math.abs((values[Nutrient.PROTEIN] ?: 0.0) - m.proteinG) > 0.5 || kotlin.math.abs((values[Nutrient.CARBOHYDRATE] ?: 0.0) - m.carbG) > 0.5 || kotlin.math.abs((values[Nutrient.FAT] ?: 0.0) - m.fatG) > 0.5)
                add("Macro grams must match the percentage split.")
        }
    }.distinct()

    /** Saves a new target version effective from [from] (default today). Older days keep their old targets. */
    suspend fun saveTargets(values: Map<Nutrient, Double>, waterMl: Double, method: MacroMethod, split: MacroSplit?, origin: TargetOrigin, from: LocalDate = time.today()): String {
        val errors = validateTargets(values, waterMl, method, split)
        if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("\n"))
        val existing = dao.targetFor(from)
        val t = NutritionTargets(existing?.id ?: Ids.new(), from, values, waterMl, method, split, origin, time.now())
        db.withTransaction { dao.upsertTarget(t.toEntity()); changes.upserted(EntityType.TARGET, t.id) }
        return t.id
    }

    fun observeReminders(): Flow<List<ReminderEntity>> = dao.observeReminders().map { l -> l.sortedBy { it.type.ordinal } }
    suspend fun reminders() = dao.reminders()
    suspend fun saveReminder(type: ReminderType, enabled: Boolean, hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) throw ValidationException("Invalid time")
        dao.upsertReminder(ReminderEntity(type, enabled, hour, minute))
    }

    fun observeIsSetUp(): Flow<Boolean> = combine(dao.observeProfile(), dao.observeTargets()) { p, t -> p != null && t.isNotEmpty() }
}
