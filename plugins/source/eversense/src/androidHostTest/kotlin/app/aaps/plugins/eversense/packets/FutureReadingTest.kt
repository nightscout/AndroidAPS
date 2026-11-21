package app.aaps.plugins.eversense.packets

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.eversense.MemoryStore
import app.aaps.plugins.eversense.RecordingWatcher
import app.aaps.plugins.eversense.connection.EversenseLink
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.exceptions.EversenseWriteException
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.packets.e3.GetCurrentGlucosePacket
import app.aaps.plugins.eversense.packets.e365.GetGlucoseDataPacket
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.util.StorageKeys
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * A reading dated in the future must not be taken: as the last reading it made every real one look "not newer" until
 * the clock passed it. A time stored that way before the check existed is dropped.
 */
class FutureReadingTest {

    private val store = MemoryStore()
    private val watcher = RecordingWatcher()

    /** Answers each packet type with a prepared response; any other packet is refused, as an unknown command is. */
    private class AnsweringLink(private val answers: Map<KClass<*>, EversenseBasePacket.Response>) : EversenseLink {

        override val aapsLogger: AAPSLogger = AAPSLoggerTest()

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : EversenseBasePacket.Response> writePacket(packet: EversenseBasePacket, timeoutMs: Long): T =
            answers[packet::class] as T? ?: throw EversenseWriteException("no answer for ${packet::class.simpleName}")

        override fun disconnectAndScheduleReconnect() {}
    }

    private fun storeLastReading(datetime: Long) {
        store.values[StorageKeys.STATE] = Json.encodeToString(EversenseState().apply { recentGlucoseDatetime = datetime })
    }

    private fun lastReading(): Long =
        Json { ignoreUnknownKeys = true }.decodeFromString<EversenseState>(store.getString(StorageKeys.STATE, null) ?: "{}").recentGlucoseDatetime

    private fun reading365(datetime: Long) = AnsweringLink(
        mapOf(GetGlucoseDataPacket::class to GetGlucoseDataPacket.Response(datetime, 120, EversenseTrendArrow.entries.first(), signalStrength = 60))
    )

    private fun readingE3(datetime: Long) = AnsweringLink(
        mapOf(GetCurrentGlucosePacket::class to GetCurrentGlucosePacket.Response(datetime, 120, EversenseTrendArrow.entries.first()))
    )

    /** Unit, so JUnit 5 runs the tests that use it (it skips a test that returns a value) */
    private fun at(now: Long, body: suspend () -> Unit): Unit = PacketTime.withClock({ now }) { runTest { body() } }

    @Test fun a365ReadingFromTheFutureIsNotTaken() = at(NOW) {
        storeLastReading(NOW - FIVE_MINUTES)
        Eversense365Communicator.readGlucose(reading365(NOW + 10 * 60_000L), store, listOf(watcher))
        assertThat(watcher.reads).isEmpty()
        assertThat(lastReading()).isEqualTo(NOW - FIVE_MINUTES)
    }

    /** The transmitter's clock may be a little ahead of the phone's: a reading within the margin is real. */
    @Test fun a365ReadingSlightlyAheadIsTaken() = at(NOW) {
        storeLastReading(NOW - FIVE_MINUTES)
        Eversense365Communicator.readGlucose(reading365(NOW + 60_000L), store, listOf(watcher))
        assertThat(watcher.reads.single().single().datetime).isEqualTo(NOW + 60_000L)
    }

    @Test fun a365LastReadingStoredInTheFutureNoLongerBlocks() = at(NOW) {
        storeLastReading(NOW + 60 * 60_000L)
        Eversense365Communicator.readGlucose(reading365(NOW), store, listOf(watcher))
        assertThat(watcher.reads.single().single().datetime).isEqualTo(NOW)
        assertThat(lastReading()).isEqualTo(NOW)
    }

    @Test fun anE3ReadingFromTheFutureIsNotTaken() = at(NOW) {
        storeLastReading(NOW - FIVE_MINUTES)
        EversenseE3Communicator.readGlucose(readingE3(NOW + 10 * 60_000L), store, listOf(watcher))
        assertThat(watcher.reads).isEmpty()
        assertThat(lastReading()).isEqualTo(NOW - FIVE_MINUTES)
    }

    @Test fun anE3LastReadingStoredInTheFutureNoLongerBlocks() = at(NOW) {
        storeLastReading(NOW + 60 * 60_000L)
        EversenseE3Communicator.readGlucose(readingE3(NOW), store, listOf(watcher))
        assertThat(watcher.reads.single().single().datetime).isEqualTo(NOW)
    }

    private companion object {

        const val NOW = 1_800_000_000_000L
        const val FIVE_MINUTES = 5 * 60_000L
    }
}
