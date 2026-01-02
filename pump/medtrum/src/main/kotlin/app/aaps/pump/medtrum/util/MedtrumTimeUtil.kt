package app.aaps.pump.medtrum.util

import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedtrumTimeUtil @Inject constructor() {

    fun getCurrentTimePumpSeconds(): Long {
        val startInstant = Instant.parse("2014-01-01T00:00:00Z")
        val currentInstant = Instant.now()
        return Duration.between(startInstant, currentInstant).seconds
    }

    fun convertPumpTimeToSystemTimeMillis(pumpTime: Long): Long {
        val startInstant = Instant.parse("2014-01-01T00:00:00Z")
        val pumpInstant = startInstant.plusSeconds(pumpTime)
        val epochInstant = Instant.EPOCH
        return Duration.between(epochInstant, pumpInstant).seconds * 1000
    }
}
