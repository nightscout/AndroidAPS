package app.aaps.pump.omnipod.omnipod5
import app.aaps.pump.omnipod.common.R

import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.omnipod.common.keys.DashBooleanPreferenceKey
import org.mockito.verification.VerificationMode
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5BleManager
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.common.bledriver.pod.command.StopDeliveryCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.SuspendDeliveryCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBasalCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBolusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramTempBasalCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramAlertsCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.queue.command.CommandDeliverBasalCorrection
import app.aaps.pump.omnipod.common.queue.command.CommandDisableSuspendAlerts
import app.aaps.pump.omnipod.common.queue.command.CommandHandleTimeChange
import app.aaps.pump.omnipod.omnipod5.queue.command.CommandPairNewPod
import app.aaps.pump.omnipod.common.queue.command.CommandPlayTestBeep
import app.aaps.pump.omnipod.common.queue.command.CommandResumeDelivery
import app.aaps.pump.omnipod.common.queue.command.CommandSilenceAlerts
import app.aaps.pump.omnipod.common.queue.command.CommandSuspendDelivery
import app.aaps.pump.omnipod.common.queue.command.CommandUpdateAlertConfiguration
import app.aaps.pump.omnipod.common.util.mapProfileToBasalProgram
import app.aaps.pump.omnipod.omnipod5.history.O5History
import app.aaps.pump.omnipod.omnipod5.history.database.O5HistoryDatabase
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.kotlin.after
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.EnumSet

/**
 * Covers the safety-relevant gating logic and simple property surface of [O5PumpPlugin] -
 * not exhaustive RxJava-flow coverage (no other pump plugin in this codebase unit-tests its
 * full blocking-RxJava dosing flow either, see `OmnipodErosPumpPluginTest`
 * for the closest precedent). Focus: the gates that must reject
 * *before* any BLE command is sent (reservoir check, bolus-already-in-progress check,
 * no-op temp-basal-cancel), the `isBusy()`/`isConnected()`/`isInitialized()` state
 * transitions (the exact thing that caused the queue-deadlock bug during development), and
 * `executeCustomCommand` routing.
 */
