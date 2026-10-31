package app.aaps.pump.eopatch.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.pump.eopatch.ble.task.FetchAlarmTask
import app.aaps.pump.eopatch.ble.task.InternalSuspendedTask
import app.aaps.pump.eopatch.ble.task.ReadBolusFinishTimeTask
import app.aaps.pump.eopatch.ble.task.ReadTempBasalFinishTimeTask
import app.aaps.pump.eopatch.vo.BolusCurrent
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchState
import app.aaps.pump.eopatch.vo.TempBasalManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The loop starts and ends its "suspended by pump" mode on a pump status event. After a resume from AAPS the patch
 * was running again, but no event came: the loop stayed suspended until its next run (2.5 minutes on a real patch).
 */
class PatchStateManagerPauseEventTest {

    private val pm: PreferenceManager = mock()
    private val normalBasalManager: NormalBasalManager = mock()
    private val rxBus: RxBus = mock()

    private lateinit var sut: PatchStateManager

    @BeforeEach
    fun setUp() {
        whenever(pm.bolusCurrent).thenReturn(mock<BolusCurrent>())
        // Started: a resume then only updates the stored state, nothing deeper.
        whenever(normalBasalManager.isStarted).thenReturn(true)
        sut = PatchStateManager(
            pm, mock<PatchConfig>(), mock<TempBasalManager>(), normalBasalManager,
            mock<ReadBolusFinishTimeTask>(), mock<ReadTempBasalFinishTimeTask>(), mock<InternalSuspendedTask>(),
            mock<FetchAlarmTask>(), mock<AAPSLogger>(), mock<AapsSchedulers>(), rxBus
        )
    }

    /** States 12 s apart, so the "same state within 10 s" shortcut does not skip the update. */
    private fun state(time: Int, paused: Boolean): PatchState = mock<PatchState>().also {
        whenever(it.currentTime()).thenReturn(time)
        whenever(it.isNormalBasalPaused).thenReturn(paused)
        whenever(it.isNormalBasalAct).thenReturn(!paused)
    }

    private fun storedState(paused: Boolean) = state(100, paused).also { whenever(pm.patchState).thenReturn(it) }

    @Test
    fun aResumeSendsAPumpStatusEvent() {
        storedState(paused = true)

        sut.updatePatchStateInner(state(112, paused = false))

        verify(rxBus).send(argThat { this is EventPumpStatusChanged })
    }

    @Test
    fun aPauseSendsAPumpStatusEvent() {
        storedState(paused = false)

        sut.updatePatchStateInner(state(112, paused = true))

        verify(rxBus).send(argThat { this is EventPumpStatusChanged })
    }

    @Test
    fun noEventWhenThePauseStateDoesNotChange() {
        storedState(paused = false)

        sut.updatePatchStateInner(state(112, paused = false))

        verify(rxBus, never()).send(any())
    }
}
