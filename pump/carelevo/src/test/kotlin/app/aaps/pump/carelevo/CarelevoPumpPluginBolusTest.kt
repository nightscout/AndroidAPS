package app.aaps.pump.carelevo

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.ble.commands.BolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.BolusCancelResponse
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusResponse
import app.aaps.pump.carelevo.ble.commands.ImmediateBolusCommand
import app.aaps.pump.carelevo.ble.commands.ImmediateBolusResponse
import app.aaps.pump.carelevo.ble.commands.InfusionInfoResponse
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.time.Clock
import kotlin.time.Instant

class CarelevoPumpPluginBolusTest : CarelevoPumpPluginTestBase() {

    @Test
    fun `deliverTreatment should require insulin greater than zero`() {
        val bolusInfo = DetailedBolusInfo().apply {
            insulin = 0.0
            carbs = 0.0
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { plugin.deliverTreatment(bolusInfo) }
        }
    }

    @Test
    fun `deliverTreatment should require carbs equal to zero`() {
        val bolusInfo = DetailedBolusInfo().apply {
            insulin = 1.0
            carbs = 10.0
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { plugin.deliverTreatment(bolusInfo) }
        }
    }

    @Test
    fun `deliverTreatment should return not enacted when bluetooth is disabled`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        val result = runBlocking {
            plugin.deliverTreatment(DetailedBolusInfo().apply {
                insulin = 1.0
                carbs = 0.0
            })
        }

        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `deliverTreatment should return not enacted when no patch address is stored`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        val result = runBlocking {
            plugin.deliverTreatment(DetailedBolusInfo().apply {
                insulin = 1.0
                carbs = 0.0
            })
        }