class O5PumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var bleManager: O5BleManager
    @Mock lateinit var podStateManager: O5PodStateManager
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var protectionCheck: ProtectionCheck
    @Mock lateinit var blePreCheck: BlePreCheck
    @Mock lateinit var history: O5History
    @Mock lateinit var historyDatabase: O5HistoryDatabase

    private lateinit var plugin: O5PumpPlugin

    @BeforeEach
    fun setup() {
        plugin = O5PumpPlugin(
            aapsLogger, rh, preferences, commandQueue, bleManager, podStateManager, history, pumpSync,
            notificationManager, pumpEnactResultProvider, bolusProgressData, protectionCheck, blePreCheck, config, historyDatabase
        )
        whenever(rh.gs(R.string.omnipod_5_error_not_enough_insulin)).thenReturn("Not enough insulin")
        whenever(rh.gs(R.string.omnipod_5_error_bolus_already_in_progress)).thenReturn("Bolus already in progress")
        whenever(rh.gs(R.string.omnipod_5_error_extended_bolus_not_supported)).thenReturn("Extended bolus not supported")
        whenever(rh.gs(R.string.omnipod_common_error_unsupported_custom_command)).thenReturn("Unsupported custom command: %1\$s")
        whenever(rh.gs(R.string.omnipod_5_error_no_active_profile)).thenReturn("No active profile")
        whenever(rh.gs(R.string.omnipod_5_error_no_active_alerts)).thenReturn("No active alerts")
        whenever(rh.gs(R.string.omnipod_5_error_unresolved_dose_pending)).thenReturn("Earlier dose unconfirmed")
        whenever(rh.gs(R.string.omnipod_5_error_cancel_temp_basal_result_is_uncertain)).thenReturn("Temp basal cancellation is uncertain")
        whenever(rh.gs(R.string.omnipod_5_error_bolus_delivery_status_uncertain)).thenReturn("Bolus delivery is uncertain")
        whenever(rh.gs(R.string.omnipod_5_error_setting_basal_profile_might_have_failed)).thenReturn("Basal profile update is uncertain")
        whenever(rh.gs(R.string.omnipod_common_pod_status_pod_fault_description)).thenReturn("Pod Fault: %1\$03d %2\$s")
        whenever(rh.gs(R.string.omnipod_common_pdm_ref)).thenReturn("Ref: %1\$s")
        whenever(history.createRecord(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(1L))
        whenever(history.markSent(any())).thenReturn(Completable.complete())
        whenever(history.markSuccess(any())).thenReturn(Completable.complete())
        whenever(history.markSendingFailure(any())).thenReturn(Completable.complete())
        whenever(history.markFailure(any())).thenReturn(Completable.complete())
        whenever(history.setTotalAmountDelivered(any(), anyOrNull())).thenReturn(Completable.complete())
    }


    @Test
    fun `isBusy is false when no activation is running or started`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.NOT_STARTED)
        whenever(bleManager.isActivationRunning).thenReturn(false)

        assertThat(plugin.isBusy()).isFalse()
    }

    /**
     * Pairing runs while the progress is still NOT_STARTED. If the pump was not busy then, the queue
     * ran its commands and disconnected the pod in the middle of the pairing (Dash #5219).
     */
    @Test
    fun `isBusy is true while an activation step is running, even before any progress`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.NOT_STARTED)
        whenever(bleManager.isActivationRunning).thenReturn(true)

        assertThat(plugin.isBusy()).isTrue()
    }

    @Test
    fun `disconnect does not close the link while an activation step is running`() {
        whenever(bleManager.isActivationRunning).thenReturn(true)

        plugin.disconnect("Queue empty")

        verify(bleManager, never()).disconnect(any<Boolean>())
    }

    @Test
    fun `disconnect closes the link when no activation is running`() {
        whenever(bleManager.isActivationRunning).thenReturn(false)

        plugin.disconnect("Queue empty")

        verify(bleManager).disconnect(false)
    }

    @Test
    fun `isBusy is true while activation is in progress`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.GOT_POD_VERSION)

        assertThat(plugin.isBusy()).isTrue()
    }

    @Test
    fun `isBusy is false once activation is completed`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)

        assertThat(plugin.isBusy()).isFalse()
    }

    @Test
    fun `isInitialized is true only when activation is fully completed`() {
        whenever(podStateManager.isPodRunning).thenReturn(true)
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        assertThat(plugin.isInitialized()).isTrue()

        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.PRIME_COMPLETED)
        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized is false once an activated pod stops running - faulted or deactivated`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.isPodRunning).thenReturn(false)

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isConnected is true when unpaired - nothing to connect to yet`() {
        whenever(podStateManager.ltk).thenReturn(null)

        assertThat(plugin.isConnected()).isTrue()
    }

    @Test
    fun `isConnected reflects bluetoothConnectionState once paired`() {
        whenever(podStateManager.ltk).thenReturn(byteArrayOf(1))
        whenever(podStateManager.bluetoothConnectionState).thenReturn(O5PodStateManager.BluetoothConnectionState.CONNECTED)
        assertThat(plugin.isConnected()).isTrue()

        whenever(podStateManager.bluetoothConnectionState).thenReturn(O5PodStateManager.BluetoothConnectionState.DISCONNECTED)
        assertThat(plugin.isConnected()).isFalse()
    }


    @Test
    fun `deliverTreatment rejects carbs`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { plugin.deliverTreatment(DetailedBolusInfo().also { it.carbs = 5.0; it.insulin = 1.0 }) }
        }
    }

    @Test
    fun `deliverTreatment rejects non-positive insulin`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { plugin.deliverTreatment(DetailedBolusInfo().also { it.carbs = 0.0; it.insulin = 0.0 }) }
        }
    }

    @Test
    fun `deliverTreatment rejects a bolus larger than the reservoir without sending any command`() {
        whenever(podStateManager.reservoirPulsesRemaining).thenReturn(20)
        whenever(podStateManager.lastStatusResponseReceived).thenReturn(System.currentTimeMillis())
        whenever(podStateManager.lastBolusStartTime).thenReturn(null)
        whenever(podStateManager.lastBolusRequestedUnits).thenReturn(null)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)

        val result = runBlocking {
            plugin.deliverTreatment(DetailedBolusInfo().also { it.carbs = 0.0; it.insulin = 5.0 })
        }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.bolusDelivered).isEqualTo(0.0)
        verify(bleManager, never()).sendCommand(any(), any())
    }

    @Test
    fun `deliverTreatment rejects when a bolus is already delivering without sending any command`() {
        whenever(podStateManager.reservoirPulsesRemaining).thenReturn(2000)
        whenever(podStateManager.lastStatusResponseReceived).thenReturn(System.currentTimeMillis())
        whenever(podStateManager.lastBolusStartTime).thenReturn(null)
        whenever(podStateManager.lastBolusRequestedUnits).thenReturn(null)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BOLUS_AND_BASAL_ACTIVE)

        val result = runBlocking {
            plugin.deliverTreatment(DetailedBolusInfo().also { it.carbs = 0.0; it.insulin = 1.0 })
        }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
    }


    @Test
    fun `setTempBasalPercent is unsupported`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { plugin.setTempBasalPercent(80, 30, false, PumpSync.TemporaryBasalType.NORMAL) }
        }
    }

    @Test
    fun `cancelTempBasal is a no-op when nothing is running - never touches the pod`() {
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)

        val result = runBlocking {
            whenever(pumpSync.expectedPumpState()).thenReturn(
                PumpSync.PumpState(temporaryBasal = null, extendedBolus = null, bolus = null, profile = null, serialNumber = "")
            )
            plugin.cancelTempBasal(false)
        }

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
    }


    @Test
    fun `executeCustomCommand pairs a new pod for CommandPairNewPod`() {
        whenever(bleManager.pairNewPod()).thenReturn(Observable.empty<PodEvent>())

        val result = plugin.executeCustomCommand(CommandPairNewPod())

        assertThat(result).isNotNull()
        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isTrue()
        verify(bleManager).pairNewPod()
    }

    @Test
    fun `executeCustomCommand rejects an unrecognized custom command`() {
        val unknown = object : CustomCommand {
            override val statusDescription = "UNKNOWN"
        }

        val result = plugin.executeCustomCommand(unknown)

        assertThat(result).isNotNull()
        assertThat(result!!.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `executeCustomCommand deactivates the pod for CommandDeactivatePod`() {
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        val result = plugin.executeCustomCommand(CommandDeactivatePod())

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isTrue()
        verify(bleManager).sendCommand(any(), any())
        verify(bleManager).removeBond()
        verify(podStateManager).reset()
    }

    @Test
    fun `executeCustomCommand silences alerts for CommandSilenceAlerts when there are active alerts`() {
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.activeAlerts).thenReturn(java.util.EnumSet.of(AlertType.LOW_RESERVOIR))
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        val result = plugin.executeCustomCommand(CommandSilenceAlerts())

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isTrue()
        verify(bleManager).sendCommand(any(), any())
    }

    @Test
    fun `executeCustomCommand rejects CommandSilenceAlerts when there are no active alerts`() {
        whenever(podStateManager.activeAlerts).thenReturn(null)

        val result = plugin.executeCustomCommand(CommandSilenceAlerts())

        assertThat(result!!.success).isFalse()
        assertThat(result.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
    }

    @Test
    fun `executeCustomCommand suspends delivery for CommandSuspendDelivery`() {
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        val result = plugin.executeCustomCommand(CommandSuspendDelivery())

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isTrue()
        verify(bleManager).sendCommand(any(), any())
        verify(podStateManager).deliverySuspended = true
    }

    @Test
    fun `executeCustomCommand plays a test beep for CommandPlayTestBeep`() {
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        val result = plugin.executeCustomCommand(CommandPlayTestBeep())

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isTrue()
        verify(bleManager).sendCommand(any(), any())
    }

    @Test
    fun `executeCustomCommand for CommandResumeDelivery fails cleanly when there is no active profile`() {
        runBlocking {
            whenever(pumpSync.expectedPumpState()).thenReturn(
                PumpSync.PumpState(temporaryBasal = null, extendedBolus = null, bolus = null, profile = null, serialNumber = "")
            )

            val result = plugin.executeCustomCommand(CommandResumeDelivery())

            assertThat(result!!.success).isFalse()
            assertThat(result.enacted).isFalse()
            verify(bleManager, never()).sendCommand(any(), any())
        }
    }

    @Test
    fun `executeCustomCommand for CommandHandleTimeChange fails cleanly when there is no active profile`() {
        runBlocking {
            whenever(pumpSync.expectedPumpState()).thenReturn(
                PumpSync.PumpState(temporaryBasal = null, extendedBolus = null, bolus = null, profile = null, serialNumber = "")
            )

            val result = plugin.executeCustomCommand(CommandHandleTimeChange(true))

            assertThat(result!!.success).isFalse()
            assertThat(result.enacted).isFalse()
            verify(bleManager, never()).sendCommand(any(), any())
        }
    }


    @Test
    fun `manufacturer, model, and DST support are fixed`() {
        assertThat(plugin.manufacturer()).isEqualTo(ManufacturerType.Insulet)
        assertThat(plugin.model()).isEqualTo(PumpType.OMNIPOD_5)
        assertThat(plugin.canHandleDST()).isFalse()
        assertThat(plugin.isFakingTempsByExtendedBoluses).isFalse()
    }

    @Test
    fun `serialNumber falls back to a placeholder before pairing`() {
        whenever(podStateManager.podId).thenReturn(null)
        assertThat(plugin.serialNumber()).isEqualTo("O5-unpaired")

        whenever(podStateManager.podId).thenReturn(4242L)
        assertThat(plugin.serialNumber()).isEqualTo("4242")
    }

    @Test
    fun `extended bolus is unsupported`() {
        val deliver = runBlocking { plugin.setExtendedBolus(1.0, 60) }
        assertThat(deliver.success).isFalse()

        val cancel = runBlocking { plugin.cancelExtendedBolus() }
        assertThat(cancel.success).isFalse()
    }


    @Test
    fun `isThisProfileSet is true before pairing - prevents premature basal-set attempts`() {
        whenever(podStateManager.ltk).thenReturn(null)

        assertThat(plugin.isThisProfileSet(mock())).isTrue()
    }

    @Test
    fun `isThisProfileSet is false while delivery is suspended - a set is genuinely needed`() {
        whenever(podStateManager.ltk).thenReturn(byteArrayOf(1))
        whenever(podStateManager.deliverySuspended).thenReturn(true)

        assertThat(plugin.isThisProfileSet(mock())).isFalse()
    }

    @Test
    fun `delivered basal reports the confirmed rounded program hour by hour`() {
        val profile = mock<PumpProfile>()
        whenever(profile.getBasalValues()).thenReturn(arrayOf(Profile.ProfileValue(0, 0.42), Profile.ProfileValue(6 * 3600, 0.43)))
        val program = mapProfileToBasalProgram(profile, PumpType.OMNIPOD_5)
        whenever(podStateManager.isActivationCompleted).thenReturn(true)
        whenever(podStateManager.basalProgram).thenReturn(program)

        val delivered = plugin.deliveredBasal()!!

        assertThat(delivered.map { it.timeAsSeconds }).containsExactlyElementsIn((0..23).map { it * 3600 }).inOrder()
        assertThat(delivered.map { it.value }).containsExactlyElementsIn((0..23).map { if (it < 6) 0.40 else 0.45 }).inOrder()
    }

    @Test
    fun `delivered basal averages half hour rates within each hour`() {
        val profile = mock<PumpProfile>()
        whenever(profile.getBasalValues()).thenReturn(arrayOf(Profile.ProfileValue(0, 0.40), Profile.ProfileValue(1800, 0.45)))
        val program = mapProfileToBasalProgram(profile, PumpType.OMNIPOD_5)
        whenever(podStateManager.isActivationCompleted).thenReturn(true)
        whenever(podStateManager.basalProgram).thenReturn(program)

        assertThat(plugin.deliveredBasal()!!.map { it.value })
            .containsExactlyElementsIn(listOf(0.425) + List(23) { 0.45 }).inOrder()
    }

    @Test
    fun `delivered basal is unknown before activation completes`() {
        whenever(podStateManager.isActivationCompleted).thenReturn(false)
        whenever(podStateManager.basalProgram).thenReturn(BasalProgram(listOf(BasalProgram.Segment(0, 48, 100))))

        assertThat(plugin.deliveredBasal()).isNull()
    }

    @Test
    fun `delivered basal is unknown before a program is confirmed`() {
        whenever(podStateManager.isActivationCompleted).thenReturn(true)
        whenever(podStateManager.basalProgram).thenReturn(null)

        assertThat(plugin.deliveredBasal()).isNull()
    }

    @Test
    fun `database reset clears the O5 history through OwnDatabasePlugin`() {
        val databasePlugin: OwnDatabasePlugin = plugin

        databasePlugin.clearAllTables()

        verify(historyDatabase).clearAllTables()
    }


    @Test
    fun `executeCustomCommand rejects CommandUpdateAlertConfiguration when settings already match`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.syncedAlertSettings).thenReturn(
            O5PodStateManager.SyncedAlertSettings(
                expirationReminderEnabled = false, expirationReminderHours = 0,
                expirationAlarmEnabled = false, expirationAlarmHours = 0,
                lowReservoirAlertEnabled = false, lowReservoirAlertUnits = 0
            )
        )

        val result = plugin.executeCustomCommand(CommandUpdateAlertConfiguration())

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
    }

    @Test
    fun `executeCustomCommand rejects CommandUpdateAlertConfiguration before activation completes`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.PRIME_COMPLETED)

        val result = plugin.executeCustomCommand(CommandUpdateAlertConfiguration())

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
    }

    @Test
    fun `executeCustomCommand pushes CommandUpdateAlertConfiguration when settings changed and records the sync`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.syncedAlertSettings).thenReturn(null)
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.podLifeInHours).thenReturn(80.toShort())
        whenever(podStateManager.minutesSinceActivation).thenReturn(60.toShort())
        whenever(podStateManager.lastStatusResponseReceived).thenReturn(System.currentTimeMillis())
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        val result = plugin.executeCustomCommand(CommandUpdateAlertConfiguration())

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isTrue()
        verify(bleManager).sendCommand(any(), any())
        verify(podStateManager).syncedAlertSettings = any()
    }

    @Test
    fun `executeCustomCommand rejects CommandDisableSuspendAlerts when nothing is armed`() {
        whenever(podStateManager.suspendAlertsEnabled).thenReturn(false)

        val result = plugin.executeCustomCommand(CommandDisableSuspendAlerts(rh))

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
    }

    @Test
    fun `executeCustomCommand silences the pod for CommandDisableSuspendAlerts when armed`() {
        whenever(podStateManager.suspendAlertsEnabled).thenReturn(true)
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        val result = plugin.executeCustomCommand(CommandDisableSuspendAlerts(rh))

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isTrue()
        verify(bleManager).sendCommand(any(), any())
        verify(podStateManager).suspendAlertsEnabled = false
    }

    @Test
    fun `executeCustomCommand rejects CommandDeliverBasalCorrection when drift compensation is disabled - the default`() {
        val result = plugin.executeCustomCommand(CommandDeliverBasalCorrection())

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
    }

    @Test
    fun `zero temp basal allows a correction within 5 minutes of a bolus - same as Dash`() {
        stubBasalCorrectionDrift(0.0)
        whenever(podStateManager.lastBolusStartTime).thenReturn(System.currentTimeMillis())
        whenever(bleManager.sendCommand(argThat { this is ProgramBolusCommand }, any()))
            .thenReturn(Observable.error(IllegalStateException("Correction command not sent")))

        plugin.executeCustomCommand(CommandDeliverBasalCorrection())

        verify(bleManager).sendCommand(argThat { this is ProgramBolusCommand }, any())
    }

    @Test
    fun `zero temp basal blocks a correction more than 5 minutes after a bolus`() {
        stubBasalCorrectionDrift(0.0)
        whenever(podStateManager.lastBolusStartTime).thenReturn(System.currentTimeMillis() - 6 * 60 * 1000L)

        val result = plugin.executeCustomCommand(CommandDeliverBasalCorrection())

        assertThat(result!!.success).isTrue()
        assertThat(result.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
        verify(podStateManager, never()).lastBasalCorrectionTime = anyOrNull()
    }

    @Test
    fun `zero temp basal blocks a correction without a recent bolus`() {
        stubBasalCorrectionDrift(0.0)

        val result = plugin.executeCustomCommand(CommandDeliverBasalCorrection())

        assertThat(result!!.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
    }

    @Test
    fun `replacing a temp basal with zero queues no correction before or after the replacement`() = runBlocking<Unit> {
        stubBasalCorrectionDrift(0.55)
        stubStopTempBasal()

        val result = plugin.setTempBasalAbsolute(0.0, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        val commands = inOrder(bleManager)
        commands.verify(bleManager).sendCommand(argThat { this is StopDeliveryCommand }, any())
        commands.verify(bleManager).sendCommand(argThat { this is ProgramTempBasalCommand }, any())
        commands.verifyNoMoreInteractions()
        verify(commandQueue, after(500).never()).customCommand(any())
        verify(podStateManager, never()).lastBasalCorrectionTime = anyOrNull()
    }

    @Test
    fun `replacing a temp basal queues a correction only after the positive replacement`() = runBlocking<Unit> {
        stubBasalCorrectionDrift(0.55)
        stubStopTempBasal()

        val result = plugin.setTempBasalAbsolute(0.4, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isTrue()
        // The correction is queued from pluginScope, so wait for it before checking the order
        verify(commandQueue, timeout(2_000)).customCommand(argThat { this is CommandDeliverBasalCorrection })
        val commands = inOrder(bleManager, commandQueue)
        commands.verify(bleManager).sendCommand(argThat { this is StopDeliveryCommand }, any())
        commands.verify(bleManager).sendCommand(argThat { this is ProgramTempBasalCommand }, any())
        commands.verify(commandQueue).customCommand(argThat { this is CommandDeliverBasalCorrection })
        verify(bleManager, never()).sendCommand(argThat { this is ProgramBolusCommand }, any())
        verify(podStateManager, never()).lastBasalCorrectionTime = anyOrNull()
    }

    @Test
    fun `a correction is not queued twice`() = runBlocking<Unit> {
        stubBasalCorrectionDrift(0.55)
        stubStopTempBasal()
        whenever(commandQueue.isCustomCommandInQueue(CommandDeliverBasalCorrection::class)).thenReturn(true)

        val result = plugin.setTempBasalAbsolute(0.4, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isTrue()
        verify(commandQueue, after(500).never()).customCommand(any())
    }

    @Test
    fun `a failed zero temp basal replacement does not queue a correction`() = runBlocking<Unit> {
        stubBasalCorrectionDrift(0.55)
        stubStopTempBasal()
        whenever(bleManager.sendCommand(argThat { this is ProgramTempBasalCommand }, any()))
            .thenReturn(Observable.error(IllegalStateException("Temp basal command not sent")))
        whenever(rh.gs(R.string.omnipod_5_error_setting_temp_basal_might_have_failed)).thenReturn("Temp basal failed")

        val result = plugin.setTempBasalAbsolute(0.0, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isFalse()
        verify(bleManager, never()).sendCommand(argThat { this is ProgramBolusCommand }, any())
        verify(commandQueue, after(500).never()).customCommand(any())
        verify(podStateManager, never()).lastBasalCorrectionTime = anyOrNull()
    }

    @Test
    fun `a standalone temp basal cancel queues a correction after the cancel`() = runBlocking<Unit> {
        stubBasalCorrectionDrift(0.0)
        stubStopTempBasal()

        val result = plugin.cancelTempBasal(false)

        assertThat(result.success).isTrue()
        // The correction is queued from pluginScope, so wait for it before checking the order
        verify(commandQueue, timeout(2_000)).customCommand(argThat { this is CommandDeliverBasalCorrection })
        val commands = inOrder(bleManager, commandQueue)
        commands.verify(bleManager).sendCommand(argThat { this is StopDeliveryCommand }, any())
        commands.verify(commandQueue).customCommand(argThat { this is CommandDeliverBasalCorrection })
        verify(bleManager, never()).sendCommand(argThat { this is ProgramBolusCommand }, any())
    }

    @Test
    fun `a pending dose blocks the drift reset and the correction`() {
        stubBasalCorrectionDrift(0.55)
        // 3 bolus pulses are not counted yet, so basal looks 0.15 U too high
        whenever(podStateManager.totalPulsesDelivered).thenReturn(23)
        whenever(podStateManager.basalExpected).thenReturn(0.0)
        whenever(podStateManager.pendingDoseCommand).thenReturn(pendingBolus(sequenceNumber = 4))

        val result = plugin.executeCustomCommand(CommandDeliverBasalCorrection())

        assertThat(result!!.success).isFalse()
        assertThat(result.enacted).isFalse()
        verify(podStateManager, never()).basalExpected = any()
        verify(bleManager, never()).sendCommand(any(), any())
        verify(podStateManager, never()).lastBasalCorrectionTime = anyOrNull()
    }

    private fun stubBasalCorrectionDrift(initialRate: Double) {
        plugin.scope = CoroutineScope(Dispatchers.Unconfined)
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.totalPulsesDelivered).thenReturn(20)
        whenever(podStateManager.cumulativeBolusPulsesDelivered).thenReturn(20)
        whenever(podStateManager.basalExpected).thenReturn(0.05)
        whenever(podStateManager.reservoirPulsesRemaining).thenReturn(1000)
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.TEMP_BASAL_ACTIVE)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())
        var activeRate: Double? = initialRate
        whenever(podStateManager.activeTempBasalRate).thenAnswer { activeRate }
        doAnswer {
            activeRate = it.getArgument<Double?>(0)
            null
        }.whenever(podStateManager).activeTempBasalRate = anyOrNull()
        stubAcceptedDoseCommands()
    }

    private fun stubAcceptedDoseCommands() {
        var pending: O5PodStateManager.PendingDoseCommand? = null
        whenever(podStateManager.pendingDoseCommand).thenAnswer { pending }
        doAnswer {
            pending = it.getArgument(0)
            null
        }.whenever(podStateManager).pendingDoseCommand = anyOrNull()
        whenever(bleManager.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            if (command is ProgramBasalCommand || command is ProgramTempBasalCommand) {
                pending = pending?.copy(confirmedByStatus = true)
                whenever(podStateManager.deliveryStatus).thenReturn(
                    if (command is ProgramBasalCommand) DeliveryStatus.BASAL_ACTIVE else DeliveryStatus.TEMP_BASAL_ACTIVE
                )
            }
            Observable.empty<PodEvent>()
        }
    }


    @Test
    fun `an unread fault warns the user but leaves the announcement pending`() {
        whenever(podStateManager.alarmSynced).thenReturn(false)
        whenever(podStateManager.isPodKaput).thenReturn(true)
        whenever(podStateManager.alarmType).thenReturn(null)
        whenever(podStateManager.podStatus).thenReturn(PodStatus.ALARM)
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(commandQueue.isCustomCommandInQueue(CommandDeactivatePod::class)).thenReturn(false)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.error(RuntimeException("pod unreachable")))

        runBlocking {
            plugin.checkPodFault()

            verify(notificationManager).post(
                eq(NotificationId.OMNIPOD_POD_FAULT), any<String>(), level = any(), validMinutes = any(),
                sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
            )
            verify(pumpSync, never()).insertAnnouncement(any(), anyOrNull(), any(), any())
        }
        verify(podStateManager, never()).alarmSynced = true
    }

    @Test
    fun `an unread fault is saved with its code and reference on a later status check`() = runBlocking {
        val alarm = AlarmType.ALARM_OCCLUDED
        val fault = "Pod Fault: 020 $alarm"
        val ref = "17-00003-02251-020"
        whenever(podStateManager.isPodKaput).thenReturn(true)
        whenever(podStateManager.podStatus).thenReturn(PodStatus.ALARM)
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState())
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.error(RuntimeException("pod unreachable")))

        plugin.checkPodKaput()

        verify(pumpSync, never()).insertAnnouncement(any(), anyOrNull(), any(), any())
        verify(podStateManager, never()).alarmSynced = true

        whenever(podStateManager.alarmType).thenReturn(alarm)
        whenever(podStateManager.pdmRef).thenReturn(ref)
        whenever(rh.gs(R.string.omnipod_common_pod_status_pod_fault_description, alarm.code, alarm.toString())).thenReturn(fault)
        whenever(rh.gs(R.string.omnipod_common_pdm_ref, ref)).thenReturn("Ref: $ref")

        plugin.checkPodKaput()

        verify(pumpSync).insertAnnouncement(eq("$fault\nRef: $ref"), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
        verify(podStateManager).alarmSynced = true
    }

    @Test
    fun `status polling saves a stopped pod fault once through stopped-pod handling`() = runBlocking {
        val alarm = AlarmType.ALARM_OCCLUDED
        val fault = "Pod Fault: 020 $alarm"
        var alarmSynced = false
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(podStateManager.isPodKaput).thenReturn(true)
        whenever(podStateManager.alarmType).thenReturn(alarm)
        whenever(podStateManager.podActivatedAt).thenReturn(1L)
        whenever(podStateManager.alarmSynced).thenAnswer { alarmSynced }
        doAnswer {
            alarmSynced = it.getArgument(0)
            null
        }.whenever(podStateManager).alarmSynced = any()
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState())
        whenever(bleManager.connect()).thenReturn(Observable.empty())
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())
        whenever(rh.gs(R.string.omnipod_common_pod_status_pod_fault_description, alarm.code, alarm.toString())).thenReturn(fault)

        plugin.getPumpStatus("Fault")
        plugin.getPumpStatus("Fault")

        val order = inOrder(pumpSync, podStateManager)
        order.verify(pumpSync).insertAnnouncement(eq(fault), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
        order.verify(podStateManager).alarmSynced = true
        verify(pumpSync, times(1)).insertAnnouncement(any(), anyOrNull(), any(), any())
    }

    @Test
    fun `a rejected temp basal cancel returns the fault and reference without throwing`() = runBlocking {
        val description = stubExpiredPodResponse()
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.TEMP_BASAL_ACTIVE)

        val result = plugin.cancelTempBasal(false)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo(description)
        verify(pumpSync).insertAnnouncement(eq(description), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
        verify(podStateManager).alarmSynced = true
        verify(notificationManager).post(
            eq(NotificationId.OMNIPOD_POD_FAULT), eq(description), level = any(), validMinutes = any(),
            sound = eq(AlarmSound.BOLUS_ERROR), actions = any(), validityCheck = anyOrNull()
        )
    }

    @Test
    fun `a rejected temp basal start returns and saves the fault details`() = runBlocking {
        val description = stubExpiredPodResponse()

        val result = plugin.setTempBasalAbsolute(0.2, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo(description)
        verify(pumpSync).insertAnnouncement(eq(description), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
    }

    @Test
    fun `a failed status poll still saves the returned alarm and records zero delivery`() = runBlocking {
        val description = stubExpiredPodResponse()
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(bleManager.connect()).thenReturn(Observable.empty())

        plugin.getPumpStatus("Fault")
        plugin.getPumpStatus("Fault")

        verify(pumpSync, times(1)).insertAnnouncement(eq(description), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
        verify(podStateManager, times(1)).alarmSynced = true
        verify(pumpSync, times(2)).syncTemporaryBasalWithPumpId(
            any(), argThat { cU == 0.0 }, any(), eq(true), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND),
            any(), eq(PumpType.OMNIPOD_5), eq("9999")
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["deactivate", "suspend", "silence", "beep"])
    fun `a rejected custom command returns and saves the fault details`(command: String) = runBlocking {
        val description = stubExpiredPodResponse()
        whenever(podStateManager.activeAlerts).thenReturn(EnumSet.of(AlertType.LOW_RESERVOIR))
        val customCommand = when (command) {
            "deactivate" -> CommandDeactivatePod()
            "suspend"    -> CommandSuspendDelivery()
            "silence"    -> CommandSilenceAlerts()
            "beep"       -> CommandPlayTestBeep()
            else         -> error("Unknown test command: $command")
        }

        val result = plugin.executeCustomCommand(customCommand)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo(description)
        verify(pumpSync).insertAnnouncement(eq(description), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
        verify(podStateManager, never()).reset()
        verify(bleManager, never()).removeBond()
    }

    @Test
    fun `a failed fault write does not escape the command error handler or lose its description`() = runBlocking {
        val description = stubExpiredPodResponse()
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.TEMP_BASAL_ACTIVE)
        whenever(pumpSync.insertAnnouncement(any(), anyOrNull(), any(), any()))
            .thenThrow(IllegalStateException("Database unavailable"))
            .thenAnswer { Unit }

        val first = plugin.cancelTempBasal(false)

        assertThat(first.success).isFalse()
        assertThat(first.comment).isEqualTo(description)
        verify(podStateManager, never()).alarmSynced = true

        val retry = plugin.cancelTempBasal(false)

        assertThat(retry.success).isFalse()
        assertThat(retry.comment).isEqualTo(description)
        verify(pumpSync, times(2)).insertAnnouncement(eq(description), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
        verify(podStateManager).alarmSynced = true
    }

    @Test
    fun `a connection error without a pod fault returns a nonempty cancellation message`() = runBlocking {
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.TEMP_BASAL_ACTIVE)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.error(MessageIOException("No response")))

        val result = plugin.cancelTempBasal(false)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).isEqualTo("Temp basal cancellation is uncertain")
        verify(pumpSync, never()).insertAnnouncement(any(), anyOrNull(), any(), any())
        verify(podStateManager, never()).alarmSynced = true
    }

    private suspend fun stubExpiredPodResponse(): String {
        val alarm = AlarmType.ALARM_PUMP_EXPIRED
        val fault = "Pod Fault: 028 $alarm"
        val ref = "16-00000-00000-028"
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState())
        whenever(rh.gs(R.string.omnipod_common_pod_status_pod_fault_description, alarm.code, alarm.toString())).thenReturn(fault)
        whenever(rh.gs(R.string.omnipod_common_pdm_ref, ref)).thenReturn("Ref: $ref")
        var alarmSynced = false
        whenever(podStateManager.alarmSynced).thenAnswer { alarmSynced }
        doAnswer {
            alarmSynced = it.getArgument(0)
            null
        }.whenever(podStateManager).alarmSynced = any()
        whenever(bleManager.sendCommand(any(), any())).thenAnswer {
            whenever(podStateManager.alarmType).thenReturn(alarm)
            whenever(podStateManager.pdmRef).thenReturn(ref)
            whenever(podStateManager.podStatus).thenReturn(PodStatus.ALARM)
            whenever(podStateManager.isPodKaput).thenReturn(true)
            Observable.error<PodEvent>(MessageIOException("Pod reported a fault"))
        }
        return "$fault\nRef: $ref"
    }

    @Test
    fun `a failed announcement write leaves the fault pending for retry`() = runBlocking {
        whenever(podStateManager.alarmType).thenReturn(AlarmType.ALARM_OCCLUDED)
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(pumpSync.insertAnnouncement(any(), anyOrNull(), any(), any()))
            .thenThrow(IllegalStateException("Database unavailable"))
            .thenAnswer { Unit }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { plugin.checkPodFault() }
        }

        verify(podStateManager, never()).alarmSynced = true

        plugin.checkPodFault()

        verify(pumpSync, times(2)).insertAnnouncement(any(), anyOrNull(), any(), any())
        verify(podStateManager).alarmSynced = true
    }

    @Test
    fun `activation timeout is saved without a fault code`() = runBlocking {
        val description = "Pod activation time exceeded"
        whenever(podStateManager.isPodKaput).thenReturn(true)
        whenever(podStateManager.isPodActivationTimeExceeded).thenReturn(true)
        whenever(podStateManager.podStatus).thenReturn(PodStatus.LUMP_OF_COAL)
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState())
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())
        whenever(rh.gs(R.string.omnipod_common_error_pod_fault_activation_time_exceeded)).thenReturn(description)

        plugin.checkPodKaput()

        verify(pumpSync).insertAnnouncement(eq(description), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
        verify(podStateManager).alarmSynced = true
    }

    @Test
    fun `a healthy pod is not reported`() {
        whenever(podStateManager.alarmSynced).thenReturn(false)
        whenever(podStateManager.isPodKaput).thenReturn(false)
        whenever(podStateManager.alarmType).thenReturn(null)

        runBlocking { plugin.checkPodFault() }

        verify(notificationManager, never()).post(
            any(), any<String>(), level = any(), validMinutes = any(),
            sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
        )
        verify(podStateManager, never()).alarmSynced = true
    }

    @Test
    fun `checkPodFault posts a notification and records an announcement on a new alarm`() {
        whenever(podStateManager.alarmSynced).thenReturn(false)
        whenever(podStateManager.alarmType).thenReturn(AlarmType.ALARM_OCCLUDED)
        whenever(commandQueue.isCustomCommandInQueue(CommandDeactivatePod::class)).thenReturn(false)
        whenever(podStateManager.podId).thenReturn(9999L)

        runBlocking {
            plugin.checkPodFault()

            verify(notificationManager).post(
                eq(NotificationId.OMNIPOD_POD_FAULT), any<String>(), level = any(), validMinutes = any(),
                sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
            )
            verify(pumpSync).insertAnnouncement(any<String>(), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
        }
        verify(podStateManager).alarmSynced = true
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `fault notification and announcement include the unsigned code and available reference`(hasRef: Boolean) = runBlocking {
        val alarm = AlarmType.ALARM_SYNC_WITHOUT_CLOSED_LOOP
        val fault = "Pod Fault: 152 $alarm"
        val ref = "19-00006-02251-152"
        whenever(podStateManager.alarmType).thenReturn(alarm)
        whenever(podStateManager.pdmRef).thenReturn(ref.takeIf { hasRef })
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(rh.gs(R.string.omnipod_common_pod_status_pod_fault_description, 152, alarm.toString())).thenReturn(fault)
        whenever(rh.gs(R.string.omnipod_common_pdm_ref, ref)).thenReturn("Ref: $ref")

        plugin.checkPodFault()

        val expected = if (hasRef) "$fault\nRef: $ref" else fault
        verify(notificationManager).post(
            eq(NotificationId.OMNIPOD_POD_FAULT), eq(expected), level = any(), validMinutes = any(),
            sound = eq(AlarmSound.BOLUS_ERROR), actions = any(), validityCheck = anyOrNull()
        )
        verify(pumpSync).insertAnnouncement(eq(expected), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
    }

    @Test
    fun `checkPodFault is a no-op once the current alarm is already synced - not re-posted on every poll`() {
        whenever(podStateManager.alarmSynced).thenReturn(true)

        runBlocking { plugin.checkPodFault() }

        verify(notificationManager, never()).post(
            any(), any<String>(), level = any(), validMinutes = any(),
            sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
        )
    }

    @Test
    fun `checkPodFault is a no-op when there is no alarm`() {
        whenever(podStateManager.alarmSynced).thenReturn(false)
        whenever(podStateManager.alarmType).thenReturn(null)

        runBlocking { plugin.checkPodFault() }

        verify(notificationManager, never()).post(
            any(), any<String>(), level = any(), validMinutes = any(),
            sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
        )
        verify(podStateManager, never()).alarmSynced = any()
    }

    @Test
    fun `checkPodFault skips the notification but still records the announcement when deactivation is already queued`() {
        whenever(podStateManager.alarmSynced).thenReturn(false)
        whenever(podStateManager.alarmType).thenReturn(AlarmType.ALARM_OCCLUDED)
        whenever(commandQueue.isCustomCommandInQueue(CommandDeactivatePod::class)).thenReturn(true)
        whenever(podStateManager.podId).thenReturn(9999L)

        runBlocking {
            plugin.checkPodFault()

            verify(notificationManager, never()).post(
                any(), any<String>(), level = any(), validMinutes = any(),
                sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
            )
            verify(pumpSync).insertAnnouncement(any<String>(), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999"))
        }
        verify(podStateManager).alarmSynced = true
    }


    private fun pendingBolus(sequenceNumber: Short?) = O5PodStateManager.PendingDoseCommand(
        type = O5PodStateManager.PendingDoseType.BOLUS,
        requestedUnits = 3.0,
        bolusType = BS.Type.NORMAL,
        startedAt = 1_000L,
        sequenceNumber = sequenceNumber
    )

    @Test
    fun `an uncertain bolus the pod never received records no insulin`() {
        whenever(podStateManager.pendingDoseCommand).thenReturn(pendingBolus(sequenceNumber = 4))
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)

        runBlocking {
            plugin.reconcilePendingDose()

            verify(pumpSync, never()).syncBolusWithPumpId(
                any<Long>(), any(), any(), any<Long>(), any(), any<String>()
            )
        }
        verify(podStateManager).pendingDoseCommand = null
    }

    @Test
    fun `an uncertain bolus the pod never received corrects conservative accounting to zero`() {
        whenever(podStateManager.pendingDoseCommand).thenReturn(
            pendingBolus(sequenceNumber = 4).copy(bolusRecordExpected = true)
        )
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        whenever(podStateManager.podId).thenReturn(9999L)

        runBlocking {
            plugin.reconcilePendingDose()

            verify(pumpSync).syncBolusWithPumpId(
                eq(1_000L),
                argThat<PumpInsulin> { cU == 0.0 },
                eq(BS.Type.NORMAL),
                eq(1_000L),
                eq(PumpType.OMNIPOD_5),
                eq("9999")
            )
        }
        verify(podStateManager).pendingDoseCommand = null
    }

    @Test
    fun `an uncertain bolus the pod did receive is finalized`() {
        whenever(podStateManager.pendingDoseCommand).thenReturn(pendingBolus(sequenceNumber = 9))
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        whenever(podStateManager.bolusPulsesRemaining).thenReturn(0)
        whenever(podStateManager.lastBolusDeliveredUnits).thenReturn(null)
        whenever(podStateManager.podId).thenReturn(9999L)

        runBlocking {
            plugin.reconcilePendingDose()

            verify(pumpSync).syncBolusWithPumpId(
                any<Long>(), any(), any(), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999")
            )
        }
        verify(podStateManager).completeBolus(1_000L, 3.0, 3.0, 60)
    }

    @Test
    fun `a confirmed partial bolus updates accounting and cumulative pulses`() {
        whenever(podStateManager.pendingDoseCommand).thenReturn(pendingBolus(sequenceNumber = 9))
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        whenever(podStateManager.bolusPulsesRemaining).thenReturn(40)
        whenever(podStateManager.lastBolusDeliveredUnits).thenReturn(null)
        whenever(podStateManager.cumulativeBolusPulsesDelivered).thenReturn(10)
        whenever(podStateManager.podId).thenReturn(9999L)

        runBlocking {
            plugin.reconcilePendingDose()

            verify(pumpSync).syncBolusWithPumpId(
                eq(1_000L),
                argThat<PumpInsulin> { cU == 1.0 },
                eq(BS.Type.NORMAL),
                eq(1_000L),
                eq(PumpType.OMNIPOD_5),
                eq("9999")
            )
        }
        verify(podStateManager).completeBolus(1_000L, 3.0, 1.0, 20)
    }

    @Test
    fun `a confirmed bolus remains unresolved when remaining pulses are unavailable`() {
        whenever(podStateManager.pendingDoseCommand).thenReturn(pendingBolus(sequenceNumber = 9))
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        whenever(podStateManager.bolusPulsesRemaining).thenReturn(null)

        runBlocking {
            plugin.reconcilePendingDose()

            verify(pumpSync, never()).syncBolusWithPumpId(
                any<Long>(), any(), any(), any<Long>(), any(), any<String>()
            )
        }
        verify(podStateManager, never()).pendingDoseCommand = null
    }

    @Test
    fun `a still-running bolus is confirmed by delivery status even when the sequence disagrees`() {
        val pending = pendingBolus(sequenceNumber = 4)
        whenever(podStateManager.pendingDoseCommand).thenReturn(pending)
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BOLUS_AND_BASAL_ACTIVE)

        runBlocking { plugin.reconcilePendingDose() }

        verify(podStateManager).pendingDoseCommand = pending.copy(confirmedByStatus = true)
    }

    @Test
    fun `sequence numbers are compared as 4 bits so a wrapped counter still matches`() {
        whenever(podStateManager.pendingDoseCommand).thenReturn(pendingBolus(sequenceNumber = 0x1F))
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(0x0F)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        whenever(podStateManager.bolusPulsesRemaining).thenReturn(0)
        whenever(podStateManager.lastBolusDeliveredUnits).thenReturn(null)
        whenever(podStateManager.podId).thenReturn(9999L)

        runBlocking {
            plugin.reconcilePendingDose()

            verify(pumpSync).syncBolusWithPumpId(
                any<Long>(), any(), any(), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999")
            )
        }
    }

    @Test
    fun `a pending dose restored without a sequence number stays unknown when inactive`() {
        whenever(podStateManager.pendingDoseCommand).thenReturn(pendingBolus(sequenceNumber = null))
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        whenever(podStateManager.bolusPulsesRemaining).thenReturn(0)
        whenever(podStateManager.lastBolusDeliveredUnits).thenReturn(null)
        whenever(podStateManager.podId).thenReturn(9999L)

        runBlocking {
            plugin.reconcilePendingDose()

            verify(pumpSync, never()).syncBolusWithPumpId(
                any<Long>(), any(), any(), any<Long>(), eq(PumpType.OMNIPOD_5), eq("9999")
            )
        }
        verify(podStateManager, never()).pendingDoseCommand = null
    }

    @Test
    fun `an uncertain temp basal the pod never received is dropped without activating one`() {
        whenever(podStateManager.pendingDoseCommand).thenReturn(
            O5PodStateManager.PendingDoseCommand(
                type = O5PodStateManager.PendingDoseType.TEMP_BASAL_START,
                requestedRate = 0.5,
                requestedDurationMinutes = 30,
                startedAt = 1_000L,
                sequenceNumber = 4
            )
        )
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)

        runBlocking { plugin.reconcilePendingDose() }

        verify(podStateManager).pendingDoseCommand = null
    }

    @Test
    fun `reconcile is a no-op when nothing is pending`() {
        whenever(podStateManager.pendingDoseCommand).thenReturn(null)

        runBlocking { plugin.reconcilePendingDose() }

        verify(podStateManager, never()).pendingDoseCommand = anyOrNull()
    }


    @Test
    fun `a temp basal is refused while an earlier dose stays unresolved`() {
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand).thenReturn(pendingBolus(sequenceNumber = 4))
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BOLUS_AND_BASAL_ACTIVE)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        val result = runBlocking {
            plugin.setTempBasalAbsolute(1.0, 30, false, PumpSync.TemporaryBasalType.NORMAL)
        }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `a bolus is refused while an earlier dose stays unresolved and no insulin is reported`() {
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand).thenReturn(pendingBolus(sequenceNumber = 4))
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BOLUS_AND_BASAL_ACTIVE)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        val result = runBlocking {
            plugin.deliverTreatment(DetailedBolusInfo().also { it.insulin = 2.0; it.carbs = 0.0 })
        }

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.bolusDelivered).isEqualTo(0.0)
    }

    @Test
    fun `the guard lets a command through once the status read settles the earlier dose`() {
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand)
            .thenReturn(pendingBolus(sequenceNumber = 9))
            .thenReturn(null)
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(9)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BOLUS_AND_BASAL_ACTIVE)
        whenever(bleManager.connect()).thenReturn(Observable.empty())
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        runBlocking { plugin.setTempBasalAbsolute(1.0, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        verify(bleManager, atLeast(2)).sendCommand(any(), any())
    }

    @Test
    fun `nothing pending means no extra status read before a dose`() {
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand).thenReturn(null)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        runBlocking { plugin.setTempBasalAbsolute(1.0, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        verify(bleManager, times(1)).sendCommand(any(), any())
    }

    @Test
    fun `a canceled bolus reconnects and re-sends the stop command when the link dropped mid-delivery`() {
        var pending: O5PodStateManager.PendingDoseCommand? = null
        whenever(podStateManager.pendingDoseCommand).thenAnswer { pending }
        doAnswer {
            pending = it.getArgument(0)
            null
        }.whenever(podStateManager).pendingDoseCommand = anyOrNull()
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(0)
        whenever(podStateManager.bolusPulsesRemaining).thenReturn(0)
        whenever(podStateManager.reservoirPulsesRemaining).thenReturn(2000)
        whenever(podStateManager.lastStatusResponseReceived).thenReturn(System.currentTimeMillis())
        whenever(podStateManager.lastBolusStartTime).thenReturn(null)
        whenever(podStateManager.lastBolusRequestedUnits).thenReturn(null)
        whenever(podStateManager.podId).thenReturn(12345L)
        // Not bolus-active: lets the initial gate pass and the retry loop settle after one cancel.
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        // Reconnect is a no-op success - simulates the link being brought back up before the stop.
        whenever(bleManager.connect()).thenReturn(Observable.empty())
        // The user presses cancel while the bolus program command is in flight.
        whenever(bleManager.sendCommand(any(), any())).thenAnswer {
            if (it.getArgument<Command>(0) is ProgramBolusCommand) {
                pending = pending?.copy(confirmedByStatus = true)
            }
            plugin.stopBolusDelivering()
            Observable.empty<PodEvent>()
        }

        val result = runBlocking {
            plugin.deliverTreatment(DetailedBolusInfo().also { it.carbs = 0.0; it.insulin = 0.5 })
        }

        assertThat(result.success).isTrue()
        // The stop must reach the pod, and only after a (re)connect.
        verify(bleManager).connect()
        verify(bleManager).sendCommand(argThat { this is StopDeliveryCommand }, any())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `bolus status and cancellation checks save a newly reported fault`(cancel: Boolean) = runBlocking {
        var pending: O5PodStateManager.PendingDoseCommand? = null
        var faulted = false
        var alarm: AlarmType? = null
        val fault = "Pod Fault: 020 ALARM_OCCLUDED"
        val ref = "17-00003-02251-020"
        whenever(podStateManager.pendingDoseCommand).thenAnswer { pending }
        doAnswer {
            pending = it.getArgument(0)
            null
        }.whenever(podStateManager).pendingDoseCommand = anyOrNull()
        whenever(podStateManager.isPodKaput).thenAnswer { faulted }
        whenever(podStateManager.alarmType).thenAnswer { alarm }
        whenever(podStateManager.pdmRef).thenAnswer { ref.takeIf { alarm != null } }
        whenever(podStateManager.podStatus).thenAnswer { if (faulted) PodStatus.ALARM else PodStatus.RUNNING_ABOVE_MIN_VOLUME }
        whenever(podStateManager.deliveryStatus).thenAnswer { if (faulted) DeliveryStatus.SUSPENDED else DeliveryStatus.BASAL_ACTIVE }
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(0)
        whenever(podStateManager.bolusPulsesRemaining).thenReturn(0)
        whenever(podStateManager.reservoirPulsesRemaining).thenReturn(2000)
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState())
        whenever(bleManager.connect()).thenReturn(Observable.empty())
        whenever(rh.gs(R.string.omnipod_common_pod_status_pod_fault_description, AlarmType.ALARM_OCCLUDED.code, AlarmType.ALARM_OCCLUDED.toString()))
            .thenReturn(fault)
        whenever(rh.gs(R.string.omnipod_common_pdm_ref, ref)).thenReturn("Ref: $ref")
        whenever(bleManager.sendCommand(any(), any())).thenAnswer {
            val command = it.getArgument<Command>(0)
            val alarmResponse = it.arguments[1] == AlarmStatusResponse::class
            Observable.defer {
                when {
                    command is ProgramBolusCommand -> {
                        pending = pending?.copy(confirmedByStatus = true)
                        if (cancel) plugin.stopBolusDelivering()
                    }
                    alarmResponse -> alarm = AlarmType.ALARM_OCCLUDED
                    command is GetStatusCommand || command is StopDeliveryCommand -> faulted = true
                }
                Observable.empty<PodEvent>()
            }
        }

        val result = plugin.deliverTreatment(DetailedBolusInfo().also { it.carbs = 0.0; it.insulin = 0.05 })

        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isEqualTo(0.05)
        verifyZeroTempBasalSynced("12345")
        verify(pumpSync).insertAnnouncement(eq("$fault\nRef: $ref"), any<Long>(), eq(PumpType.OMNIPOD_5), eq("12345"))
        verify(podStateManager).alarmSynced = true
        verify(notificationManager).post(
            eq(NotificationId.OMNIPOD_POD_FAULT), eq("$fault\nRef: $ref"), level = any(), validMinutes = any(),
            sound = eq(AlarmSound.BOLUS_ERROR), actions = any(), validityCheck = anyOrNull()
        )
        if (cancel) verify(bleManager).sendCommand(argThat { this is StopDeliveryCommand }, any())
    }

    private fun pumpState(temporaryBasal: PumpSync.PumpState.TemporaryBasal? = null, serial: String = "", profile: PumpProfile? = null) =
        PumpSync.PumpState(temporaryBasal = temporaryBasal, extendedBolus = null, bolus = null, profile = profile, serialNumber = serial)

    private fun zeroTempBasal(type: PumpSync.TemporaryBasalType) = PumpSync.PumpState.TemporaryBasal(
        timestamp = 1_000L, duration = 60_000L, rate = 0.0, isAbsolute = true, type = type, id = 1L, pumpId = 1L
    )

    private suspend fun verifyZeroTempBasalSynced(serial: String) {
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            any(), argThat { this.cU == 0.0 }, any(), eq(true), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND),
            any(), eq(PumpType.OMNIPOD_5), eq(serial)
        )
    }

    private suspend fun stubStopTempBasal() {
        whenever(pumpSync.syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())).thenReturn(true)
        whenever(pumpSync.syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())).thenReturn(true)
    }

    private suspend fun verifyNoTempBasalSynced() {
        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun `a faulted pod records a zero temp basal under the pod serial`() = runBlocking<Unit> {
        whenever(podStateManager.isPodKaput).thenReturn(true)
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState())

        plugin.checkPodKaput()

        verifyZeroTempBasalSynced("9999")
    }

    @Test
    fun `a faulted pod does not record a second zero temp basal when one is already running`() = runBlocking<Unit> {
        whenever(podStateManager.isPodKaput).thenReturn(true)
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState(zeroTempBasal(PumpSync.TemporaryBasalType.PUMP_SUSPEND)))

        plugin.checkPodKaput()

        verifyNoTempBasalSynced()
    }

    @Test
    fun `a faulted pod replaces a short zero temp basal from the loop`() = runBlocking<Unit> {
        whenever(podStateManager.isPodKaput).thenReturn(true)
        whenever(podStateManager.podId).thenReturn(9999L)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState(zeroTempBasal(PumpSync.TemporaryBasalType.NORMAL)))

        plugin.checkPodKaput()

        verifyZeroTempBasalSynced("9999")
    }

    @Test
    fun `a healthy pod records no zero temp basal`() = runBlocking<Unit> {
        whenever(podStateManager.isPodKaput).thenReturn(false)

        plugin.checkPodKaput()

        verifyNoTempBasalSynced()
    }

    @Test
    fun `no running pod records a zero temp basal under the serial AAPS has registered`() = runBlocking<Unit> {
        whenever(podStateManager.isPodRunning).thenReturn(false)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState(serial = "4242"))

        plugin.createFakeTBRWhenNoActivePod()

        verifyZeroTempBasalSynced("4242")
    }

    @Test
    fun `no running pod and no registered pump records the zero temp basal under the unpaired serial`() = runBlocking<Unit> {
        whenever(podStateManager.isPodRunning).thenReturn(false)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState(serial = ""))

        plugin.createFakeTBRWhenNoActivePod()

        verifyZeroTempBasalSynced(O5PumpPlugin.UNPAIRED_SERIAL)
    }

    @Test
    fun `a running pod gets no fake zero temp basal`() = runBlocking<Unit> {
        whenever(podStateManager.isPodRunning).thenReturn(true)

        plugin.createFakeTBRWhenNoActivePod()

        verifyNoTempBasalSynced()
        verify(pumpSync, never()).expectedPumpState()
    }

    @Test
    fun `deactivation records a zero temp basal under the old pod serial`() = runBlocking<Unit> {
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState(serial = "12345"))

        plugin.executeCustomCommand(CommandDeactivatePod())

        verifyZeroTempBasalSynced("12345")
    }

    @Test
    fun `suspending delivery records a zero temp basal`() = runBlocking<Unit> {
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        plugin.executeCustomCommand(CommandSuspendDelivery())

        verifyZeroTempBasalSynced("12345")
    }

    @Test
    fun `a confirmed temp basal cancel tells AAPS the temp basal ended`() = runBlocking<Unit> {
        stubStopTempBasal()
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand).thenReturn(null)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.TEMP_BASAL_ACTIVE, DeliveryStatus.BASAL_ACTIVE)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        val result = plugin.cancelTempBasal(false)

        assertThat(result.enacted).isTrue()
        verify(pumpSync).syncStopTemporaryBasalWithPumpId(any(), any(), eq(PumpType.OMNIPOD_5), eq("12345"), any())
    }

    @Test
    fun `a temp basal cancel while delivery is suspended keeps the zero temp basal`() = runBlocking<Unit> {
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand).thenReturn(null)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.SUSPENDED)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState(zeroTempBasal(PumpSync.TemporaryBasalType.PUMP_SUSPEND)))
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        plugin.cancelTempBasal(false)

        verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
    }

    @Test
    fun `a reconciled temp basal cancel tells AAPS the temp basal ended`() = runBlocking<Unit> {
        stubStopTempBasal()
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand).thenReturn(
            O5PodStateManager.PendingDoseCommand(
                type = O5PodStateManager.PendingDoseType.TEMP_BASAL_CANCEL, startedAt = 5_000L, sequenceNumber = 4, historyId = 7L
            )
        )
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(4)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)

        plugin.reconcilePendingDose()

        verify(pumpSync).syncStopTemporaryBasalWithPumpId(eq(5_000L), eq(7L), eq(PumpType.OMNIPOD_5), eq("12345"), any())
    }

    @Test
    fun `a reconciled basal program ends the temp basal and stores the pod time zone`() = runBlocking<Unit> {
        stubStopTempBasal()
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand).thenReturn(
            O5PodStateManager.PendingDoseCommand(
                type = O5PodStateManager.PendingDoseType.BASAL_PROGRAM, startedAt = 5_000L, sequenceNumber = 4, historyId = 7L,
                requestedBasalProgram = BasalProgram(listOf(BasalProgram.Segment(0, 48, 100)))
            )
        )
        whenever(podStateManager.sequenceNumberOfLastProgrammingCommand).thenReturn(4)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)

        plugin.reconcilePendingDose()

        verify(pumpSync).syncStopTemporaryBasalWithPumpId(eq(5_000L), eq(7L), eq(PumpType.OMNIPOD_5), eq("12345"), any())
        verify(podStateManager).updateTimeZone()
        verify(podStateManager).deliverySuspended = false
    }

    private fun flatProfile(): PumpProfile = mock<PumpProfile>().also {
        whenever(it.getBasalValues()).thenReturn(arrayOf(Profile.ProfileValue(0, 1.0)))
    }

    @Test
    fun `profile change disables the suspend alert even when it was previously disabled`() = runBlocking<Unit> {
        stubStopTempBasal()
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        var suspendAlertsEnabled = false
        whenever(podStateManager.suspendAlertsEnabled).thenAnswer { suspendAlertsEnabled }
        doAnswer {
            suspendAlertsEnabled = it.getArgument(0)
            null
        }.whenever(podStateManager).suspendAlertsEnabled = any()
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        stubAcceptedDoseCommands()
        val result = plugin.setNewBasalProfile(flatProfile())

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        inOrder(bleManager, podStateManager) {
            verify(bleManager).sendCommand(argThat { this is SuspendDeliveryCommand }, any())
            verify(podStateManager).suspendAlertsEnabled = true
            verify(bleManager).sendCommand(argThat { this is ProgramBasalCommand }, any())
            verify(bleManager).sendCommand(argThat { this is ProgramAlertsCommand }, any())
            verify(podStateManager).suspendAlertsEnabled = false
        }
        assertThat(suspendAlertsEnabled).isFalse()
    }

    @Test
    fun `failed profile write keeps the suspend alert armed`() = runBlocking<Unit> {
        stubStopTempBasal()
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())
        whenever(bleManager.sendCommand(argThat { this is ProgramBasalCommand }, any()))
            .thenReturn(Observable.error(IllegalStateException("Profile write failed")))

        val result = plugin.setNewBasalProfile(flatProfile())

        assertThat(result.success).isFalse()
        verify(podStateManager).suspendAlertsEnabled = true
        verify(podStateManager, never()).suspendAlertsEnabled = false
        verify(bleManager, never()).sendCommand(argThat { this is ProgramAlertsCommand }, any())
    }

    @Test
    fun `setting a basal profile records the suspend, then ends it, and posts no PROFILE_SET_OK itself`() = runBlocking<Unit> {
        stubStopTempBasal()
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand).thenReturn(null)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.BASAL_ACTIVE)
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        stubAcceptedDoseCommands()
        val result = plugin.setNewBasalProfile(flatProfile())

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        verifyZeroTempBasalSynced("12345")
        verify(pumpSync).syncStopTemporaryBasalWithPumpId(any(), any(), eq(PumpType.OMNIPOD_5), eq("12345"), any())
        verify(podStateManager).updateTimeZone()
        verify(notificationManager, never()).post(eq(NotificationId.PROFILE_SET_OK), any<TextRef>(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `resuming delivery ends the zero temp basal`() = runBlocking<Unit> {
        stubStopTempBasal()
        val profile = flatProfile()
        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.podId).thenReturn(12345L)
        whenever(podStateManager.pendingDoseCommand).thenReturn(null)
        whenever(podStateManager.deliveryStatus).thenReturn(DeliveryStatus.SUSPENDED)
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState(zeroTempBasal(PumpSync.TemporaryBasalType.PUMP_SUSPEND), profile = profile))
        whenever(bleManager.sendCommand(any(), any())).thenReturn(Observable.empty())

        stubAcceptedDoseCommands()
        val result = plugin.executeCustomCommand(CommandResumeDelivery())

        assertThat(result!!.success).isTrue()
        verify(pumpSync).syncStopTemporaryBasalWithPumpId(any(), any(), eq(PumpType.OMNIPOD_5), eq("12345"), any())
    }

    @Test
    fun `setting a basal profile before pairing is deferred, not reported as a write`() = runBlocking<Unit> {
        whenever(podStateManager.ltk).thenReturn(null)

        val result = plugin.setNewBasalProfile(flatProfile())

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
        verify(bleManager, never()).sendCommand(any(), any())
    }

    private fun verifyTextRefPosted(id: NotificationId, mode: VerificationMode = times(1)) {
        verify(notificationManager, mode).post(eq(id), any<TextRef>(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `pod warnings report a missing pod, but only once per 15 minutes`() {
        whenever(podStateManager.isPodRunning).thenReturn(false)

        plugin.updatePodWarnings()
        plugin.updatePodWarnings()

        verifyTextRefPosted(NotificationId.OMNIPOD_POD_NOT_ATTACHED)
    }

    @Test
    fun `pod warnings report suspended delivery with sound`() {
        whenever(podStateManager.isPodRunning).thenReturn(true)
        whenever(podStateManager.isSuspended).thenReturn(true)
        whenever(preferences.get(DashBooleanPreferenceKey.SoundDeliverySuspendedNotification)).thenReturn(true)
        whenever(rh.gs(R.string.omnipod_common_alert_delivery_suspended)).thenReturn("Insulin delivery is suspended")

        plugin.updatePodWarnings()

        verify(notificationManager).dismiss(NotificationId.OMNIPOD_POD_NOT_ATTACHED)
        verify(notificationManager).post(
            eq(NotificationId.OMNIPOD_POD_SUSPENDED), eq("Insulin delivery is suspended"), level = any(), validMinutes = any(),
            sound = eq(AlarmSound.BOLUS_ERROR), actions = any(), validityCheck = anyOrNull()
        )
    }

    @Test
    fun `pod warnings report a pod time zone that differs from the phone`() {
        whenever(podStateManager.isPodRunning).thenReturn(true)
        whenever(podStateManager.isSuspended).thenReturn(false)
        whenever(podStateManager.sameTimeZone).thenReturn(false)

        plugin.updatePodWarnings()

        verify(notificationManager).dismiss(NotificationId.OMNIPOD_POD_SUSPENDED)
        verifyTextRefPosted(NotificationId.OMNIPOD_TIME_OUT_OF_SYNC)
    }

    @Test
    fun `pod warnings stay quiet for a healthy running pod`() {
        whenever(podStateManager.isPodRunning).thenReturn(true)
        whenever(podStateManager.isSuspended).thenReturn(false)
        whenever(podStateManager.sameTimeZone).thenReturn(true)

        plugin.updatePodWarnings()

        verify(notificationManager, never()).post(any(), any<TextRef>(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull())
        verify(notificationManager, never()).post(
            any(), any<String>(), level = any(), validMinutes = any(),
            sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
        )
    }
}
