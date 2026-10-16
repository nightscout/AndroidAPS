package app.aaps.pump.eopatch.ble.task

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.core.api.DeActivation
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.core.scan.IBleDevice
import app.aaps.pump.eopatch.vo.BolusCurrent
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import app.aaps.pump.eopatch.vo.TempBasalManager
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * #2552: between two patches no insulin is given, but AAPS counted the scheduled basal as given. A discarded patch now
 * leaves a 0 U/h PUMP_SUSPEND temp basal, which the activation of the next patch ends.
 */
class DeactivateTaskTest {

    private val serial = "JP100019950"
    private val pumpSync: PumpSync = mock()
    private val rxBus: RxBus = mock()
    private val pm: PreferenceManager = mock()
    private val deactivation: DeActivation = mock()

    private lateinit var sut: DeactivateTask

    @BeforeEach
    fun setUp() {
        val schedulers = mock<AapsSchedulers>().also { whenever(it.io).thenReturn(Schedulers.trampoline()) }
        sut = DeactivateTask(mock<StopBasalTask>(), mock<TempBasalManager>(), schedulers, pumpSync, rxBus)
        sut.aapsLogger = mock<AAPSLogger>()
        sut.pm = pm
        sut.normalBasalManager = mock<NormalBasalManager>()
        sut.patchConfig = mock<PatchConfig>().also {
            whenever(it.isActivated).thenReturn(true)
            whenever(it.patchSerialNumber).thenReturn(serial)
            whenever(it.lifecycleEvent).thenReturn(mock<PatchLifecycleEvent>())   // not shut down yet
        }
        sut.taskQueue = mock<TaskQueue>().also { whenever(it.isReady2(any())).thenReturn(Observable.just(TaskFunc.DEACTIVATE)) }
        sut.patch = mock<IBleDevice>()
        sut.deactivation = deactivation
        whenever(pm.bolusCurrent).thenReturn(mock<BolusCurrent>())
        runBlocking {
            whenever(pumpSync.syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())).thenReturn(true)
        }
    }

    private fun verifyNoDeliveryRecordedBeforeShutdown() = runBlocking {
        val order = inOrder(pumpSync, pm)
        order.verify(pumpSync).syncTemporaryBasalWithPumpId(
            any(), eq(PumpRate(0.0)), any(), eq(true), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND), any(),
            eq(PumpType.EOFLOW_EOPATCH2), eq(serial)
        )
        // The shutdown clears the serial: after it, the record would not match the active pump and be dropped.
        order.verify(pm).updatePatchLifeCycle(any())
        verify(rxBus).send(argThat { this is EventPumpStatusChanged })
    }

    @Test
    fun aDiscardedPatchLeavesAZeroTempBasal() {
        whenever(deactivation.start()).thenReturn(Single.just(PatchBooleanResponse(true)))

        sut.run(forced = false, timeout = 10_000).blockingGet()

        verifyNoDeliveryRecordedBeforeShutdown()
    }

    @Test
    fun aForcedDiscardLeavesAZeroTempBasalToo() {
        whenever(deactivation.start()).thenReturn(Single.error(RuntimeException("no answer")))

        sut.run(forced = true, timeout = 10_000).blockingGet()

        verifyNoDeliveryRecordedBeforeShutdown()
    }
}
