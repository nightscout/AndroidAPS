package app.aaps.plugins.source

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.source.BgSource
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * When [DexcomOnePlusWarmupBasalGuard] cancels a temp basal: every condition, one test each, plus
 * the case where all of them hold. The last tests check the pump command it starts, the user entry
 * log, and the second check right before the command.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DexcomOnePlusWarmupBasalGuardTest : TestBaseWithProfile() {

    private val loop: Loop = mock()
    private val commandQueue: CommandQueue = mock()
    private val uel: UserEntryLogger = mock()
    private val cancelResult: PumpEnactResult = mock()
    private val persistenceLayer: PersistenceLayer = mock()
    private val owner: BgSource = mock()
    private val profile: EffectiveProfile = mock()

    private lateinit var guard: DexcomOnePlusWarmupBasalGuard

    private val fixedNow = 1_000_000_000L
    private val highTemp = TB(timestamp = fixedNow - 60_000L, type = TB.Type.NORMAL, isAbsolute = true, rate = 2.0, duration = 30 * 60_000L)
    private val lowTemp = TB(timestamp = fixedNow - 60_000L, type = TB.Type.NORMAL, isAbsolute = true, rate = 0.3, duration = 30 * 60_000L)

    @BeforeEach
    fun prepareGuard() {
        guard = DexcomOnePlusWarmupBasalGuard(aapsLogger, commandQueue, uel, { loop }, activePlugin, profileFunction, persistenceLayer)
        runTest {
            // Happy path: active source, closed loop, no fresh glucose, HIGH residual temp, profile 1.0 U/h.
            whenever(activePlugin.activeBgSource).thenReturn(owner)
            whenever(loop.isEnabled()).thenReturn(true)
            whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
            whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(null)
            whenever(persistenceLayer.getTemporaryBasalActiveAt(fixedNow)).thenReturn(highTemp)
            whenever(profile.getBasal()).thenReturn(1.0)
            whenever(profileFunction.getProfile()).thenReturn(profile)
            whenever(cancelResult.success).thenReturn(true)
            whenever(commandQueue.cancelTempBasal(any(), any())).thenReturn(cancelResult)
        }
    }

    @Test
    fun `high temp, no fresh glucose, closed loop, active source → cancel`() = runTest {
        assertThat(guard.shouldCancelResidualTemp(owner, fixedNow)).isTrue()
    }

    @Test
    fun `not the active BG source → skip`() = runTest {
        whenever(activePlugin.activeBgSource).thenReturn(mock<BgSource>())
        assertThat(guard.shouldCancelResidualTemp(owner, fixedNow)).isFalse()
    }

    @Test
    fun `loop disabled → skip`() = runTest {
        whenever(loop.isEnabled()).thenReturn(false)
        assertThat(guard.shouldCancelResidualTemp(owner, fixedNow)).isFalse()
    }

    @Test
    fun `open loop → skip (do not auto-enact)`() = runTest {
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        assertThat(guard.shouldCancelResidualTemp(owner, fixedNow)).isFalse()
    }

    @Test
    fun `suspended by user → skip (never lift a protective zero-temp)`() = runTest {
        whenever(loop.runningMode()).thenReturn(RM.Mode.SUSPENDED_BY_USER)
        assertThat(guard.shouldCancelResidualTemp(owner, fixedNow)).isFalse()
    }

    @Test
    fun `recent glucose present → skip (loop owns dosing)`() = runTest {
        val recent = GV(
            timestamp = fixedNow - 2 * 60_000L,
            raw = null,
            value = 120.0,
            trendArrow = TrendArrow.FLAT,
            noise = null,
            sourceSensor = SourceSensor.UNKNOWN,
        )
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(recent)
        assertThat(guard.shouldCancelResidualTemp(owner, fixedNow)).isFalse()
    }

    @Test
    fun `no active temp → skip (already on profile)`() = runTest {
        whenever(persistenceLayer.getTemporaryBasalActiveAt(fixedNow)).thenReturn(null)
        assertThat(guard.shouldCancelResidualTemp(owner, fixedNow)).isFalse()
    }

    @Test
    fun `temp at or below profile → skip (never raise delivery)`() = runTest {
        whenever(persistenceLayer.getTemporaryBasalActiveAt(fixedNow)).thenReturn(lowTemp)
        assertThat(guard.shouldCancelResidualTemp(owner, fixedNow)).isFalse()
    }

    @Test
    fun `the cancel is the loop's own call, not forced, and is in the user entry log`() = runTest {
        guard.ensureProfileBasalDuringWarmup(owner, this) { fixedNow }
        runCurrent()

        // autoForced = false: a cancel the pump refuses must stay a failure, so AAPS keeps the temp.
        verify(commandQueue).cancelTempBasal(enforceNew = false, autoForced = false)
        verify(uel).log(Action.CANCEL_TEMP_BASAL, Sources.DexcomOnePlus)
    }

    @Test
    fun `a reading that arrives before the launched cancel runs → nothing is cancelled`() = runTest {
        guard.ensureProfileBasalDuringWarmup(owner, this) { fixedNow }
        // The first check passed and the cancel is queued. Now a fresh reading arrives and the loop
        // sets a new temp before the queued job runs.
        val fresh = GV(
            timestamp = fixedNow - 60_000L,
            raw = null,
            value = 180.0,
            trendArrow = TrendArrow.FLAT,
            noise = null,
            sourceSensor = SourceSensor.UNKNOWN,
        )
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(fresh)
        runCurrent()

        verify(commandQueue, never()).cancelTempBasal(any(), any())
        verifyNoInteractions(uel)
    }

    @Test
    fun `conditions not met at the first check → nothing is launched`() = runTest {
        whenever(persistenceLayer.getTemporaryBasalActiveAt(fixedNow)).thenReturn(lowTemp)
        guard.ensureProfileBasalDuringWarmup(owner, this) { fixedNow }
        runCurrent()

        verify(commandQueue, never()).cancelTempBasal(any(), any())
        verifyNoInteractions(uel)
    }
}
