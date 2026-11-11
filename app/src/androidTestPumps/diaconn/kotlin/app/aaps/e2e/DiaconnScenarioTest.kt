package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.pump.diaconn.emulator.DiaconnEmulatorBleTransport
import app.aaps.pump.diaconn.emulator.DiaconnPumpState
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Diaconn G8 driver against its emulator, through the real command queue. See [AbstractPumpScenarioTest]. The tests
 * here are for what only the G8 does.
 */
@RunWith(AndroidJUnit4::class)
class DiaconnScenarioTest : AbstractPumpScenarioTest() {

    override fun pumpUnderTest(): PumpUnderTest = DiaconnPumpUnderTest()

    private val pumpState: DiaconnPumpState get() = (scenario.control as DiaconnEmulatorBleTransport).pumpState

    @Ignore("The report a real G8 sends for an empty reservoir was never seen, so the emulator does not send one")
    @Test
    override fun anEmptyReservoirRaisesAnAlarm() {
    }

    /**
     * The pump numbers its log 0..9999 and then starts again with a higher wrap count. AAPS records a bolus only from
     * the pump log, and the driver read the old wrap to its end and then nothing more: every bolus after it was lost.
     */
    @Test
    fun bolusesAfterThePumpLogWrapsAreRecorded() {
        scenario.bringUp()
        pumpState.nextLogNumber = LOG_NEAR_ITS_END
        scenario.pair()
        repeat(BOLUSES) {
            assertWithMessage("bolus ${it + 1}").that(scenario.bolus(BOLUS).success).isTrue()
        }
        assertWithMessage("the pump log wrapped").that(pumpState.wrappingCount).isGreaterThan(0)
        scenario.readStatus()
        scenario.checkBolusRecords()
    }

    /**
     * The G8 runs a temp basal in whole 30-minute steps (its log holds 2..96 quarters of an hour). A 15-minute one was
     * sent as 30 minutes and a 45-minute one as 60, but stored as asked: AAPS counted the base rate while the pump still
     * gave the temp basal.
     */
    @Test
    fun aTempBasalIsStoredAsLongAsThePumpRunsIt() {
        scenario.bringUp()
        scenario.pair()
        for (minutes in listOf(SHORT_MINUTES, ODD_MINUTES)) {
            assertWithMessage("temp basal of $minutes min").that(scenario.tempBasal(RATE, minutes).success).isTrue()
            // The driver records a temp basal from the pump log, which a status read reads.
            scenario.readStatus()
            val stored = checkNotNull(runBlocking { testGraphs.persistenceLayer.getTemporaryBasalActiveAt(System.currentTimeMillis()) }) {
                "AAPS has no temp basal after one of $minutes min"
            }
            val onPump = pumpState.tempBasalTime * QUARTER_HOUR_MS
            assertWithMessage("asked $minutes min: AAPS stores ${stored.duration / MINUTE_MS} min, the pump runs ${onPump / MINUTE_MS} min")
                .that(stored.duration).isEqualTo(onPump)
        }
        scenario.checkTempBasal()
    }

    /**
     * A history read with new records reports success. The pump answers a log page with a number of its own, not the
     * number of the request, so the answer woke no request: each page waited its whole 2 s and counted as lost, and
     * the read failed while it had stored every record.
     */
    @Test
    fun aHistoryReadWithNewRecordsSucceeds() {
        scenario.bringUp()
        scenario.pair()
        // Records the driver has not read yet.
        assertWithMessage("bolus on the pump").that(scenario.control.bolusOnPump(BOLUS)).isTrue()

        assertWithMessage("history read").that(scenario.readTdds().success).isTrue()
    }

    /**
     * A profile write is taken at the first try. A real G8 refuses the start of a written basal for a while after the
     * write, also right after its basal setting report (2026-11-12): a driver that started it on that report had every
     * write fail, and the pump ran the new rates while AAPS kept the old profile.
     */
    @Test
    fun aProfileWriteIsTakenAtTheFirstTry() {
        scenario.bringUp()
        scenario.pair()

        assertWithMessage("the write of the profile").that(scenario.profileWrittenOnce(PROFILE_PERCENT)).isTrue()
    }

    private companion object {

        const val PROFILE_PERCENT = 110

        /** Five records before the end of the log, so a few boluses wrap it. */
        const val LOG_NEAR_ITS_END = 9995
        const val BOLUSES = 7
        const val BOLUS = 0.5

        /** Above the base rate of the test profile: a temp basal at the base rate is no temp basal. */
        const val RATE = 1.0
        const val SHORT_MINUTES = 15
        const val ODD_MINUTES = 45
        const val MINUTE_MS = 60_000L
        const val QUARTER_HOUR_MS = 15 * MINUTE_MS
    }
}
