package app.aaps.pump.omnipod.omnipod5

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBasalCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBolusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramTempBasalCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.StopDeliveryCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.queue.command.CommandDeliverBasalCorrection
import app.aaps.pump.omnipod.common.util.mapProfileToBasalProgram
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5BleManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.InMemoryO5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.PersistedO5PodStateManager
import app.aaps.pump.omnipod.omnipod5.history.O5History
import app.aaps.pump.omnipod.omnipod5.history.database.O5HistoryDatabase
import app.aaps.pump.omnipod.omnipod5.keys.O5StringNonPreferenceKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource
import app.aaps.pump.omnipod.omnipod5.R as O5R

@OptIn(ExperimentalTime::class)
class O5DoseRecoveryTest : TestBaseWithProfile() {

    private companion object {
        const val CONCURRENCY_TIMEOUT_MS = 5_000L
        // Short wait used to prove that something is still blocked. It is a bound, not a sleep.
        const val BLOCKED_PROBE_MS = 200L
    }

    @Mock lateinit var ble: O5BleManager
    @Mock lateinit var history: O5History
    @Mock lateinit var historyDatabase: O5HistoryDatabase
    @Mock lateinit var sync: PumpSync
    @Mock lateinit var queue: CommandQueue
    @Mock lateinit var progress: BolusProgressData
    @Mock lateinit var protection: ProtectionCheck
    @Mock lateinit var preCheck: BlePreCheck

    private lateinit var state: O5PodStateManager
    private lateinit var plugin: O5PumpPlugin

