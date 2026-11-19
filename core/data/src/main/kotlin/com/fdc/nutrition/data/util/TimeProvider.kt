package com.fdc.nutrition.data.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable clock so time-dependent logic is testable. Timestamps are stored as UTC epoch millis; dates as local epoch days. */
@Singleton
open class TimeProvider @Inject constructor() {
    open val clock: Clock get() = Clock.systemDefaultZone()
    fun now(): Instant = Instant.now(clock)
    fun today(): LocalDate = LocalDate.now(clock)
    val zone: ZoneId get() = clock.zone
}

class FixedTimeProvider(private val fixed: Clock) : TimeProvider() {
    override val clock: Clock get() = fixed
}
