package app.aaps.pump.medtrum.util

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.Duration
import java.time.Instant

@SingleIn(AppScope::class)
@Inject
class MedtrumTimeUtil() {

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