    @BeforeEach
    fun setup() {
        state = InMemoryO5PodStateManager().also {
            it.podId = 12345L
            it.ltk = ByteArray(16)
            it.cumulativeBolusPulsesDelivered = 0
            it.updateFromDefaultStatusResponse(status(0, DeliveryStatus.BASAL_ACTIVE))
        }
        plugin = newPlugin()
        whenever(history.createRecord(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Single.just(7L))
        whenever(history.markSent(any())).thenReturn(Completable.complete())
        whenever(history.markSuccess(any())).thenReturn(Completable.complete())
        whenever(history.markSendingFailure(any())).thenReturn(Completable.complete())
        whenever(history.markFailure(any())).thenReturn(Completable.complete())
        whenever(history.setTotalAmountDelivered(any(), anyOrNull())).thenReturn(Completable.complete())
        whenever(ble.connect()).thenReturn(Observable.empty())
        whenever(rh.gs(R.string.omnipod_5_error_bolus_delivery_status_uncertain)).thenReturn("Bolus outcome unknown")
        whenever(rh.gs(R.string.omnipod_5_error_unresolved_dose_pending)).thenReturn("Earlier dose unknown")
        whenever(rh.gs(R.string.omnipod_5_error_setting_temp_basal_might_have_failed)).thenReturn("Temp basal outcome unknown")
        whenever(rh.gs(R.string.omnipod_5_error_setting_basal_profile_might_have_failed)).thenReturn("Profile outcome unknown")
        whenever(rh.gs(R.string.omnipod_5_error_uncertain_smb)).thenReturn("SMB outcome unknown: %s")
        runBlocking {
            whenever(sync.syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())).thenReturn(true)
            whenever(sync.syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())).thenReturn(true)
            whenever(sync.syncBolusWithPumpId(any(), any(), any(), any(), any(), any())).thenReturn(true)
        }
    }

    private fun newPlugin() = O5PumpPlugin(
            aapsLogger, rh, preferences, queue, ble, state, history, sync,
            notificationManager, pumpEnactResultProvider, progress, protection, preCheck, config, historyDatabase
        )

    private fun status(sequence: Int, delivery: DeliveryStatus, remaining: Int = 0): DefaultStatusResponse {
        val bytes = ByteBuffer.allocate(10)
            .put(0x1d.toByte())
            .put(((delivery.value.toInt() shl 4) or 8).toByte())
            .putInt((sequence shl 11) or remaining)
            .putInt(1000)
            .array()
        return DefaultStatusResponse(bytes)
    }

    private fun pendingBolus(accepted: Boolean = false, cancelled: Boolean = false) = O5PodStateManager.PendingDoseCommand(
        type = O5PodStateManager.PendingDoseType.BOLUS,
        requestedUnits = 1.0,
        bolusType = BS.Type.SMB,
        startedAt = 1000L,
        sequenceNumber = 4,
        historyId = 7L,
        bolusRecordExpected = true,
        confirmedByStatus = accepted,
        bolusCancelRequested = cancelled,
        bolusCancelSequenceNumber = if (cancelled) 6 else null,
        bolusCancelHistoryId = if (cancelled) 8L else null
    )

    private fun useBolusClock(): TestTimeSource = TestTimeSource().also { clock ->
        plugin.bolusTimeSource = clock
        plugin.bolusSleep = { clock += it.milliseconds }
    }

    private fun stubBolusDelivery(beforeSend: () -> Unit = {}, onStatus: () -> Unit = {}) {
        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            Observable.defer {
                val pending = requireNotNull(state.pendingDoseCommand)
                val response = when (command) {
                    is ProgramBolusCommand -> {
                        beforeSend()
                        status(
                            requireNotNull(pending.sequenceNumber).toInt(),
                            DeliveryStatus.BOLUS_AND_BASAL_ACTIVE,
                            (requireNotNull(pending.requestedUnits) * 20).toInt()
                        )
                    }
                    is GetStatusCommand -> {
                        onStatus()
                        status(requireNotNull(pending.sequenceNumber).toInt(), DeliveryStatus.BASAL_ACTIVE)
                    }
                    else -> error("Unexpected command: $command")
                }
                state.updateFromDefaultStatusResponse(response)
                Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
            }
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.05, 1.0, 1.05, 5.0, 10.0])
    fun `bolus progress catches up after delayed sleeps and updates`(amount: Double) = runBlocking<Unit> {
        val clock = useBolusClock()
        val start = clock.markNow()
        val frames = mutableListOf<Pair<Long, Double>>()
        var confirmationAt = 0L
        plugin.bolusSleep = { clock += (it + 125L).milliseconds }
        whenever(progress.updateProgress(any<PumpInsulin>())).thenAnswer {
            frames += start.elapsedNow().inWholeMilliseconds to it.getArgument<PumpInsulin>(0).cU
            clock += 75.milliseconds
            Unit
        }
        stubBolusDelivery(onStatus = { confirmationAt = start.elapsedNow().inWholeMilliseconds })

        val result = plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = amount })

        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.00001).of(amount)
        assertThat(frames).isNotEmpty()
        frames.forEach { (elapsedMs, delivered) ->
            assertThat(delivered).isWithin(0.00001).of((elapsedMs / 2000L) * 0.05)
            assertThat(delivered).isLessThan(amount)
        }
        val deliveryDurationMs = Math.round(amount * 20) * 2000L
        assertThat(confirmationAt).isAtLeast(deliveryDurationMs + 3000L)
        assertThat(confirmationAt).isAtMost(deliveryDurationMs + 3125L)
        verify(progress, atLeastOnce()).updateProgress(
            eq(99), eq(TextRef.AndroidRes(O5R.string.omnipod_5_checking_delivery_status)), eq(PumpInsulin(amount))
        )
        assertThat(state.pendingDoseCommand).isNull()
    }

    @Test
    fun `bolus progress excludes send preparation and includes history sync time`() = runBlocking<Unit> {
        val clock = useBolusClock()
        val start = clock.markNow()
        val frames = mutableListOf<Double>()
        var confirmationAt = 0L
        stubBolusDelivery(
            beforeSend = { clock += 7.seconds },
            onStatus = { confirmationAt = start.elapsedNow().inWholeMilliseconds }
        )
        var syncCalls = 0
        whenever(sync.syncBolusWithPumpId(any(), any(), any(), any(), any(), any())).doSuspendableAnswer {
            if (syncCalls++ == 0) clock += 10.seconds
            true
        }
        whenever(progress.updateProgress(any<PumpInsulin>())).thenAnswer {
            frames += it.getArgument<PumpInsulin>(0).cU
            Unit
        }

        plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 1.0 })

        assertThat(frames.first()).isWithin(0.00001).of(0.25)
        assertThat(confirmationAt).isEqualTo(50_000L)
        assertThat(state.pendingDoseCommand).isNull()
    }

    @Test
    fun `estimated delivery stays unconfirmed when final status cannot be read`() = runBlocking<Unit> {
        useBolusClock()
        stubBolusDelivery(onStatus = { throw IllegalStateException("Status response lost") })

        val result = plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 1.0 })

        assertThat(result.comment).isEqualTo("Bolus outcome unknown")
        assertThat(state.pendingDoseCommand).isNotNull()
        assertThat(state.lastBolusDeliveredUnits).isNull()
        verify(history, never()).markSuccess(any())
        verify(progress, atLeastOnce()).updateProgress(
            eq(99), eq(TextRef.AndroidRes(O5R.string.omnipod_5_checking_delivery_status)), eq(PumpInsulin(1.0))
        )
        verify(progress, never()).updateProgress(eq(100))
        verify(ble, times(5)).sendCommand(argThat { this is GetStatusCommand }, any())
    }

    @Test
    fun `cancellation stops the estimate and confirms the partial delivery`() = runBlocking<Unit> {
        val clock = useBolusClock()
        val start = clock.markNow()
        plugin.bolusSleep = {
            clock += it.milliseconds
            if (start.elapsedNow() >= 10.seconds) plugin.stopBolusDelivering()
        }
        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            val pending = requireNotNull(state.pendingDoseCommand)
            val response = when (command) {
                is ProgramBolusCommand -> status(requireNotNull(pending.sequenceNumber).toInt(), DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 20)
                is StopDeliveryCommand -> status(
                    requireNotNull(pending.bolusCancelSequenceNumber).toInt(), DeliveryStatus.BASAL_ACTIVE, 15
                )
                else -> error("Unexpected command: $command")
            }
            state.updateFromDefaultStatusResponse(response)
            Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
        }

        val result = plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 1.0 })

        assertThat(start.elapsedNow()).isEqualTo(10.seconds)
        assertThat(result.bolusDelivered).isWithin(0.00001).of(0.25)
        assertThat(state.pendingDoseCommand).isNull()
        verify(ble).sendCommand(argThat { this is StopDeliveryCommand }, any())
        verify(ble, never()).sendCommand(argThat { this is GetStatusCommand }, any())
    }

    @Test
    fun `basal correction uses the same pulse timing and confirmation margin`() {
        val clock = useBolusClock()
        val start = clock.markNow()
        state.activationProgress = ActivationProgress.COMPLETED
        state.basalExpected = 0.05
        stubBolusDelivery()

        val result = plugin.executeCustomCommand(CommandDeliverBasalCorrection())

        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.00001).of(0.05)
        assertThat(start.elapsedNow()).isEqualTo(5.seconds)
        assertThat(state.pendingDoseCommand).isNull()
        runBlocking {
            verify(sync, never()).syncBolusWithPumpId(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `lost temp response restores original type and delivery once`() = runBlocking<Unit> {
        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            Observable.just<PodEvent>(PodEvent.CommandSent(command))
                .concatWith(Observable.error(IllegalStateException("Response lost")))
        }
        val result = plugin.setTempBasalAbsolute(0.7, 30, false, PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND)
        assertThat(result.success).isFalse()
        val pending = state.pendingDoseCommand!!
        assertThat(pending.tempBasalType).isEqualTo(PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND)

        state.updateFromDefaultStatusResponse(status(1, DeliveryStatus.TEMP_BASAL_ACTIVE))
        joinAll(
            launch(Dispatchers.Default) { plugin.reconcilePendingDose() },
            launch(Dispatchers.Default) { plugin.reconcilePendingDose() }
        )
        assertThat(state.activeTempBasalStartTime).isEqualTo(pending.startedAt)
        assertThat(state.activeTempBasalRate).isEqualTo(0.7)
        assertThat(state.activeTempBasalDurationMinutes).isEqualTo(30.toShort())
        assertThat(state.pendingDoseCommand).isNull()
        verify(sync, times(1)).syncTemporaryBasalWithPumpId(
            eq(pending.startedAt), argThat { cU == 0.7 }, eq(1_800_000L), eq(true),
            eq(PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND), eq(7L), eq(PumpType.OMNIPOD_5), eq("12345")
        )
        verify(history).markSuccess(7L)
    }

    @Test
    fun `accepted expired temp is recorded without a running temp`() = runBlocking<Unit> {
        state.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.TEMP_BASAL_START,
            requestedRate = 0.5, requestedDurationMinutes = 30, startedAt = 1000L,
            sequenceNumber = 4, historyId = 7L, tempBasalType = PumpSync.TemporaryBasalType.NORMAL
        )
        state.updateFromDefaultStatusResponse(status(4, DeliveryStatus.BASAL_ACTIVE))
        plugin.reconcilePendingDose()
        plugin.reconcilePendingDose()
        assertThat(state.pendingDoseCommand).isNull()
        assertThat(state.activeTempBasalStartTime).isNull()
        assertThat(state.activeTempBasalRate).isNull()
        assertThat(state.activeTempBasalDurationMinutes).isNull()
        verify(sync).syncTemporaryBasalWithPumpId(
            eq(1000L), argThat { cU == 0.5 }, eq(1_800_000L), eq(true),
            eq(PumpSync.TemporaryBasalType.NORMAL), eq(7L), eq(PumpType.OMNIPOD_5), eq("12345")
        )
    }

    @Test
    fun `lost profile response restores exact program and ends suspension once`() = runBlocking<Unit> {
        state.updateFromDefaultStatusResponse(status(0, DeliveryStatus.SUSPENDED))
        val profile = mock<PumpProfile>()
        whenever(profile.getBasalValues()).thenReturn(arrayOf(Profile.ProfileValue(0, 0.7), Profile.ProfileValue(43200, 1.2)))
        whenever(ble.sendCommand(any(), any())).thenReturn(Observable.error(IllegalStateException("Response lost")))
        assertThat(plugin.setNewBasalProfile(profile).success).isFalse()
        val pending = state.pendingDoseCommand!!
        val requested = mapProfileToBasalProgram(profile, PumpType.OMNIPOD_5)
        assertThat(pending.requestedBasalProgram).isEqualTo(requested)
        state.updateFromDefaultStatusResponse(status(1, DeliveryStatus.BASAL_ACTIVE))
        plugin.reconcilePendingDose()
        plugin.reconcilePendingDose()
        assertThat(state.basalProgram).isEqualTo(requested)
        assertThat(state.deliverySuspended).isFalse()
        assertThat(state.timeZoneId).isNotNull()
        assertThat(state.pendingDoseCommand).isNull()
        verify(sync).syncStopTemporaryBasalWithPumpId(eq(pending.startedAt), eq(7L), eq(PumpType.OMNIPOD_5), eq("12345"), any())
    }

    @Test
    fun `old active basal does not confirm a rejected new profile`() = runBlocking<Unit> {
        val old = BasalProgram(listOf(BasalProgram.Segment(0, 48, 100)))
        state.basalProgram = old
        state.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.BASAL_PROGRAM, startedAt = 1000L,
            sequenceNumber = 4, historyId = 7L,
            requestedBasalProgram = BasalProgram(listOf(BasalProgram.Segment(0, 48, 120)))
        )
        state.updateFromDefaultStatusResponse(status(2, DeliveryStatus.BASAL_ACTIVE))
        plugin.reconcilePendingDose()
        assertThat(state.basalProgram).isEqualTo(old)
        assertThat(state.pendingDoseCommand).isNull()
        verify(history).markFailure(7L)
        verify(sync, never()).syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
    }

    @Test
    fun `old active basal alone leaves a profile with no acceptance evidence pending`() = runBlocking<Unit> {
        val pending = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.BASAL_PROGRAM, startedAt = 1000L,
            requestedBasalProgram = BasalProgram(listOf(BasalProgram.Segment(0, 48, 120)))
        )
        state.pendingDoseCommand = pending
        plugin.reconcilePendingDose()
        assertThat(state.pendingDoseCommand).isEqualTo(pending)
        assertThat(state.basalProgram).isNull()
    }

    @Test
    fun `rejected temp does not create a delivery record`() = runBlocking<Unit> {
        state.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.TEMP_BASAL_START,
            requestedRate = 0.5, requestedDurationMinutes = 30, startedAt = 1000L,
            sequenceNumber = 4, historyId = 7L
        )
        state.updateFromDefaultStatusResponse(status(2, DeliveryStatus.BASAL_ACTIVE))
        plugin.reconcilePendingDose()
        assertThat(state.pendingDoseCommand).isNull()
        assertThat(state.activeTempBasalRate).isNull()
        verify(sync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())
        verify(history).markFailure(7L)
    }

    @Test
    fun `cancel sequence alone cannot settle an unaccepted bolus or zero its record`() = runBlocking<Unit> {
        state.pendingDoseCommand = pendingBolus(cancelled = true)
        state.updateFromDefaultStatusResponse(status(6, DeliveryStatus.BASAL_ACTIVE, 10))
        plugin.reconcilePendingDose()
        plugin.reconcilePendingDose()
        assertThat(state.pendingDoseCommand).isNotNull()
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isFalse()
        verify(sync, never()).syncBolusWithPumpId(any(), any(), any(), any(), any(), any())
        whenever(ble.sendCommand(any(), any())).thenReturn(Observable.empty())
        val result = plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 0.5 })
        assertThat(result.success).isFalse()
        verify(ble, never()).sendCommand(argThat { this is ProgramBolusCommand }, any())
    }

    @Test
    fun `older pending bolus without saved cancel intent cannot be rejected from another sequence`() = runBlocking<Unit> {
        state.pendingDoseCommand = pendingBolus().copy(bolusCancelRequested = null)
        state.updateFromDefaultStatusResponse(status(6, DeliveryStatus.BASAL_ACTIVE, 10))
        plugin.reconcilePendingDose()
        assertThat(state.pendingDoseCommand).isNotNull()
        verify(sync, never()).syncBolusWithPumpId(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `accepted bolus survives a later cancel sequence and accounts partial delivery once`() = runBlocking<Unit> {
        state.pendingDoseCommand = pendingBolus(accepted = true, cancelled = true)
        state.updateFromDefaultStatusResponse(status(6, DeliveryStatus.BASAL_ACTIVE, 12))
        plugin.reconcilePendingDose()
        plugin.reconcilePendingDose()
        assertThat(state.lastBolusDeliveredUnits).isWithin(0.00001).of(0.4)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(8.toShort())
        assertThat(state.pendingDoseCommand).isNull()
        verify(sync).syncBolusWithPumpId(eq(1000L), argThat<PumpInsulin> { cU > 0.39 && cU < 0.41 }, eq(BS.Type.SMB), eq(7L), any(), any())
        verify(history).markSuccess(8L)
    }

    @Test
    fun `a running accepted bolus cannot be finalized from its remaining pulses`() = runBlocking<Unit> {
        state.pendingDoseCommand = pendingBolus(accepted = true)
        state.updateFromDefaultStatusResponse(status(4, DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 12))
        plugin.reconcilePendingDose()
        assertThat(state.pendingDoseCommand).isNotNull()
        assertThat(state.lastBolusDeliveredUnits).isNull()
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(8.toShort())
        verify(sync, never()).syncBolusWithPumpId(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `saved acceptance cannot finalize from an unrelated inactive status`() = runBlocking<Unit> {
        state.pendingDoseCommand = pendingBolus()
        state.updateFromDefaultStatusResponse(status(4, DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 16))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(4.toShort())
        state.updateFromDefaultStatusResponse(status(9, DeliveryStatus.BASAL_ACTIVE))
        plugin.reconcilePendingDose()
        assertThat(state.pendingDoseCommand).isNotNull()
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isTrue()
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(4.toShort())
        assertThat(state.lastBolusDeliveredUnits).isNull()
        verify(sync, never()).syncBolusWithPumpId(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `unknown delivery status cannot finalize or account a requested bolus`() = runBlocking<Unit> {
        state.pendingDoseCommand = pendingBolus()
        state.updateFromDefaultStatusResponse(status(4, DeliveryStatus.UNKNOWN))
        plugin.reconcilePendingDose()
        assertThat(state.pendingDoseCommand).isNotNull()
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(0.toShort())
        assertThat(state.lastBolusDeliveredUnits).isNull()
        verify(sync, never()).syncBolusWithPumpId(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `failed finalization retains accounted progress and retry adds no pulses`() = runBlocking<Unit> {
        state.pendingDoseCommand = pendingBolus()
        state.updateFromDefaultStatusResponse(status(4, DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 16))
        state.updateFromDefaultStatusResponse(status(4, DeliveryStatus.BASAL_ACTIVE))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(20.toShort())
        whenever(history.setTotalAmountDelivered(eq(7L), anyOrNull()))
            .thenReturn(Completable.error(IllegalStateException("Store unavailable")), Completable.complete())
        assertThrows<IllegalStateException> { runBlocking { plugin.reconcilePendingDose() } }
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(20)
        state.updateFromDefaultStatusResponse(status(4, DeliveryStatus.BASAL_ACTIVE))
        plugin.reconcilePendingDose()
        plugin.reconcilePendingDose()
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(20.toShort())
        assertThat(state.pendingDoseCommand).isNull()
        verify(sync).syncBolusWithPumpId(eq(1000L), argThat<PumpInsulin> { cU == 1.0 }, any(), eq(7L), any(), any())
    }

    @Test
    fun `status recovery waits for bolus accounting and runs after the dose lock is released`() = runBlocking<Unit> {
        val programEntered = CountDownLatch(1)
        val allowProgram = CountDownLatch(1)
        val accountingEntered = CountDownLatch(1)
        val allowAccounting = CountDownLatch(1)
        val statusStarted = CountDownLatch(1)
        val statusCommandEntered = CountDownLatch(1)

        whenever(history.setTotalAmountDelivered(any(), anyOrNull())).thenReturn(
            Completable.fromAction {
                accountingEntered.countDown()
                check(allowAccounting.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            }
        )
        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            when (command) {
                is ProgramBolusCommand -> {
                    programEntered.countDown()
                    check(allowProgram.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    val response = status(1, DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 1)
                    state.updateFromDefaultStatusResponse(response)
                    state.increaseMessageSequenceNumber()
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                is StopDeliveryCommand -> {
                    val response = status(2, DeliveryStatus.BASAL_ACTIVE)
                    state.updateFromDefaultStatusResponse(response)
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                is GetStatusCommand -> {
                    statusCommandEntered.countDown()
                    val response = status(2, DeliveryStatus.BASAL_ACTIVE)
                    state.updateFromDefaultStatusResponse(response)
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                else -> Observable.empty()
            }
        }

        val bolus = async(Dispatchers.IO) {
            plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 0.05 })
        }
        try {
            assertThat(programEntered.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            withTimeout(CONCURRENCY_TIMEOUT_MS) {
                async(Dispatchers.IO) { plugin.stopBolusDelivering() }.await()
            }
            allowProgram.countDown()
            assertThat(accountingEntered.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()

            val statusRead = async(Dispatchers.IO) {
                statusStarted.countDown()
                plugin.getPumpStatus("concurrent recovery test")
            }
            assertThat(statusStarted.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            // Simulated hung accounting holds the dose lock, so the waiting status read must time out.
            assertThrows<TimeoutCancellationException> {
                runBlocking { withTimeout(BLOCKED_PROBE_MS) { statusRead.await() } }
            }
            assertThat(statusRead.isActive).isTrue()
            assertThat(statusCommandEntered.count).isEqualTo(1L)

            allowAccounting.countDown()
            withTimeout(CONCURRENCY_TIMEOUT_MS) { bolus.await() }
            withTimeout(CONCURRENCY_TIMEOUT_MS) { statusRead.await() }
            assertThat(statusCommandEntered.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            assertThat(state.pendingDoseCommand).isNull()
        } finally {
            allowProgram.countDown()
            allowAccounting.countDown()
        }
    }

    @Test
    fun `status read times out while a hung pod command holds the dose lock and resumes after release`() = runBlocking<Unit> {
        val programEntered = CountDownLatch(1)
        val allowProgram = CountDownLatch(1)
        val statusCommandEntered = CountDownLatch(1)

        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            when (command) {
                is ProgramBolusCommand -> {
                    // Simulated hung BLE call inside the dose lock. The wait is finite and released in finally.
                    programEntered.countDown()
                    check(allowProgram.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    val response = status(1, DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 1)
                    state.updateFromDefaultStatusResponse(response)
                    state.increaseMessageSequenceNumber()
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                is StopDeliveryCommand -> {
                    val response = status(2, DeliveryStatus.BASAL_ACTIVE)
                    state.updateFromDefaultStatusResponse(response)
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                is GetStatusCommand -> {
                    statusCommandEntered.countDown()
                    val response = status(2, DeliveryStatus.BASAL_ACTIVE)
                    state.updateFromDefaultStatusResponse(response)
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                else -> Observable.empty()
            }
        }

        val bolus = async(Dispatchers.IO) {
            plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 0.05 })
        }
        try {
            assertThat(programEntered.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            val statusRead = async(Dispatchers.IO) { plugin.getPumpStatus("hung pod command test") }

            assertThrows<TimeoutCancellationException> {
                runBlocking { withTimeout(BLOCKED_PROBE_MS) { statusRead.await() } }
            }
            assertThat(statusRead.isActive).isTrue()
            assertThat(statusCommandEntered.count).isEqualTo(1L)

            // Stop the bolus so the completion wait ends early, then release the hung command.
            withTimeout(CONCURRENCY_TIMEOUT_MS) {
                async(Dispatchers.IO) { plugin.stopBolusDelivering() }.await()
            }
            allowProgram.countDown()
            withTimeout(CONCURRENCY_TIMEOUT_MS) { bolus.await() }
            withTimeout(CONCURRENCY_TIMEOUT_MS) { statusRead.await() }
            assertThat(statusCommandEntered.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            assertThat(state.pendingDoseCommand).isNull()
        } finally {
            allowProgram.countDown()
        }
    }

    @Test
    fun `BLE callback waiting for plugin status deadlocks the dose lock until externally released`() = runBlocking<Unit> {
        val allowResponse = CountDownLatch(1)
        val statusAttempted = CompletableDeferred<Deferred<Unit>>()
        val statusCommandEntered = CountDownLatch(1)
        val scopeJob = SupervisorJob()
        val testScope = CoroutineScope(scopeJob + Dispatchers.IO)

        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            when (command) {
                is ProgramBolusCommand -> Observable.defer {
                    // Start inline: getPumpStatus reaches the real dose lock before async returns.
                    val statusRead = testScope.async(start = CoroutineStart.UNDISPATCHED) {
                        plugin.getPumpStatus("BLE callback circular wait test")
                    }
                    statusRead.invokeOnCompletion { allowResponse.countDown() }
                    statusAttempted.complete(statusRead)
                    // Bolus owns doseMutex -> BLE response -> status read -> the same doseMutex.
                    check(allowResponse.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    val response = status(1, DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 1)
                    state.updateFromDefaultStatusResponse(response)
                    state.increaseMessageSequenceNumber()
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                is StopDeliveryCommand -> {
                    val response = status(2, DeliveryStatus.BASAL_ACTIVE)
                    state.updateFromDefaultStatusResponse(response)
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                is GetStatusCommand -> {
                    statusCommandEntered.countDown()
                    val response = status(2, DeliveryStatus.BASAL_ACTIVE)
                    state.updateFromDefaultStatusResponse(response)
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                else -> Observable.empty()
            }
        }

        val bolus = testScope.async {
            plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 0.05 })
        }
        try {
            val statusRead = withTimeout(CONCURRENCY_TIMEOUT_MS) { statusAttempted.await() }
            withTimeout(CONCURRENCY_TIMEOUT_MS) {
                async(Dispatchers.IO) { plugin.stopBolusDelivering() }.await()
            }
            // Only this test has a timeout. Production has no lock timeout or automatic recovery.
            assertThrows<TimeoutCancellationException> {
                runBlocking { withTimeout(BLOCKED_PROBE_MS) { statusRead.await() } }
            }
            assertThrows<TimeoutCancellationException> {
                runBlocking { withTimeout(BLOCKED_PROBE_MS) { bolus.await() } }
            }
            assertThat(statusRead.isActive).isTrue()
            assertThat(bolus.isActive).isTrue()
            assertThat(allowResponse.count).isEqualTo(1L)
            assertThat(statusCommandEntered.count).isEqualTo(1L)
            verify(sync, never()).syncBolusWithPumpId(any(), any(), any(), any(), any(), any())

            // Break the injected dependency, not the lock, and let both plugin calls finish.
            allowResponse.countDown()
            val result = withTimeout(CONCURRENCY_TIMEOUT_MS) { bolus.await() }
            withTimeout(CONCURRENCY_TIMEOUT_MS) { statusRead.await() }
            assertThat(result.success).isTrue()
            assertThat(result.bolusDelivered).isWithin(0.00001).of(0.05)
            assertThat(statusCommandEntered.count).isEqualTo(0L)
            withTimeout(CONCURRENCY_TIMEOUT_MS) { plugin.getPumpStatus("after circular wait recovery") }
            assertThat(state.pendingDoseCommand).isNull()
            assertThat(state.lastBolusDeliveredUnits).isWithin(0.00001).of(0.05)
            assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(1.toShort())
            verify(history, times(1)).setTotalAmountDelivered(eq(7L), eq(0.05))
            // The expected and final amounts update the same pump record; recovery adds neither.
            verify(sync, times(2)).syncBolusWithPumpId(
                any(), argThat<PumpInsulin> { cU == 0.05 }, any(), eq(7L), eq(PumpType.OMNIPOD_5), eq("12345")
            )
            verify(ble, times(1)).sendCommand(argThat { this is ProgramBolusCommand }, any())
            verify(ble, times(1)).sendCommand(argThat { this is StopDeliveryCommand }, any())
            verify(ble, times(2)).sendCommand(argThat { this is GetStatusCommand }, any())
        } finally {
            allowResponse.countDown()
            withTimeout(CONCURRENCY_TIMEOUT_MS) {
                scopeJob.children.toList().joinAll()
                scopeJob.cancelAndJoin()
            }
        }
        assertThat(scopeJob.isCompleted).isTrue()
    }

    @Test
    fun `bolus cancellation returns while the dose lock is held and sends a stop command`() = runBlocking<Unit> {
        val programEntered = CountDownLatch(1)
        val allowProgram = CountDownLatch(1)
        val stopCommandEntered = CountDownLatch(1)

        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            when (command) {
                is ProgramBolusCommand -> {
                    programEntered.countDown()
                    check(allowProgram.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    val response = status(1, DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 1)
                    state.updateFromDefaultStatusResponse(response)
                    state.increaseMessageSequenceNumber()
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                is StopDeliveryCommand -> {
                    stopCommandEntered.countDown()
                    val response = status(2, DeliveryStatus.BASAL_ACTIVE)
                    state.updateFromDefaultStatusResponse(response)
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                else -> Observable.empty()
            }
        }

        val bolus = async(Dispatchers.IO) {
            plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 0.05 })
        }
        try {
            assertThat(programEntered.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            withTimeout(CONCURRENCY_TIMEOUT_MS) {
                async(Dispatchers.IO) { plugin.stopBolusDelivering() }.await()
            }
            allowProgram.countDown()
            withTimeout(CONCURRENCY_TIMEOUT_MS) { bolus.await() }
            assertThat(stopCommandEntered.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            verify(ble).sendCommand(argThat { this is StopDeliveryCommand }, any())
        } finally {
            allowProgram.countDown()
        }
    }

    @Test
    fun `queued basal correction runs after temp basal releases the dose lock`() = runBlocking<Unit> {
        val queueEntered = CountDownLatch(1)
        val correctionAttempting = CountDownLatch(1)
        val correctionEntered = CountDownLatch(1)
        val allowCorrection = CountDownLatch(1)
        val queueResult = CompletableDeferred<PumpEnactResult>()
        val queueCallerReturned = CompletableDeferred<PumpEnactResult>()
        val scopeJob = SupervisorJob()
        val testScope = CoroutineScope(scopeJob + Dispatchers.IO)

        state.activationProgress = ActivationProgress.COMPLETED
        state.basalExpected = 0.05
        plugin.scope = testScope
        whenever(queue.isCustomCommandInQueue(CommandDeliverBasalCorrection::class)).thenReturn(false)
        // Model the real queue: the caller suspends until a separate worker has run the command.
        whenever(queue.customCommand(any())).doSuspendableAnswer {
            queueEntered.countDown()
            testScope.launch {
                correctionAttempting.countDown()
                queueResult.complete(
                    plugin.executeCustomCommand(CommandDeliverBasalCorrection())
                )
            }
            queueResult.await().also { queueCallerReturned.complete(it) }
        }
        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            when (command) {
                is ProgramTempBasalCommand -> {
                    val response = status(0, DeliveryStatus.TEMP_BASAL_ACTIVE)
                    state.updateFromDefaultStatusResponse(response)
                    state.basalExpected = 0.05
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                is ProgramBolusCommand -> {
                    correctionEntered.countDown()
                    check(allowCorrection.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    val response = status(state.msgSequenceNumber.toInt(), DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 1)
                    state.updateFromDefaultStatusResponse(response)
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                is StopDeliveryCommand -> {
                    val response = status(state.msgSequenceNumber.toInt(), DeliveryStatus.BASAL_ACTIVE)
                    state.updateFromDefaultStatusResponse(response)
                    Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
                }
                else -> Observable.empty()
            }
        }

        try {
            // The temp basal holds the dose lock while it enqueues the correction. It must return
            // without waiting for the queued command, or the two would deadlock.
            val tempBasalResult = withTimeout(CONCURRENCY_TIMEOUT_MS) {
                async(Dispatchers.IO) {
                    plugin.setTempBasalAbsolute(0.4, 30, false, PumpSync.TemporaryBasalType.NORMAL)
                }.await()
            }
            assertThat(tempBasalResult.success).isTrue()
            assertThat(queueEntered.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            assertThat(correctionAttempting.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()

            // The correction now owns the dose lock and is held inside the pod command.
            assertThat(correctionEntered.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            assertThrows<TimeoutCancellationException> {
                runBlocking { withTimeout(BLOCKED_PROBE_MS) { queueCallerReturned.await() } }
            }
            assertThat(queueResult.isActive).isTrue()

            withTimeout(CONCURRENCY_TIMEOUT_MS) {
                async(Dispatchers.IO) { plugin.stopBolusDelivering() }.await()
            }
            allowCorrection.countDown()
            withTimeout(CONCURRENCY_TIMEOUT_MS) { queueCallerReturned.await() }

            assertThat(state.pendingDoseCommand).isNull()
            assertThat(state.lastBolusDeliveredUnits).isWithin(0.00001).of(0.05)
            assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(0.toShort())
            verify(sync, never()).syncBolusWithPumpId(any(), any(), any(), any(), any(), any())
            verify(ble, times(1)).sendCommand(argThat { this is ProgramBolusCommand }, any())
            verify(queue, times(1)).customCommand(any())
        } finally {
            allowCorrection.countDown()
            withTimeout(CONCURRENCY_TIMEOUT_MS) {
                scopeJob.children.toList().joinAll()
                scopeJob.cancelAndJoin()
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = BS.Type::class, names = ["NORMAL", "SMB"])
    fun `normal response saves acceptance before cancellation with lost stop responses`(type: BS.Type) = runBlocking<Unit> {
        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            if (command is ProgramBolusCommand) {
                val response = status(1, DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 10)
                state.updateFromDefaultStatusResponse(response)
                state.msgSequenceNumber = 3
                plugin.stopBolusDelivering()
                Observable.just(PodEvent.CommandSent(command), PodEvent.ResponseReceived(command, response))
            } else {
                assertThat(command).isInstanceOf(StopDeliveryCommand::class.java)
                assertThat(state.pendingDoseCommand!!.confirmedByStatus).isTrue()
                assertThat(state.pendingDoseCommand!!.bolusCancelRequested).isTrue()
                assertThat(state.pendingDoseCommand!!.bolusCancelSequenceNumber).isEqualTo(3.toShort())
                Observable.error<PodEvent>(IllegalStateException("Stop response lost"))
            }
        }
        plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 0.5; it.bolusType = type })
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isTrue()
        state.updateFromDefaultStatusResponse(status(3, DeliveryStatus.BASAL_ACTIVE, 4))
        plugin.reconcilePendingDose()
        plugin.reconcilePendingDose()
        assertThat(state.lastBolusDeliveredUnits).isWithin(0.00001).of(0.3)
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(6.toShort())
        assertThat(state.pendingDoseCommand).isNull()
        verify(sync, never()).syncBolusWithPumpId(any(), argThat<PumpInsulin> { cU == 0.0 }, any(), any(), any(), any())
    }

    @Test
    fun `successful write without response keeps SMB unknown rather than accepted`() = runBlocking<Unit> {
        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            Observable.just<PodEvent>(PodEvent.CommandSent(command)).concatWith(Observable.error(IllegalStateException("Response lost")))
        }
        plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 0.5; it.bolusType = BS.Type.SMB })
        assertThat(state.pendingDoseCommand).isNotNull()
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isFalse()
        verify(notificationManager).post(
            eq(NotificationId.OMNIPOD_UNCERTAIN_SMB), any<String>(), level = any(), validMinutes = any(),
            sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
        )
        state.updateFromDefaultStatusResponse(status(1, DeliveryStatus.BASAL_ACTIVE))
        plugin.reconcilePendingDose()
        assertThat(state.lastBolusDeliveredUnits).isEqualTo(0.5)
        assertThat(state.pendingDoseCommand).isNull()
    }

    @Test
    fun `cancel requested during lost program response sends a stop but does not invent acceptance`() = runBlocking<Unit> {
        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            if (command is ProgramBolusCommand) {
                plugin.stopBolusDelivering()
                state.msgSequenceNumber = 3
                Observable.just<PodEvent>(PodEvent.CommandSent(command)).concatWith(Observable.error(IllegalStateException("Response lost")))
            } else {
                assertThat(state.pendingDoseCommand!!.bolusCancelRequested).isTrue()
                assertThat(state.pendingDoseCommand!!.bolusCancelSequenceNumber).isEqualTo(3.toShort())
                Observable.error<PodEvent>(IllegalStateException("Stop response lost"))
            }
        }
        plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 0.5 })
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isFalse()
        state.updateFromDefaultStatusResponse(status(3, DeliveryStatus.BASAL_ACTIVE, 4))
        plugin.reconcilePendingDose()
        assertThat(state.pendingDoseCommand).isNotNull()
        verify(ble).sendCommand(argThat { this is StopDeliveryCommand }, any())
        verify(sync, never()).syncBolusWithPumpId(any(), argThat<PumpInsulin> { cU == 0.0 }, any(), any(), any(), any())
    }

    @Test
    fun `normal temp and profile responses use the same confirmation state`() = runBlocking<Unit> {
        whenever(ble.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            val delivery = when (command) {
                is ProgramBasalCommand -> DeliveryStatus.BASAL_ACTIVE
                is StopDeliveryCommand -> DeliveryStatus.BASAL_ACTIVE
                else                   -> DeliveryStatus.TEMP_BASAL_ACTIVE
            }
            val response = status(state.msgSequenceNumber.toInt(), delivery)
            state.updateFromDefaultStatusResponse(response)
            Observable.just<PodEvent>(PodEvent.ResponseReceived(command, response))
        }
        assertThat(plugin.setTempBasalAbsolute(0.7, 30, false, PumpSync.TemporaryBasalType.SUPERBOLUS).success).isTrue()
        assertThat(state.activeTempBasalRate).isEqualTo(0.7)
        assertThat(state.pendingDoseCommand).isNull()
        val profile = mock<PumpProfile>()
        whenever(profile.getBasalValues()).thenReturn(arrayOf(Profile.ProfileValue(0, 1.2)))
        assertThat(plugin.setNewBasalProfile(profile).success).isTrue()
        assertThat(state.basalProgram).isEqualTo(mapProfileToBasalProgram(profile, PumpType.OMNIPOD_5))
        assertThat(state.activeTempBasalRate).isNull()
        assertThat(state.pendingDoseCommand).isNull()
    }

    @Test
    fun `failed accounting leaves accepted temp pending until it can be saved`() = runBlocking<Unit> {
        state.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.TEMP_BASAL_START,
            requestedRate = 0.5, requestedDurationMinutes = 30, startedAt = System.currentTimeMillis(),
            sequenceNumber = 4, historyId = 7L
        )
        state.updateFromDefaultStatusResponse(status(4, DeliveryStatus.TEMP_BASAL_ACTIVE))
        whenever(sync.syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenThrow(IllegalStateException("Store unavailable")).thenReturn(true)
        try {
            plugin.reconcilePendingDose()
        } catch (_: IllegalStateException) {
            // The pending marker must survive a failed database write.
        }
        assertThat(state.pendingDoseCommand!!.confirmedByStatus).isTrue()
        plugin.reconcilePendingDose()
        assertThat(state.activeTempBasalRate).isEqualTo(0.5)
        assertThat(state.pendingDoseCommand).isNull()
    }

    @Test
    fun `restart after accepted bolus and lost stop response keeps partial accounting once`() = runBlocking<Unit> {
        val storage = mock<Preferences>()
        var json: String? = null
        whenever(storage.put(eq(O5StringNonPreferenceKey.PodState), any())).thenAnswer {
            json = it.getArgument(1)
            Unit
        }
        whenever(storage.getIfExists(O5StringNonPreferenceKey.PodState)).thenAnswer { json }
        whenever(storage.get(O5StringNonPreferenceKey.PodState)).thenAnswer { json ?: "" }
        val writer = PersistedO5PodStateManager(aapsLogger, storage)
        writer.podId = 12345L
        writer.cumulativeBolusPulsesDelivered = 10
        writer.pendingDoseCommand = pendingBolus()
        writer.updateFromDefaultStatusResponse(status(4, DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, 16))
        assertThat(writer.cumulativeBolusPulsesDelivered).isEqualTo(14.toShort())
        writer.pendingDoseCommand = writer.pendingDoseCommand!!.copy(
            bolusCancelRequested = true, bolusCancelSequenceNumber = 6, bolusCancelHistoryId = 8L
        )
        state = PersistedO5PodStateManager(aapsLogger, storage)
        plugin = newPlugin()
        assertThat(state.pendingDoseCommand!!.accountedBolusPulses).isEqualTo(4)
        state.updateFromDefaultStatusResponse(status(6, DeliveryStatus.BASAL_ACTIVE, 12))
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(18.toShort())
        plugin.reconcilePendingDose()
        state = PersistedO5PodStateManager(aapsLogger, storage)
        plugin = newPlugin()
        plugin.reconcilePendingDose()
        assertThat(state.cumulativeBolusPulsesDelivered).isEqualTo(18.toShort())
        assertThat(state.lastBolusDeliveredUnits).isWithin(0.00001).of(0.4)
        assertThat(state.pendingDoseCommand).isNull()
        verify(sync).syncBolusWithPumpId(eq(1000L), argThat<PumpInsulin> { cU > 0.39 && cU < 0.41 }, any(), eq(7L), any(), any())
    }
}
