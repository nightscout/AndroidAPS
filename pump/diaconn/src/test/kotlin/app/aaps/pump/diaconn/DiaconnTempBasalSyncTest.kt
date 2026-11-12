package app.aaps.pump.diaconn

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.invocation.Invocation
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer

class DiaconnTempBasalSyncTest : TestBaseWithProfile() {

    private lateinit var pumpSync: PumpSync

    @BeforeEach
    fun setup() {
        // The sync calls are suspend functions that return Boolean: a bare mock gives null for them.
        pumpSync = mock(defaultAnswer = Answer { false })
    }

    private suspend fun aapsHas(temporaryBasal: PumpSync.PumpState.TemporaryBasal?) {
        whenever(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(temporaryBasal, null, null, null, SERIAL))
    }

    private suspend fun follow(pumpRuns: Boolean, notBefore: Long) =
        DiaconnTempBasalSync.followStatus(pumpSync, aapsLogger, pumpRuns, PUMP_START, PUMP_END, RATE, notBefore, NOW, SERIAL)

    // The JVM name of a method with a value class parameter (the rate) has a suffix.
    private fun calls(name: String): List<Invocation> = mockingDetails(pumpSync).invocations.filter { it.method.name.startsWith(name) }

    private fun recorded(type: PumpSync.TemporaryBasalType, start: Long = PUMP_START) =
        PumpSync.PumpState.TemporaryBasal(start, T_30_MIN, RATE, true, type, 1L, start)

    // A real G8 went back to its temp basal after a stop on the pump. AAPS had cut it at the stop.
    @Test
    fun aTempBasalThePumpRunsAgainAfterAStopIsRecordedFromTheRestart() = runTest {
        aapsHas(null)

        follow(pumpRuns = true, notBefore = RESTART)

        val sync = calls("syncTemporaryBasalWithPumpId").single()
        // timestamp, rate, duration, isAbsolute, type, pumpId, ...
        assertThat(sync.arguments[0]).isEqualTo(RESTART)
        assertThat(sync.arguments[2]).isEqualTo(PUMP_END - RESTART)
        assertThat(sync.arguments[4]).isEqualTo(PumpSync.TemporaryBasalType.NORMAL)
        assertThat(sync.arguments[5]).isEqualTo(RESTART)
    }

    // With no stop known, an earlier start could lie over a record AAPS has already.
    @Test
    fun withNoStopKnownItIsRecordedFromNow() = runTest {
        aapsHas(null)

        follow(pumpRuns = true, notBefore = 0)

        assertThat(calls("syncTemporaryBasalWithPumpId").single().arguments[0]).isEqualTo(NOW)
    }

    @Test
    fun aTempBasalThePumpDoesNotRunIsEndedNow() = runTest {
        aapsHas(recorded(PumpSync.TemporaryBasalType.NORMAL))

        follow(pumpRuns = false, notBefore = 0)

        assertThat(calls("syncStopTemporaryBasalWithPumpId").single().arguments[0]).isEqualTo(NOW)
        assertThat(calls("syncTemporaryBasalWithPumpId")).isEmpty()
    }

    // The record of the stop has its own rules.
    @Test
    fun aStopOfThePumpIsLeftAlone() = runTest {
        aapsHas(recorded(PumpSync.TemporaryBasalType.PUMP_SUSPEND))

        follow(pumpRuns = false, notBefore = 0)

        assertThat(calls("syncStopTemporaryBasalWithPumpId")).isEmpty()
        assertThat(calls("syncTemporaryBasalWithPumpId")).isEmpty()
    }

    @Test
    fun nothingChangesWhenBothAgree() = runTest {
        aapsHas(recorded(PumpSync.TemporaryBasalType.NORMAL))

        follow(pumpRuns = true, notBefore = RESTART)

        assertThat(calls("syncStopTemporaryBasalWithPumpId")).isEmpty()
        assertThat(calls("syncTemporaryBasalWithPumpId")).isEmpty()
    }

    private companion object {

        const val SERIAL = "64004"
        const val RATE = 0.8
        const val T_30_MIN = 30 * 60_000L
        const val PUMP_START = 1_794_419_550_000L
        const val PUMP_END = PUMP_START + T_30_MIN
        const val RESTART = PUMP_START + 280_000L
        const val NOW = PUMP_START + 300_000L
    }
}