        assertThat(result.enacted).isFalse()
        assertThat(result.bolusDelivered).isWithin(0.001).of(0.0)
    }

    @Test
    fun `deliverTreatment should reject when immediate bolus is already running`() {
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 2.0, modeRaw = IMME_BOLUS_MODE))
        infusionInfoSubject.onNext(
            Optional.of(
                CarelevoInfusionInfoDomainModel(
                    immeBolusInfusionInfo = CarelevoImmeBolusInfusionInfoDomainModel(
                        infusionId = "imme-1",
                        address = "AA:BB:CC:DD:EE:FF",
                        mode = 3,
                        volume = 1.0,
                        infusionDurationSeconds = 30
                    )
                )
            )
        )

        val result = runBlocking {
            plugin.deliverTreatment(DetailedBolusInfo().apply {
                insulin = 1.0
                carbs = 0.0
            })
        }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.bolusDelivered).isWithin(0.001).of(0.0)
    }

    @Test
    fun `setExtendedBolus should succeed when the pump accepts the command`() {
        val result = runBlocking { plugin.setExtendedBolus(insulin = 1.2, durationInMinutes = 30) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
    }

    @Test
    fun `setExtendedBolus should fail when the pump rejects the command`() {
        whenever { bleSession.runSingle(any(), isA<ExtendBolusCommand>(), any()) }
            .thenReturn(ExtendBolusResponse(resultCode = 1, expectedTimeSeconds = 0))

        val result = runBlocking { plugin.setExtendedBolus(insulin = 1.2, durationInMinutes = 30) }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `cancelExtendedBolus should succeed when the pump accepts the command`() {
        val result = runBlocking { plugin.cancelExtendedBolus() }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.isTempCancel).isTrue()
    }

    @Test
    fun `setExtendedBolus should require a positive duration`() {
        // durationInMinutes == 0 would otherwise encode speed = Infinity onto the wire.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { plugin.setExtendedBolus(insulin = 1.0, durationInMinutes = 0) }
        }
    }

    @Test
    fun `deliverTreatment should continue and record the bolus when the local persist fails after the pump ACK`() {
        // Pump ACKed (resultCode==0 stubbed in the base) — insulin IS being delivered. A failed
        // local persist must NOT turn that into "nothing happened": the delivery loop still runs
        // and the bolus still reaches pumpSync, or IOB would silently lose a delivered dose.
        whenever(startImmeBolusInfusionUseCase.persistImmeBolusStarted(any(), any(), any(), any(), any(), anyOrNull())).thenReturn(false)
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 2.0), infusionInfo(bolusTotal = 2.25))

        val result = runBlocking {
            plugin.deliverTreatment(DetailedBolusInfo().apply {
                insulin = 0.25
                carbs = 0.0
            })
        }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.001).of(0.25)
        verifyBlocking(pumpSync) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @Timeout(10)
    fun `deliverTreatment should report a pump-confirmed user stop as a partial success`() {
        stopDuringStart(BolusCancelResponse(resultCode = 0, infusedAmount = 0.3))

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.001).of(0.3)
    }

    @Test
    fun `deliverTreatment should explain an over-limit reject with the deliverable amount`() {
        whenever { bleSession.runSingle(any(), isA<ImmediateBolusCommand>(), any()) }
            .thenReturn(ImmediateBolusResponse(actionId = 1, resultCode = 20, expectedCompletionSeconds = 0, remainingReservoirUnits = 2.8))
        whenever(rh.gs(eq(R.string.common_label_unit_value_dose_with_space), eq("2.80"))).thenReturn("2.80 U")
        whenever(rh.gs(eq(R.string.carelevo_bolus_rejected_exceed_limit), eq("2.80 U"))).thenReturn("over limit 2.80 U")

        val result = runBlocking {
            plugin.deliverTreatment(DetailedBolusInfo().apply {
                insulin = 3.0
                carbs = 0.0
            })
        }

        assertThat(result.success).isFalse()
        assertThat(result.comment).isEqualTo("over limit 2.80 U")
    }

    @Test
    fun `setExtendedBolus should record in pumpSync even when the local persist fails`() {
        whenever(startExtendBolusInfusionUseCase.persistExtendBolusStarted(any(), any(), any())).thenReturn(false)

        val result = runBlocking { plugin.setExtendedBolus(insulin = 1.2, durationInMinutes = 30) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        verifyBlocking(pumpSync) { syncExtendedBolusWithPumpId(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelExtendedBolus should record the stop in pumpSync even when the local persist fails`() {
        whenever(cancelExtendBolusInfusionUseCase.persistExtendBolusCancelled()).thenReturn(false)

        val result = runBlocking { plugin.cancelExtendedBolus() }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        verifyBlocking(pumpSync) { syncStopExtendedBolusWithPumpId(any(), any(), any(), any()) }
    }

    @Test
    fun `deliverTreatment should not send the bolus when stop was pressed before the patch got it`() {
        whenever(bolusProgressData.isStopPressed).thenReturn(true)

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isFalse()
        assertThat(result.bolusDelivered).isWithin(0.001).of(0.0)
        verifyBlocking(bleSession, never()) { runSingle(any(), isA<ImmediateBolusCommand>(), any()) }
        verifyBlocking(pumpSync, never()) { syncBolusWithPumpId(any(), any(), any(), any(), any(), any()) }
    }

    /** The timeout also guards the wait for the cancel: a cancel that ends before the wait starts must not block it. */
    @Test
    @Timeout(10)
    fun `deliverTreatment should record what the patch gave when the patch does not confirm the stop`() {
        stopDuringStart(BolusCancelResponse(resultCode = 1, infusedAmount = 0.0))
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 2.0), infusionInfo(bolusTotal = 2.4))

        val result = runBlocking { plugin.deliverTreatment(bolus(2.0)) }

        val amount = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync) { syncBolusWithPumpId(any(), amount.capture(), any(), any(), any(), any()) }
        assertThat(amount.firstValue.cU).isWithin(0.001).of(0.4)
        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.001).of(0.4)
        verify(cancelImmeBolusInfusionUseCase).persistImmeBolusCancelled()
    }

    @Test
    @Timeout(10)
    fun `deliverTreatment should wait while the patch still reports the bolus running`() {
        stopDuringStart(BolusCancelResponse(resultCode = 1, infusedAmount = 0.0))
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(
            infusionInfo(bolusTotal = 2.0),
            infusionInfo(bolusTotal = 2.3, modeRaw = IMME_BOLUS_MODE),
            infusionInfo(bolusTotal = 3.5)
        )

        val result = runBlocking { plugin.deliverTreatment(bolus(1.5)) }

        val amount = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync) { syncBolusWithPumpId(any(), amount.capture(), any(), any(), any(), any()) }
        assertThat(amount.firstValue.cU).isWithin(0.001).of(1.5)
        assertThat(result.bolusDelivered).isWithin(0.001).of(1.5)
        verifyBlocking(bleSession, times(3)) { readInfusionInfo(any()) }
    }

    @Test
    @Timeout(10)
    fun `deliverTreatment should record the full bolus when the patch cannot tell after a stop it did not confirm`() {
        stopDuringStart(BolusCancelResponse(resultCode = 1, infusedAmount = 0.0))
        whenever { bleSession.readInfusionInfo(any()) }
            .thenReturn(infusionInfo(bolusTotal = 2.0))
            .thenAnswer { throw IllegalStateException("unreachable") }
        whenever(rh.gs(any<TextRef>(), any())).thenReturn("2.00 U")
        whenever(rh.gs(any<Int>(), any())).thenReturn("The patch did not confirm the stop.")

        val result = runBlocking { plugin.deliverTreatment(bolus(2.0)) }

        val amount = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync) { syncBolusWithPumpId(any(), amount.capture(), any(), any(), any(), any()) }
        assertThat(amount.firstValue.cU).isWithin(0.001).of(2.0)
        assertThat(result.bolusDelivered).isWithin(0.001).of(2.0)
        assertThat(result.comment).isEqualTo("The patch did not confirm the stop.")
    }

    @Test
    @Timeout(10)
    fun `deliverTreatment should record a confirmed stop only once`() {
        stopDuringStart(BolusCancelResponse(resultCode = 0, infusedAmount = 0.3))

        runBlocking { plugin.deliverTreatment(bolus(2.0)) }

        val amount = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync, times(1)) { syncBolusWithPumpId(any(), amount.capture(), any(), any(), any(), any()) }
        assertThat(amount.firstValue.cU).isWithin(0.001).of(0.3)
        verifyBlocking(bleSession, times(1)) { readInfusionInfo(any()) }
    }

    @Test
    fun `deliverTreatment should record a bolus the app did not record and refuse the new one once`() {
        leftover(volume = 0.6, bolusTotalBefore = 12.5, bolusType = "SMB")
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 13.1))
        whenever { pumpSync.syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any()) }.thenReturn(true)
        whenever(rh.gs(R.string.carelevo_bolus_rejected_missed_bolus_recorded)).thenReturn("added")

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isFalse()
        assertThat(result.comment).isEqualTo("added")
        verifyLeftoverRecorded(amount = 0.6, type = BS.Type.SMB)
        verify(cancelImmeBolusInfusionUseCase).persistImmeBolusCancelled()
        verifyBlocking(bleSession, never()) { runSingle(any(), isA<ImmediateBolusCommand>(), any()) }
    }

    @Test
    fun `deliverTreatment should go on when the left bolus was already recorded`() {
        leftover(volume = 1.0, bolusTotalBefore = 2.0)
        // syncBolusWithPumpId answers false: the record of this pump id existed and was only updated. Then the new
        // bolus: the total before it, and after it.
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 3.0), infusionInfo(bolusTotal = 3.0), infusionInfo(bolusTotal = 3.25))

        val result = runBlocking { plugin.deliverTreatment(bolus(0.25)) }

        assertThat(result.success).isTrue()
        verifyBlocking(bleSession) { runSingle(any(), isA<ImmediateBolusCommand>(), any()) }
    }

    @Test
    fun `the status read records a left bolus from the patch total and removes its record`() {
        leftover(volume = 0.6, bolusTotalBefore = 12.5, bolusType = "SMB")
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 13.0))

        runBlocking { plugin.getPumpStatus("test") }

        // The patch gave 0.5 U of the 0.6 U (it stopped by itself, for example on an occlusion).
        verifyLeftoverRecorded(amount = 0.5, type = BS.Type.SMB)
        verify(cancelImmeBolusInfusionUseCase).persistImmeBolusCancelled()
    }

    @Test
    fun `the status read records the full dose and keeps the record while the patch still gives the bolus`() {
        leftover(volume = 0.6, bolusTotalBefore = 12.5)
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 12.8, modeRaw = IMME_BOLUS_MODE))

        runBlocking { plugin.getPumpStatus("test") }

        verifyLeftoverRecorded(amount = 0.6, type = BS.Type.NORMAL)
        verify(cancelImmeBolusInfusionUseCase, never()).persistImmeBolusCancelled()
    }

    @Test
    fun `the status read records the full dose when the patch total cannot tell`() {
        // A lower total than before the bolus: the patch counter started again.
        leftover(volume = 0.6, bolusTotalBefore = 12.5)
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 1.0))

        runBlocking { plugin.getPumpStatus("test") }

        verifyLeftoverRecorded(amount = 0.6, type = BS.Type.NORMAL)
    }

    @Test
    fun `the status read records a left bolus of an older app version at its start as a normal bolus`() {
        leftover(volume = 0.6, bolusTotalBefore = null, bolusType = null, pumpId = null, createdAt = Instant.fromEpochMilliseconds(OLD_CREATED_AT))

        runBlocking { plugin.getPumpStatus("test") }

        val amount = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(OLD_CREATED_AT), amount.capture(), eq(BS.Type.NORMAL), eq(OLD_CREATED_AT), any(), any()) }
        assertThat(amount.firstValue.cU).isWithin(0.001).of(0.6)
    }

    @Test
    fun `a finished bolus is recorded with the pump id saved at its start`() {
        whenever(dateUtil.now()).thenReturn(START_PUMP_ID)
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 2.0), infusionInfo(bolusTotal = 2.25))

        runBlocking { plugin.deliverTreatment(bolus(0.25)) }

        verify(startImmeBolusInfusionUseCase).persistImmeBolusStarted(any(), any(), any(), eq(START_PUMP_ID), eq(BS.Type.NORMAL), eq(2.0))
        verifyBlocking(pumpSync) { syncBolusWithPumpId(any(), any(), anyOrNull(), eq(START_PUMP_ID), any(), any()) }
    }

    /**
     * Found by the emulator scenario test: the patch stopped the bolus on its own, as at an occlusion, and the whole
     * dose stayed recorded, reported as given. Its bolus total tells what it gave.
     */
    @Test
    fun `a bolus the patch stopped on its own counts what the patch gave and is not reported as given`() {
        whenever(dateUtil.now()).thenReturn(START_PUMP_ID)
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 2.0), infusionInfo(bolusTotal = 2.5))
        whenever(rh.gs(any<Int>(), anyVararg())).thenReturn("0.50 U")
        whenever(rh.gs(eq(R.string.carelevo_bolus_stopped_by_patch), anyVararg())).thenReturn("stopped")

        val result = runBlocking { plugin.deliverTreatment(bolus(2.0)) }

        assertThat(result.comment).isEqualTo("stopped")
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.001).of(0.5)
        // The record of the whole dose, then the same record set to what the patch gave.
        val recorded = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync, times(2)) { syncBolusWithPumpId(any(), recorded.capture(), any(), eq(START_PUMP_ID), any(), any()) }
        assertThat(recorded.lastValue.cU).isWithin(0.001).of(0.5)
        // The patch's alarms are read at once, so the reason, as an occlusion, shows too.
        verifyBlocking(bleSession) { readActiveAlarmSnapshots(any()) }
    }

    /** Too much is the safer error: when the patch cannot tell, the whole dose stays. */
    @Test
    @Timeout(10)
    fun `a finished bolus keeps the whole dose when the patch cannot tell its total after it`() {
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 2.0)).thenAnswer { throw RuntimeException("link down") }

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.001).of(1.0)
        verifyBlocking(pumpSync, times(1)) { syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any()) }
        verifyBlocking(bleSession, never()) { readActiveAlarmSnapshots(any()) }
    }

    @Test
    @Timeout(10)
    fun `a confirmed stop is recorded at the bolus start with its own type and pump id`() {
        whenever(dateUtil.now()).thenReturn(START_PUMP_ID)
        stopDuringStart(BolusCancelResponse(resultCode = 0, infusedAmount = 0.3))

        runBlocking { plugin.deliverTreatment(bolus(2.0).apply { bolusType = BS.Type.PRIMING }) }

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(START_PUMP_ID), any(), eq(BS.Type.PRIMING), eq(START_PUMP_ID), any(), any()) }
    }

    /**
     * Found by the emulator scenario test: the patch gave the bolus, its answer was lost, and nothing was recorded. The
     * patch shows the bolus running, so it is recorded in full and saved for the correction at the next status read.
     */
    @Test
    fun `a start whose answer is lost is recorded when the patch shows the bolus running`() {
        whenever(dateUtil.now()).thenReturn(START_PUMP_ID)
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 2.0), infusionInfo(bolusTotal = 2.1, modeRaw = IMME_BOLUS_MODE))
        whenever { bleSession.runSingle(any(), isA<ImmediateBolusCommand>(), any()) }.thenAnswer { answerTimeout() }

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.001).of(1.0)
        verify(startImmeBolusInfusionUseCase).persistImmeBolusStarted(any(), eq(1.0), eq(0), eq(START_PUMP_ID), eq(BS.Type.NORMAL), eq(2.0))
        verifyBolusRecorded(pumpId = START_PUMP_ID, amount = 1.0)
    }

    @Test
    fun `a start that got no answer and that the patch did not start is not recorded`() {
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 2.0), infusionInfo(bolusTotal = 2.0))
        whenever { bleSession.runSingle(any(), isA<ImmediateBolusCommand>(), any()) }.thenAnswer { answerTimeout() }

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.bolusDelivered).isWithin(0.001).of(0.0)
        verify(startImmeBolusInfusionUseCase, never()).persistImmeBolusStarted(any(), any(), any(), any(), any(), anyOrNull())
        verifyBlocking(pumpSync, never()) { syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any()) }
    }

    /** The whole dose counts: too much is the safer error, the loop then gives less. The alarm tells the user. */
    @Test
    fun `a start that got no answer is counted in full when the patch cannot be asked`() {
        whenever(dateUtil.now()).thenReturn(START_PUMP_ID)
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 2.0)).thenAnswer { throw RuntimeException("link down") }
        whenever { bleSession.runSingle(any(), isA<ImmediateBolusCommand>(), any()) }.thenAnswer { answerTimeout() }

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.001).of(1.0)
        verifyBolusRecorded(pumpId = START_PUMP_ID, amount = 1.0)
    }

    /** A start counted in full that the patch never gave: its totals show it, and the record is set to 0 U. */
    @Test
    fun `the status read sets a left bolus to 0 U when the patch total shows nothing was given`() {
        leftover(volume = 0.6, bolusTotalBefore = 12.5)
        whenever { bleSession.readInfusionInfo(any()) }.thenReturn(infusionInfo(bolusTotal = 12.5))

        runBlocking { plugin.getPumpStatus("test") }

        verifyLeftoverRecorded(amount = 0.0, type = BS.Type.NORMAL)
        verify(cancelImmeBolusInfusionUseCase).persistImmeBolusCancelled()
    }

    private fun verifyBolusRecorded(pumpId: Long, amount: Double) {
        val recorded = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(pumpId), recorded.capture(), any(), eq(pumpId), any(), any()) }
        assertThat(recorded.firstValue.cU).isWithin(0.001).of(amount)
    }

    /** Throws a real `TimeoutCancellationException`, as the session does when the patch does not answer. */
    private fun answerTimeout(): Any = runBlocking { withTimeout(1) { delay(10_000) } }

    /** A record of an earlier bolus is left, as after the app stopped during that bolus. */
    private fun leftover(
        volume: Double,
        bolusTotalBefore: Double?,
        bolusType: String? = "NORMAL",
        pumpId: Long? = LEFTOVER_PUMP_ID,
        createdAt: Instant = Clock.System.now()
    ) {
        infusionInfoSubject.onNext(
            Optional.of(
                CarelevoInfusionInfoDomainModel(
                    immeBolusInfusionInfo = CarelevoImmeBolusInfusionInfoDomainModel(
                        infusionId = "imme-1",
                        address = "AA:BB:CC:DD:EE:FF",
                        mode = 3,
                        createdAt = createdAt,
                        volume = volume,
                        infusionDurationSeconds = 44,
                        pumpId = pumpId,
                        bolusTotalBefore = bolusTotalBefore,
                        bolusType = bolusType
                    )
                )
            )
        )
    }

    /** The left bolus is recorded at its start, under its own pump id. */
    private fun verifyLeftoverRecorded(amount: Double, type: BS.Type) {
        val recorded = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(LEFTOVER_PUMP_ID), recorded.capture(), eq(type), eq(LEFTOVER_PUMP_ID), any(), any()) }
        assertThat(recorded.firstValue.cU).isWithin(0.001).of(amount)
    }

    /** The user presses stop while the patch starts the bolus, and the patch answers the cancel with [cancelResponse]. */
    private fun stopDuringStart(cancelResponse: BolusCancelResponse) {
        whenever(bolusProgressData.state).thenReturn(MutableStateFlow<BolusProgressState?>(null))
        // Not pressed yet when the bolus is sent, pressed from then on.
        whenever(bolusProgressData.isStopPressed).thenReturn(false, true)
        whenever { bleSession.runSingle(any(), isA<BolusCancelCommand>(), any()) }.thenReturn(cancelResponse)
        whenever { bleSession.runSingle(any(), isA<ImmediateBolusCommand>(), any()) }.thenAnswer {
            plugin.stopBolusDelivering()
            ImmediateBolusResponse(actionId = 1, resultCode = 0, expectedCompletionSeconds = 1, remainingReservoirUnits = 60.0)
        }
    }

    private fun bolus(insulin: Double) = DetailedBolusInfo().apply {
        this.insulin = insulin
        carbs = 0.0
    }

    private fun infusionInfo(bolusTotal: Double, modeRaw: Int = BASAL_MODE) = InfusionInfoResponse(
        subId = 0,
        runningMinutes = 100,
        insulinRemaining = 60.0,
        infusedTotalBasalAmount = 1.0,
        infusedTotalBolusAmount = bolusTotal,
        pumpStateRaw = 0,
        modeRaw = modeRaw,
        currentInfusedProgramVolume = 0.0,
        realInfusedTime = 0
    )

    @Test
    fun `cancelExtendedBolus should fail when the session throws`() {
        whenever { bleSession.runSingle(any(), isA<ExtendBolusCancelCommand>(), any()) }
            .thenAnswer { throw IllegalStateException("failed") }

        val result = runBlocking { plugin.cancelExtendedBolus() }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    private companion object {

        // Raw infusion modes the patch reports in 0x91.
        const val BASAL_MODE = 1
        const val IMME_BOLUS_MODE = 3

        const val LEFTOVER_PUMP_ID = 1_760_000_000_000L
        const val START_PUMP_ID = 1_760_000_100_000L
        const val OLD_CREATED_AT = 1_760_000_200_000L
    }
}
