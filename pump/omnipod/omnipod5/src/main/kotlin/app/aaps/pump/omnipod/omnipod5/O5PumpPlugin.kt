package app.aaps.pump.omnipod.omnipod5
import app.aaps.pump.omnipod.common.R

import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.BS
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.withCompose
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposeScreenContent
import app.aaps.core.ui.compose.icons.IcPluginOmnipod
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5BleManager
import app.aaps.pump.omnipod.common.bledriver.pod.command.DeactivateCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramAlertsCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBasalCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBeepsCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBolusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramTempBasalCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.SilenceAlertsCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.StopDeliveryCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.SuspendDeliveryCommand
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertConfiguration
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertTrigger
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BeepRepetitionType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BeepType
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.definition.O5_FIXED_NONCE
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodConstants
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ProgramReminder
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoActivationTimeResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoTriggeredAlertsResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.basalDrift
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.basalDelivered
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.withPodAcceptance
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.buildO5ExpirationAlerts
import app.aaps.pump.omnipod.omnipod5.history.O5History
import app.aaps.pump.omnipod.common.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.common.history.data.BolusRecord
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BolusType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.history.data.TempBasalRecord
import app.aaps.pump.omnipod.common.keys.DashBooleanPreferenceKey
import app.aaps.pump.omnipod.common.keys.OmnipodBooleanPreferenceKey
import app.aaps.pump.omnipod.common.keys.OmnipodIntPreferenceKey
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
import app.aaps.pump.omnipod.common.ui.compose.OmnipodPodHistoryScreen
import app.aaps.pump.omnipod.omnipod5.keys.O5IntentKey
import app.aaps.pump.omnipod.omnipod5.ui.O5CertificateStoreScreen
import app.aaps.pump.omnipod.omnipod5.ui.compose.OmnipodO5ComposeContent
import app.aaps.pump.omnipod.common.util.mapProfileToBasalProgram
import app.aaps.pump.omnipod.omnipod5.ui.compose.O5PodHistoryScreen
import app.aaps.pump.omnipod.omnipod5.ui.compose.O5PodHistoryViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx3.rxCompletable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.concurrent.CountDownLatch
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.concurrent.thread
import kotlin.math.ceil

private fun (() -> PumpEnactResult).get(): PumpEnactResult = invoke()

/**
 * `Pump`-interface implementation for Omnipod 5. Connects the already-built
 * pairing/BLE/command/persistence layers
 * (see [O5BleManager], [O5PodStateManager]) to AAPS's dosing/control surface.
 *
 * Runs O5 as a manual pod under AAPS's control, identical capabilities to Omnipod Dash -
 * the pod's own onboard automated-delivery mode is never engaged (mirrors
 * [PumpType.OMNIPOD_DASH]'s capability fields).
 *
 * Every dose-affecting command (bolus, temp basal, basal program) records a
 * [O5PodStateManager.pendingDoseCommand] marker *before* sending, and clears it only once
 * the outcome is confirmed. If the BLE response never arrives, the marker survives (it's
 * part of the persisted pod state) and [reconcilePendingDose] - run at the end of every
 * [getPumpStatus] call - resolves it on the next successful status read. Regular status
 * reads are driven by the loop cycle and `KeepAliveWorker` (like Dash), so the pod stays
 * disconnected between cycles. Two timers run on top of that:
 *  - [statusChecker] only queues an extra read when a
 *    [O5PodStateManager.pendingDoseCommand] is still outstanding (see
 *    [refreshStatusOnUnacknowledgedCommands]), and stops itself once the marker is cleared.
 *  - [housekeepingChecker] runs every minute while the plugin runs, but uses stored state
 *    only and never connects to the pod. It tells AAPS that no insulin is going in while no
 *    pod is running ([createFakeTBRWhenNoActivePod]) and posts the periodic pod warnings
 *    ([updatePodWarnings]), like the Dash status tick does.
 * This is what makes an uncertain delivery outcome
 * recoverable instead of silently lost; see [O5PodStateManager]'s class doc for why this
 * doesn't need a Dash-style persisted command ledger to do so.
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@PumpDriver
@IntKey(1085)
@SingleIn(AppScope::class)
class O5PumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val bleManager: O5BleManager,
    private val podStateManager: O5PodStateManager,
    private val history: O5History,
    private val pumpSync: PumpSync,
    notificationManager: NotificationManager,
    private val pumpEnactResultProvider: () -> PumpEnactResult,
    private val bolusProgressData: BolusProgressData,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck,
    private val config: Config
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ ->
            OmnipodO5ComposeContent(
                pluginName = rh.gs(R.string.omnipod_5_name),
                protectionCheck = protectionCheck,
                blePreCheck = blePreCheck,
                rh = rh
            )
        }
        .icon(IcPluginOmnipod)
        .pluginName(TextRef.AndroidRes(R.string.omnipod_5_name))
        .description(TextRef.AndroidRes(R.string.omnipod_5_pump_description)),
    ownPreferences = OmnipodBooleanPreferenceKey.entries + OmnipodIntPreferenceKey.entries +
        DashBooleanPreferenceKey.entries + O5IntentKey.ConfigEnabledEntries(config),
    aapsLogger, rh, preferences, commandQueue, notificationManager
), Pump {

    @Volatile private var bolusCanceled = false
    @Volatile private var bolusDeliveryInProgress = false
    @Volatile private var stopConnecting: CountDownLatch? = null
    private val doseMutex = Mutex()

    private var statusChecker: Runnable
    private var housekeepingChecker: Runnable
    private var nextPodWarningCheck: Long = 0

    private var handler: Handler? = null
    @VisibleForTesting
    internal var scope: CoroutineScope? = null

    companion object {

        private const val BOLUS_RETRY_INTERVAL_MS = 2000L
        private const val BOLUS_RETRIES = 5
        private const val STATUS_CHECK_INTERVAL_MS = 60L * 1000
        private const val POD_WARNING_INTERVAL_MS = 15L * 60 * 1000
        private const val RESERVOIR_OVER_50_UNITS_DEFAULT = 75.0

        /** Serial reported before any pod is paired, and used for the zero "no delivery" temp
         *  basal when AAPS has no pump registered yet. */
        internal const val UNPAIRED_SERIAL = "O5-unpaired"

        private const val FIXED_NONCE = O5_FIXED_NONCE

        /** Matches Dash's fixed pulse-delay constant for bolus delivery pacing - a
         *  pod-firmware-level property of the shared command layer. */
        private const val BOLUS_DELAY_BETWEEN_PULSES_EIGHTH_SECONDS: Byte = 16

        private val pumpDescription = PumpDescription().fillFor(PumpType.OMNIPOD_5)
    }

    init {
        statusChecker = Runnable {
            refreshStatusOnUnacknowledgedCommands()
            if (podStateManager.pendingDoseCommand != null) {
                handler?.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MS)
            }
        }
        housekeepingChecker = Runnable {
            updatePodWarnings()
            scope?.launch {
                try {
                    createFakeTBRWhenNoActivePod()
                } catch (e: Exception) {
                    aapsLogger.warn(LTag.PUMP, "O5 error in createFakeTBRWhenNoActivePod: $e")
                }
            }
            handler?.postDelayed(housekeepingChecker, STATUS_CHECK_INTERVAL_MS)
        }
    }

    /**
     * (Re)start the 60s [statusChecker] tick. Called whenever a dose-affecting command records a
     * [O5PodStateManager.pendingDoseCommand], so an unconfirmed dose is reconciled promptly. The
     * tick self-stops once the pending marker is cleared, so it does not run while the pod is idle.
     */
    private fun armStatusChecker() {
        handler?.removeCallbacks(statusChecker)
        handler?.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MS)
    }

    /**
     * Mirrors `OmnipodDashPumpPlugin.refreshStatusOnUnacknowledgedCommands`. The periodic tick no
     * longer forces a BLE connect on its own - regular status polling is driven by the loop cycle
     * and `KeepAliveWorker` (same as Dash), which keeps the pod disconnected between cycles instead
     * of reconnecting every tick. We only reach out here when a dose-affecting command was left
     * unconfirmed ([O5PodStateManager.pendingDoseCommand] survives), so a single status read is
     * queued to reconcile it promptly rather than waiting for the next loop-driven read. The queue
     * routes that read through [getPumpStatus], which runs [reconcilePendingDose] at the end.
     */
    private fun refreshStatusOnUnacknowledgedCommands() {
        if (podStateManager.activationProgress == ActivationProgress.COMPLETED &&
            podStateManager.pendingDoseCommand != null &&
            commandQueue.size() == 0 &&
            commandQueue.performing() == null
        ) {
            pluginScope.launch { commandQueue.readStatus(rh.gs(R.string.omnipod_5_unconfirmed_command)) }
        }
    }

    /**
     * Tells AAPS that no insulin is going in while no pod is running (none paired, activation not
     * finished, faulted or deactivated). Without this AAPS keeps counting profile basal as
     * delivered and IOB includes insulin that never went in. Mirrors
     * `OmnipodDashPumpPlugin.createFakeTBRWhenNoActivePod`.
     *
     * Uses the serial AAPS has registered as the active pump, so the record is accepted instead of
     * being rejected as data from a different pump - the old pod's serial after a deactivation or
     * discard, or [UNPAIRED_SERIAL] when nothing is registered. The new-pod wizard's
     * `connectNewPump()` ends this temp basal when the next pod starts delivering.
     *
     * Internal (rather than private) to allow unit testing within this module.
     */
    internal suspend fun createFakeTBRWhenNoActivePod() {
        if (podStateManager.isPodRunning) return
        val expectedState = pumpSync.expectedPumpState()
        recordNoDelivery(expectedState, expectedState.serialNumber.ifEmpty { UNPAIRED_SERIAL })
    }

    /**
     * Records a zero-rate [PumpSync.TemporaryBasalType.PUMP_SUSPEND] temp basal for the whole pod
     * lifetime, unless one is already running. A zero temp basal of another type (for example a
     * 30 minute one from the loop) is replaced, because it would end while the pod still delivers
     * nothing.
     */
    private suspend fun recordNoDelivery(expectedState: PumpSync.PumpState, pumpSerial: String) {
        val tbr = expectedState.temporaryBasal
        if (tbr != null && tbr.rate == 0.0 && tbr.type == PumpSync.TemporaryBasalType.PUMP_SUSPEND) return
        aapsLogger.info(LTag.PUMP, "O5 recording zero delivery temp basal, serial=$pumpSerial")
        syncZeroTempBasal(pumpSerial)
    }

    private suspend fun syncZeroTempBasal(pumpSerial: String) {
        val now = System.currentTimeMillis()
        pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = now,
            rate = PumpRate(0.0),
            duration = T.mins(PodConstants.MAX_POD_LIFETIME.toMinutes()).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = now, // not used for anything, only has to be unique
            pumpType = PumpType.OMNIPOD_5,
            pumpSerial = pumpSerial
        )
    }

    /**
     * When the pod has stopped delivering for good (fault or deactivation), tells AAPS that no
     * insulin is going in. Runs on every status read, not only once per alarm like
     * [checkPodFault], so a lost write or an app restart still ends up with the zero temp basal.
     * Mirrors `OmnipodDashPumpPlugin.checkPodKaput`.
     *
     * Internal (rather than private) to allow unit testing within this module.
     */
    internal suspend fun checkPodKaput() {
        if (!podStateManager.isPodKaput) return
        recordNoDelivery(pumpSync.expectedPumpState(), serialNumber())
    }

    /**
     * Posts the periodic pod warnings, at most once every 15 minutes: no running pod, delivery
     * suspended, pod time zone differs from the phone. Uses stored state only. Port of
     * `OmnipodDashPumpPlugin.updatePodWarnings`.
     *
     * Internal (rather than private) to allow unit testing within this module.
     */
    internal fun updatePodWarnings() {
        val now = System.currentTimeMillis()
        if (now <= nextPodWarningCheck) return
        if (!podStateManager.isPodRunning) {
            notificationManager.post(
                NotificationId.OMNIPOD_POD_NOT_ATTACHED,
                TextRef.AndroidRes(R.string.omnipod_common_pod_status_no_active_pod)
            )
        } else {
            notificationManager.dismiss(NotificationId.OMNIPOD_POD_NOT_ATTACHED)
            if (podStateManager.isSuspended) {
                notificationManager.post(
                    NotificationId.OMNIPOD_POD_SUSPENDED,
                    rh.gs(R.string.omnipod_common_alert_delivery_suspended),
                    sound = if (preferences.get(DashBooleanPreferenceKey.SoundDeliverySuspendedNotification)) AlarmSound.BOLUS_ERROR else null
                )
            } else {
                notificationManager.dismiss(NotificationId.OMNIPOD_POD_SUSPENDED)
                if (!podStateManager.sameTimeZone) {
                    notificationManager.post(
                        NotificationId.OMNIPOD_TIME_OUT_OF_SYNC,
                        TextRef.AndroidRes(R.string.timezone_on_pod_is_different_from_the_timezone),
                        level = NotificationLevel.NORMAL
                    )
                }
            }
        }
        nextPodWarningCheck = now + POD_WARNING_INTERVAL_MS
    }

    override suspend fun onStart() {
        super.onStart()
        handler = Handler(Looper.getMainLooper())
        if (podStateManager.pendingDoseCommand != null) {
            handler?.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MS)
        }
        handler?.postDelayed(housekeepingChecker, STATUS_CHECK_INTERVAL_MS)
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        merge(
            preferences.observe(OmnipodBooleanPreferenceKey.ExpirationReminder).drop(1).map {},
            preferences.observe(OmnipodIntPreferenceKey.ExpirationReminderHours).drop(1).map {},
            preferences.observe(OmnipodBooleanPreferenceKey.ExpirationAlarm).drop(1).map {},
            preferences.observe(OmnipodIntPreferenceKey.ExpirationAlarmHours).drop(1).map {},
            preferences.observe(OmnipodBooleanPreferenceKey.LowReservoirAlert).drop(1).map {},
            preferences.observe(OmnipodIntPreferenceKey.LowReservoirAlertUnits).drop(1).map {},
        ).collectResilient(newScope, aapsLogger, LTag.PUMP) { commandQueue.customCommand(CommandUpdateAlertConfiguration()) }
    }

    override suspend fun onStop() {
        super.onStop()
        handler?.removeCallbacks(statusChecker)
        handler?.removeCallbacks(housekeepingChecker)
        handler = null
        scope?.cancel()
        scope = null
    }


    /** Ready only while an activated pod is actually running. [ActivationProgress] never moves
     *  backwards on a fault (the wizard needs it for deactivation), so the pod status is checked
     *  too - like Dash's `isPodRunning`. */
    override fun isInitialized(): Boolean =
        podStateManager.activationProgress == ActivationProgress.COMPLETED && podStateManager.isPodRunning
    override fun isSuspended(): Boolean = podStateManager.deliverySuspended

    // The progress alone does not cover pairing: it is still NOT_STARTED until the first step is done,
    // and the queue then ran and disconnected in the middle of the pairing (Dash #5219).
    override fun isBusy(): Boolean =
        bleManager.isActivationRunning ||
            (podStateManager.activationProgress != ActivationProgress.NOT_STARTED &&
                podStateManager.activationProgress.isBefore(ActivationProgress.COMPLETED))

    override fun isConnected(): Boolean =
        podStateManager.ltk == null ||
            podStateManager.bluetoothConnectionState == O5PodStateManager.BluetoothConnectionState.CONNECTED

    override fun isConnecting(): Boolean = stopConnecting != null

    override fun isHandshakeInProgress(): Boolean =
        stopConnecting != null &&
            podStateManager.bluetoothConnectionState == O5PodStateManager.BluetoothConnectionState.CONNECTED

    override fun finishHandshaking() {}

    override fun connect(reason: String) {
        aapsLogger.info(LTag.PUMP, "O5 connect reason=$reason")
        podStateManager.bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.CONNECTING
        synchronized(this) {
            stopConnecting?.let {
                aapsLogger.warn(LTag.PUMP, "O5 already connecting: $it")
                return
            }
            stopConnecting = CountDownLatch(1)
        }
        thread(start = true, name = "O5ConnectionThread") {
            try {
                stopConnecting?.let { latch ->
                    bleManager.connect(latch).ignoreElements().blockingAwait()
                }
            } catch (e: Exception) {
                aapsLogger.info(LTag.PUMPCOMM, "O5 connect error=$e")
            } finally {
                synchronized(this) { stopConnecting = null }
            }
        }
    }

    override fun disconnect(reason: String) {
        aapsLogger.info(LTag.PUMP, "O5 disconnect reason=$reason")
        if (bleManager.isActivationRunning) {
            // The activation uses the BLE link outside the queue. Closing it here breaks the pairing (Dash #5219).
            aapsLogger.info(LTag.PUMP, "O5 disconnect ignored, pod activation is running")
            return
        }
        stopConnecting?.countDown()
        bleManager.disconnect(false)
    }

    override fun stopConnecting() {
        aapsLogger.info(LTag.PUMP, "O5 stopConnecting")
        stopConnecting?.countDown()
        bleManager.disconnect(true)
    }


    override suspend fun getPumpStatus(reason: String) {
        aapsLogger.debug(LTag.PUMP, "O5 getPumpStatus reason=$reason")
        if (podStateManager.ltk == null) {
            return
        }
        try {
            doseMutex.withLock {
                fetchStatus().blockingAwait()
                reconcilePendingDoseLocked()
                checkPodKaput()
                checkPodFault()
                updateAlertConfiguration()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error in O5 getPumpStatus", e)
        }
        syncPumpFlows()
    }

    /**
     * Posts a user-facing [NotificationId.OMNIPOD_POD_FAULT] alert (with sound) plus a
     * [PumpSync.insertAnnouncement] entry the first time [O5PodStateManager.alarmType]
     * is seen non-null, mirroring Dash's `OmnipodDashPumpPlugin.checkPodKaput()` handling -
     * without this, a faulted O5 pod only shows CRITICAL status on
     * the Omnipod overview screen with no system notification/sound, so a fault could go
     * unnoticed if the user isn't actively looking at that screen. [O5PodStateManager
     * .alarmSynced] makes this idempotent across repeated status polls of the same fault;
     * the notification is skipped (but the announcement/sync flag are not) if a pod
     * deactivation is already queued, since the user is already acting on the fault.
     *
     * Internal (rather than private) to allow unit testing within this module, without
     * needing to drive [getPumpStatus]'s full status-poll chain.
     */
    internal suspend fun checkPodFault() {
        if (podStateManager.alarmSynced) return
        if (!podStateManager.isPodKaput && podStateManager.alarmType == null) return

        if (podStateManager.alarmType == null) {
            try {
                fetchAlarmStatus().blockingAwait()
            } catch (e: Exception) {
                aapsLogger.debug(LTag.PUMP, "O5 could not read the alarm status page: ${e.message}")
            }
        }

        val description = podStateManager.alarmType?.toString()
            ?: if (podStateManager.isPodActivationTimeExceeded) {
                rh.gs(R.string.omnipod_common_error_pod_fault_activation_time_exceeded)
            } else {
                null
            }
            ?: podStateManager.podStatus?.toString()
            ?: return

        if (!commandQueue.isCustomCommandInQueue(CommandDeactivatePod::class)) {
            notificationManager.post(
                NotificationId.OMNIPOD_POD_FAULT,
                description,
                sound = AlarmSound.BOLUS_ERROR
            )
        }
        pumpSync.insertAnnouncement(
            error = description,
            pumpId = System.currentTimeMillis(),
            pumpType = PumpType.OMNIPOD_5,
            pumpSerial = serialNumber()
        )
        podStateManager.alarmSynced = true
    }

    /**
     * Reads status page 2, the pod's fault detail, to learn *why* it faulted. Mirrors the Dash
     * driver, which requests this page explicitly - the pod never volunteers it.
     */
    private fun fetchAlarmStatus(): Completable = Completable.defer {
        val cmd = GetStatusCommand.Builder()
            .setUniqueId(requirePodId())
            .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
            .setStatusResponseType(ResponseType.StatusResponseType.ALARM_STATUS)
            .build()
        bleManager.sendCommand(cmd, AlarmStatusResponse::class).ignoreElements()
    }

    private fun ensureConnected(): Completable = Completable.defer {
        bleManager.connect().ignoreElements()
    }

    private fun fetchStatus(): Completable = ensureConnected().andThen(Completable.defer {
        val cmd = GetStatusCommand.Builder()
            .setUniqueId(requirePodId())
            .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
            .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
            .build()
        bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements()
    })
        .andThen(Completable.defer { fetchActivationTimeIfNeeded() })
        .andThen(Completable.defer { fetchTriggeredAlertsIfNeeded() })

    /**
     * Status pages 5/1 carry diagnostic data only relevant once something's actually
     * wrong or alerting - fetched conditionally, on top of the default status poll
     * above, rather than on every 15s poll. State is populated as a side effect of
     * [O5BleManager.sendCommand] via `O5BleManagerImpl.onResponse`,
     * same as every other response type, so nothing further is done with the result
     * here - see [O5PodStateManager.podActivatedAt]/[O5PodStateManager.triggeredAlertTimes].
     */
    private fun fetchActivationTimeIfNeeded(): Completable {
        if (podStateManager.alarmType == null || podStateManager.podActivatedAt != null) return Completable.complete()
        val cmd = GetStatusCommand.Builder()
            .setUniqueId(requirePodId())
            .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
            .setStatusResponseType(ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_5)
            .build()
        return bleManager.sendCommand(cmd, PodInfoActivationTimeResponse::class).ignoreElements()
    }

    private fun fetchTriggeredAlertsIfNeeded(): Completable {
        if (podStateManager.activeAlerts?.isNotEmpty() != true) return Completable.complete()
        val cmd = GetStatusCommand.Builder()
            .setUniqueId(requirePodId())
            .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
            .setStatusResponseType(ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_1)
            .build()
        return bleManager.sendCommand(cmd, PodInfoTriggeredAlertsResponse::class).ignoreElements()
    }

    /**
     * Resolves a [O5PodStateManager.pendingDoseCommand] left over from a dose-affecting
     * call whose BLE response never arrived - see this class's doc comment. Called after
     * every successful status read, so an uncertain outcome is recovered automatically
     * rather than only on the next user-triggered action.
     *
     * Decides in the same order OmnipodKit's `PodCommsSession.recoverUnacknowledgedCommand`
     * does:
     *  1. **Authoritative** - the pod reports the sequence number of the last programming
     *     command it accepted ([O5PodStateManager.sequenceNumberOfLastProgrammingCommand]).
     *     If it equals the number this dose was sent with, the pod definitely got it.
     *  2. **Heuristic fallback** - otherwise, a *positive* sign of the delivery actually
     *     running counts as confirmation ([confirmedByDeliveryStatus]).
     *  3. A different sequence can reject an unaccepted command only if no bolus stop
     *     has since replaced it. Otherwise the outcome stays unknown and blocks dosing.
     *
     * Step 3 is why the sequence number matters. With only the delivery-status heuristic, an
     * uncertain bolus that the pod never received looks identical to one that completed - both
     * report "not currently bolusing" - and the old code resolved that ambiguity by assuming
     * delivery, crediting `requestedUnits - bolusPulsesRemaining` as insulin that was never
     * given. That inflates IOB and suppresses later dosing.
     */
    internal suspend fun reconcilePendingDose() = doseMutex.withLock {
        reconcilePendingDoseLocked()
    }

    private suspend fun reconcilePendingDoseLocked() {
        var pending = podStateManager.pendingDoseCommand ?: return
        val status = podStateManager.deliveryStatus ?: return
        if (status == DeliveryStatus.UNKNOWN) return
        val podSequence = podStateManager.sequenceNumberOfLastProgrammingCommand
        val sentSequence = pending.sequenceNumber
        val acceptedByPod = pending.confirmedByStatus || pending.bolusCancelRequested != true && podSequence != null && sentSequence != null &&
            (podSequence.toInt() and 0x0f) == (sentSequence.toInt() and 0x0f)

        val confirmedByStatus = confirmedByDeliveryStatus(pending)
        if (!acceptedByPod && !confirmedByStatus) {
            val canReject = pending.type != O5PodStateManager.PendingDoseType.BOLUS || pending.bolusCancelRequested == false
            if (sentSequence != null && podSequence != null && canReject) {
                aapsLogger.info(
                    LTag.PUMP,
                    "O5 pending ${pending.type} was not received by the pod " +
                        "(sent sequence $sentSequence, pod's last programming sequence $podSequence) - recording no delivery"
                )
                if (pending.type == O5PodStateManager.PendingDoseType.BOLUS && pending.bolusRecordExpected) {
                    pumpSync.syncBolusWithPumpId(
                        timestamp = pending.startedAt,
                        amount = PumpInsulin(0.0),
                        type = pending.bolusType ?: BS.Type.NORMAL,
                        pumpId = pending.historyId ?: pending.startedAt,
                        pumpType = PumpType.OMNIPOD_5,
                        pumpSerial = serialNumber()
                    )
                }
                pending.historyId?.let {
                    history.markSendingFailure(it).andThen(history.markFailure(it)).blockingAwait()
                }
                podStateManager.pendingDoseCommand = null
                return
            }
            return
        }
        if (!pending.confirmedByStatus) {
            pending = pending.copy(confirmedByStatus = true)
            podStateManager.pendingDoseCommand = pending
        }

        when (pending.type) {
            O5PodStateManager.PendingDoseType.BOLUS              ->
                if (!status.bolusDeliveringActive() && (status.basalActive() || status.tempBasalActive() || status.suspended())) {
                    val remainingPulses = podStateManager.bolusPulsesRemaining ?: return
                    val deliveredUnits = (pending.requestedUnits ?: return) -
                        remainingPulses.toInt() * PodConstants.POD_PULSE_BOLUS_UNITS
                    finalizeBolus(pending, deliveredUnits)
                }

            O5PodStateManager.PendingDoseType.TEMP_BASAL_START   -> confirmTempBasal(pending)

            O5PodStateManager.PendingDoseType.TEMP_BASAL_CANCEL  ->
                if (podStateManager.deliveryStatus?.tempBasalActive() != true) {
                    podStateManager.activeTempBasalStartTime = null
                    podStateManager.activeTempBasalRate = null
                    podStateManager.activeTempBasalDurationMinutes = null
                    syncTempBasalCancelled(pending.startedAt, pending.historyId ?: pending.startedAt)
                    markDoseSuccess(pending)
                    podStateManager.pendingDoseCommand = null
                }

            O5PodStateManager.PendingDoseType.BASAL_PROGRAM      ->
                if (status.basalActive()) {
                    val program = pending.requestedBasalProgram ?: return
                    podStateManager.basalProgram = program
                    onBasalProgramConfirmed(pending.startedAt, pending.historyId ?: pending.startedAt)
                    markDoseSuccess(pending)
                    podStateManager.pendingDoseCommand = null
                }
        }
    }

    private fun markDoseSuccess(pending: O5PodStateManager.PendingDoseCommand) {
        pending.historyId?.let { history.markSent(it).andThen(history.markSuccess(it)).blockingAwait() }
    }

    private suspend fun confirmTempBasal(pending: O5PodStateManager.PendingDoseCommand) {
        val rate = pending.requestedRate ?: return
        val duration = pending.requestedDurationMinutes ?: return
        val running = pending.startedAt + duration * 60_000L > System.currentTimeMillis() &&
            podStateManager.deliveryStatus?.tempBasalActive() == true
        pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = pending.startedAt,
            rate = PumpRate(rate),
            duration = T.mins(duration.toLong()).msecs(),
            isAbsolute = true,
            type = pending.tempBasalType ?: PumpSync.TemporaryBasalType.NORMAL,
            pumpId = pending.historyId ?: pending.startedAt,
            pumpType = PumpType.OMNIPOD_5,
            pumpSerial = serialNumber()
        )
        podStateManager.activeTempBasalStartTime = pending.startedAt.takeIf { running }
        podStateManager.activeTempBasalRate = rate.takeIf { running }
        podStateManager.activeTempBasalDurationMinutes = duration.takeIf { running }
        markDoseSuccess(pending)
        notificationManager.dismiss(NotificationId.OMNIPOD_TBR_ALERTS)
        podStateManager.pendingDoseCommand = null
    }

    /** Tells AAPS that the temp basal (or the zero "suspended" temp basal) running at
     *  [timestamp] has ended. */
    private suspend fun syncTempBasalStopped(timestamp: Long, endPumpId: Long) {
        val stopped = pumpSync.syncStopTemporaryBasalWithPumpId(
            timestamp = timestamp,
            endPumpId = endPumpId,
            pumpType = PumpType.OMNIPOD_5,
            pumpSerial = serialNumber()
        )
        aapsLogger.info(LTag.PUMP, "O5 syncStopTemporaryBasalWithPumpId ret=$stopped endPumpId=$endPumpId")
    }

    /**
     * After a confirmed temp basal cancel. If the pod is suspended, scheduled basal does not
     * come back, so the zero "suspended" temp basal in AAPS must stay and nothing is stopped.
     */
    private suspend fun syncTempBasalCancelled(timestamp: Long, endPumpId: Long) {
        if (podStateManager.deliveryStatus?.suspended() == true) {
            aapsLogger.info(LTag.PUMP, "O5 temp basal cancelled while delivery is suspended - keeping the zero temp basal")
            return
        }
        syncTempBasalStopped(timestamp, endPumpId)
    }

    /**
     * A basal program always suspends delivery first, so any temp basal on the pod is gone and
     * scheduled basal runs again. Records that in AAPS and in the pod state, and stores the pod
     * clock's time zone, because the same command sets the pod clock. Mirrors Dash's
     * `SET_BASAL_PROFILE` / `RESUME_DELIVERY` confirmation handling.
     */
    private suspend fun onBasalProgramConfirmed(timestamp: Long, endPumpId: Long) {
        podStateManager.deliverySuspended = false
        podStateManager.activeTempBasalStartTime = null
        podStateManager.activeTempBasalRate = null
        podStateManager.activeTempBasalDurationMinutes = null
        podStateManager.updateTimeZone()
        syncTempBasalStopped(timestamp, endPumpId)
        notificationManager.dismiss(NotificationId.OMNIPOD_POD_SUSPENDED)
        notificationManager.dismiss(NotificationId.FAILED_UPDATE_PROFILE)
        notificationManager.dismiss(NotificationId.OMNIPOD_TBR_ALERTS)
        notificationManager.dismiss(NotificationId.OMNIPOD_TIME_OUT_OF_SYNC)
    }

    /**
     * Clears the way for a new dose-affecting command, returning false if it must not be sent.
     *
     * A [O5PodStateManager.pendingDoseCommand] means an earlier dose may or may not have
     * reached the pod. Issuing another one on top of that risks stacking a second dose onto a
     * first that did land, so this first tries to settle the question the same way OmnipodKit's
     * `PodCommsSession.tryToResolvePendingCommand` does - read status, reconcile, and proceed
     * only if the pending marker actually cleared.
     *
     * Note this resolves rather than merely refuses: in the ordinary case where the pod is
     * reachable again, the status read settles the outcome and the new command goes ahead
     * normally. Refusal is reserved for a pod that still cannot be reached or whose status
     * leaves the earlier dose undecided.
     *
     * Deliberately *not* applied to pod deactivation. Blocking a user from taking a pod off
     * because the driver is unsure about a temp basal is the wrong trade, and deactivation
     * never sets a pending marker of its own.
     */
    private suspend fun pendingDoseResolved(): Boolean {
        if (podStateManager.pendingDoseCommand == null) return true
        return try {
            fetchStatus().blockingAwait()
            reconcilePendingDoseLocked()
            val resolved = podStateManager.pendingDoseCommand == null
            if (!resolved) {
                aapsLogger.warn(LTag.PUMP, "O5 refusing new dose command: an earlier dose is still unresolved after a status read")
            }
            resolved
        } catch (e: Exception) {
            aapsLogger.warn(LTag.PUMP, "O5 refusing new dose command: could not read status to resolve the pending dose: ${e.message}")
            false
        }
    }

    private fun unresolvedDoseResult(): PumpEnactResult =
        pumpEnactResultProvider.get().success(false).enacted(false)
            .comment(rh.gs(R.string.omnipod_5_error_unresolved_dose_pending))

    /**
     * Whether the pod's delivery status positively shows [pending] took effect - the fallback
     * for when the sequence number cannot decide it (mirrors OmnipodKit's
     * `checkCommandAgainstStatus`).
     *
     * Deliberately only ever answers true on a *positive* observation. "Not currently
     * bolusing" is not evidence a bolus happened, so [O5PodStateManager.PendingDoseType.BOLUS]
     * uses a running bolus as confirmation. Scheduled basal alone cannot prove a new
     * basal program, since the old program may still be running.
     */
    private fun confirmedByDeliveryStatus(pending: O5PodStateManager.PendingDoseCommand): Boolean {
        val status = podStateManager.deliveryStatus ?: return false
        return when (pending.type) {
            O5PodStateManager.PendingDoseType.BOLUS             -> status.bolusDeliveringActive()
            O5PodStateManager.PendingDoseType.TEMP_BASAL_START  -> status.tempBasalActive()
            O5PodStateManager.PendingDoseType.TEMP_BASAL_CANCEL -> !status.tempBasalActive()
            O5PodStateManager.PendingDoseType.BASAL_PROGRAM     -> false
        }
    }

    private fun pendingDoseAccepted(): Boolean {
        val pending = podStateManager.pendingDoseCommand ?: return false
        val sequence = pending.sequenceNumber
        val podSequence = podStateManager.sequenceNumberOfLastProgrammingCommand
        return pending.confirmedByStatus || confirmedByDeliveryStatus(pending) ||
            sequence != null && podSequence != null && (sequence.toInt() and 0x0f) == (podSequence.toInt() and 0x0f)
    }

    private fun requirePodId(): Int =
        podStateManager.podId?.toInt() ?: throw IllegalStateException("O5 pod not paired")


    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult =
        setNewBasalProfile(profile, OmnipodCommandType.SET_BASAL_PROFILE)

    private suspend fun setNewBasalProfile(
        profile: PumpProfile,
        historyType: OmnipodCommandType = OmnipodCommandType.SET_BASAL_PROFILE
    ): PumpEnactResult = doseMutex.withLock { setNewBasalProfileLocked(profile, historyType) }

    private suspend fun setNewBasalProfileLocked(
        profile: PumpProfile,
        historyType: OmnipodCommandType
    ): PumpEnactResult {
        if (podStateManager.ltk == null) {
            // Pod not paired yet - deferred, not an actual write, same as Dash. enacted=false => no PROFILE_SET_OK.
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }
        if (!pendingDoseResolved()) return unresolvedDoseResult()
        val basalProgram = mapProfileToBasalProgram(profile, PumpType.OMNIPOD_5)
        val historyId = history.createRecord(
            commandType = historyType,
            initialResult = InitialResult.NOT_SENT,
            basalProfileRecord = BasalValuesRecord(profile.getBasalValues().toList())
        ).blockingGet()
        return try {
            if (podStateManager.deliveryStatus?.suspended() != true) {
                val basalBeeps = preferences.get(OmnipodBooleanPreferenceKey.BasalBeepsEnabled)
                val cmd = SuspendDeliveryCommand.Builder()
                    .setUniqueId(requirePodId())
                    .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                    .setNonce(FIXED_NONCE)
                    .setBeepType(if (basalBeeps) BeepType.LONG_SINGLE_BEEP else BeepType.SILENT)
                    .build()
                bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()
                podStateManager.deliverySuspended = true
                podStateManager.suspendAlertsEnabled = true
                syncZeroTempBasal(serialNumber())
            }

            podStateManager.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
                type = O5PodStateManager.PendingDoseType.BASAL_PROGRAM,
                startedAt = System.currentTimeMillis(),
                sequenceNumber = podStateManager.msgSequenceNumber.toShort(),
                historyId = historyId,
                requestedBasalProgram = basalProgram
            )
            armStatusChecker()
            val basalBeeps = preferences.get(OmnipodBooleanPreferenceKey.BasalBeepsEnabled)
            val cmd = ProgramBasalCommand.Builder()
                .setUniqueId(requirePodId())
                .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                .setNonce(FIXED_NONCE)
                .setBasalProgram(basalProgram)
                .setProgramReminder(ProgramReminder(atStart = basalBeeps, atEnd = false, atInterval = 0))
                .setCurrentTime(Date())
                .build()
            bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()
            val accepted = pendingDoseAccepted()
            reconcilePendingDoseLocked()
            if (!accepted || podStateManager.pendingDoseCommand != null) return unresolvedDoseResult()
            // PROFILE_SET_OK is posted centrally (CommandQueue) on success && enacted.
            disableSuspendAlerts()
            pumpEnactResultProvider.get().success(true).enacted(true)
        } catch (e: Exception) {
            history.markSendingFailure(historyId).andThen(history.markFailure(historyId)).blockingAwait()
            aapsLogger.error(LTag.PUMP, "Error in O5 setNewBasalProfile", e)
            notifyUncertain(NotificationId.FAILED_UPDATE_PROFILE, rh.gs(R.string.omnipod_5_error_setting_basal_profile_might_have_failed))
            pumpEnactResultProvider.get().success(false).enacted(false)
        }
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        if (podStateManager.ltk == null) return true
        if (podStateManager.deliverySuspended) return false
        return mapProfileToBasalProgram(profile, PumpType.OMNIPOD_5) == podStateManager.basalProgram
    }


    private val _lastDataTime = MutableStateFlow(0L)
    override val lastDataTime: StateFlow<Long> = _lastDataTime

    private val _lastBolusTime = MutableStateFlow<Long?>(null)
    override val lastBolusTime: StateFlow<Long?> = _lastBolusTime

    private val _lastBolusAmount = MutableStateFlow<PumpInsulin?>(null)
    override val lastBolusAmount: StateFlow<PumpInsulin?> = _lastBolusAmount

    override val baseBasalRate: PumpRate
        get() {
            val rate = if (podStateManager.alarmType != null) 0.0
            else podStateManager.basalProgram?.rateAt(System.currentTimeMillis()) ?: 0.0
            return PumpRate(rate)
        }

    private val _reservoirLevel = MutableStateFlow(PumpInsulin(0.0))
    override val reservoirLevel: StateFlow<PumpInsulin> = _reservoirLevel

    override val batteryLevel: StateFlow<Int?> = MutableStateFlow(null)

    private fun syncPumpFlows() {
        _lastDataTime.value = podStateManager.lastStatusResponseReceived ?: 0L
        _lastBolusTime.value = podStateManager.lastBolusStartTime
        _lastBolusAmount.value = podStateManager.lastBolusRequestedUnits?.let { PumpInsulin(it) }
        _reservoirLevel.value = PumpInsulin(
            podStateManager.reservoirPulsesRemaining?.let { it * PodConstants.POD_PULSE_BOLUS_UNITS }
                ?: RESERVOIR_OVER_50_UNITS_DEFAULT
        )
    }


    override suspend fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult =
        doseMutex.withLock { deliverTreatmentLocked(detailedBolusInfo) }

    private suspend fun deliverTreatmentLocked(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        if (!pendingDoseResolved()) {
            return pumpEnactResultProvider.get().success(false).enacted(false).bolusDelivered(0.0)
                .comment(rh.gs(R.string.omnipod_5_error_unresolved_dose_pending))
        }
        try {
            bolusDeliveryInProgress = true
            syncPumpFlows()
            val requestedUnits = detailedBolusInfo.insulin
            if (requestedUnits > reservoirLevel.value.cU) {
                return pumpEnactResultProvider.get().success(false).enacted(false).bolusDelivered(0.0)
                    .comment(rh.gs(R.string.omnipod_5_error_not_enough_insulin))
            }
            if (podStateManager.deliveryStatus?.bolusDeliveringActive() == true) {
                return pumpEnactResultProvider.get().success(false).enacted(false).bolusDelivered(0.0)
                    .comment(rh.gs(R.string.omnipod_5_error_bolus_already_in_progress))
            }

            val bolusBeepsKey = if (detailedBolusInfo.bolusType == BS.Type.SMB) OmnipodBooleanPreferenceKey.SmbBeepsEnabled
            else OmnipodBooleanPreferenceKey.BolusBeepsEnabled
            val bolusBeeps = preferences.get(bolusBeepsKey)
            val startedAt = System.currentTimeMillis()
            val historyId = history.createRecord(
                commandType = OmnipodCommandType.SET_BOLUS,
                date = startedAt,
                initialResult = InitialResult.NOT_SENT,
                bolusRecord = BolusRecord(
                    requestedUnits,
                    BolusType.fromBolusInfoBolusType(detailedBolusInfo.bolusType)
                )
            ).blockingGet()

            val pendingDose = O5PodStateManager.PendingDoseCommand(
                type = O5PodStateManager.PendingDoseType.BOLUS,
                requestedUnits = requestedUnits,
                bolusType = detailedBolusInfo.bolusType,
                startedAt = startedAt,
                sequenceNumber = podStateManager.msgSequenceNumber.toShort(),
                historyId = historyId
            )
            podStateManager.pendingDoseCommand = pendingDose

            val cmd = ProgramBolusCommand.Builder()
                .setUniqueId(requirePodId())
                .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                .setNonce(FIXED_NONCE)
                .setNumberOfUnits(requestedUnits)
                .setDelayBetweenPulsesInEighthSeconds(BOLUS_DELAY_BETWEEN_PULSES_EIGHTH_SECONDS)
                .setProgramReminder(ProgramReminder(atStart = bolusBeeps, atEnd = bolusBeeps, atInterval = 0))
                .setO5BolusInfo(mealUnits = 0.0, correctionUnits = requestedUnits)
                .build()

            var commandMayHaveBeenSent = false
            val ret = try {
                bleManager.sendCommand(cmd, DefaultStatusResponse::class)
                    .doOnNext { rememberBolusAcceptance(startedAt, it) }
                    .filter { it.isCommandSent() }
                    .doOnNext {
                        commandMayHaveBeenSent = true
                        markBolusRecordExpected(startedAt)
                    }
                    .concatMapCompletable {
                        rxCompletable(Dispatchers.IO) {
                            pumpSync.syncBolusWithPumpId(
                                timestamp = startedAt,
                                amount = PumpInsulin(requestedUnits),
                                type = detailedBolusInfo.bolusType,
                                pumpId = pendingDose.historyId ?: startedAt,
                                pumpType = PumpType.OMNIPOD_5,
                                pumpSerial = serialNumber()
                            )
                        }
                    }
                    .blockingAwait()
                armStatusChecker()
                val completion = waitForBolusDeliveryToComplete(requestedUnits).blockingGet()
                val deliveredUnits = completion.deliveredUnits
                val accepted = podStateManager.pendingDoseCommand?.confirmedByStatus == true
                if (deliveredUnits != null && accepted) {
                    finalizeBolus(podStateManager.pendingDoseCommand ?: pendingDose, deliveredUnits)
                    pumpEnactResultProvider.get().success(true).enacted(true).bolusDelivered(deliveredUnits)
                } else {
                    pumpEnactResultProvider.get().success(true).enacted(true).bolusDelivered(requestedUnits)
                        .comment(rh.gs(R.string.omnipod_5_error_bolus_delivery_status_uncertain))
                }
            } catch (throwable: Throwable) {
                aapsLogger.error(LTag.PUMP, "O5 deliverTreatment error: $throwable")
                if (!commandMayHaveBeenSent && podStateManager.pendingDoseCommand?.confirmedByStatus != true) {
                    podStateManager.pendingDoseCommand = null
                    pendingDose.historyId?.let {
                        history.markSendingFailure(it).andThen(history.markFailure(it)).blockingAwait()
                    }
                } else {
                    if (bolusCanceled) {
                        try {
                            cancelBolus().blockingAwait()
                        } catch (e: Exception) {
                            aapsLogger.warn(LTag.PUMP, "O5 bolus stop outcome is unknown: ${e.message}")
                        }
                    }
                    armStatusChecker()
                }
                pumpEnactResultProvider.get().success(bolusCanceled).enacted(false)
            }

            if (detailedBolusInfo.bolusType == BS.Type.SMB && podStateManager.pendingDoseCommand != null) {
                notifyUncertain(NotificationId.OMNIPOD_UNCERTAIN_SMB, rh.gs(R.string.omnipod_5_error_uncertain_smb, requestedUnits))
            } else if (podStateManager.pendingDoseCommand != null) {
                notifyUncertain(NotificationId.OMNIPOD_POD_FAULT, rh.gs(R.string.omnipod_5_error_bolus_delivery_status_uncertain))
            }
            return ret
        } finally {
            bolusCanceled = false
            bolusDeliveryInProgress = false
        }
    }

    private data class BolusCompletion(val deliveredUnits: Double?)

    private fun waitForBolusDeliveryToComplete(requestedUnits: Double): Single<BolusCompletion> = Single.defer {
        val estimatedSeconds = ceil(requestedUnits / PodConstants.POD_PULSE_BOLUS_UNITS).toLong() * 2 + 3
        var waited = 0L
        while (waited < estimatedSeconds && !bolusCanceled) {
            waited += 1
            Thread.sleep(1000)
            val percent = (waited.toFloat() / estimatedSeconds) * 100
            bolusProgressData.updateProgress(percent.toInt())
        }

        repeat(BOLUS_RETRIES) {
            val cmd = if (bolusCanceled) cancelBolus() else fetchStatus()
            try {
                cmd.blockingAwait()
            } catch (e: Exception) {
                aapsLogger.debug(LTag.PUMP, "waitForBolusDeliveryToComplete errorGettingStatus=$e")
                Thread.sleep(BOLUS_RETRY_INTERVAL_MS)
                return@repeat
            }
            val bolusActive = podStateManager.deliveryStatus?.bolusDeliveringActive() == true
            if (bolusActive) {
                val remainingPulses = podStateManager.bolusPulsesRemaining
                if (remainingPulses == null) {
                    Thread.sleep(BOLUS_RETRY_INTERVAL_MS)
                    return@repeat
                }
                val remainingUnits = remainingPulses.toInt() * PodConstants.POD_PULSE_BOLUS_UNITS
                val delivered = requestedUnits - remainingUnits
                val percent = (delivered / requestedUnits) * 100
                bolusProgressData.updateProgress(percent.toInt())
                val sleepSeconds = if (bolusCanceled) BOLUS_RETRY_INTERVAL_MS / 1000
                else ceil(remainingUnits / PodConstants.POD_PULSE_BOLUS_UNITS).toLong() * 2 + 3
                Thread.sleep(sleepSeconds * 1000)
            } else {
                val remainingPulses = podStateManager.bolusPulsesRemaining
                if (remainingPulses != null) {
                    val deliveredUnits = requestedUnits - remainingPulses.toInt() * PodConstants.POD_PULSE_BOLUS_UNITS
                    return@defer Single.just(BolusCompletion(deliveredUnits))
                }
            }
        }
        Single.just(BolusCompletion(null))
    }

    private fun markBolusRecordExpected(startedAt: Long) {
        val pending = podStateManager.pendingDoseCommand ?: return
        if (pending.type == O5PodStateManager.PendingDoseType.BOLUS && pending.startedAt == startedAt) {
            podStateManager.pendingDoseCommand = pending.copy(bolusRecordExpected = true)
        }
    }

    private fun rememberBolusAcceptance(startedAt: Long, event: PodEvent) {
        val response = (event as? PodEvent.ResponseReceived)?.response as? DefaultStatusResponse ?: return
        val pending = podStateManager.pendingDoseCommand ?: return
        if (pending.startedAt != startedAt) return
        val confirmed = pending.withPodAcceptance(response.sequenceNumberOfLastProgrammingCommand, response.deliveryStatus)
        if (confirmed != pending) {
            podStateManager.pendingDoseCommand = confirmed
        }
    }

    private suspend fun finalizeBolus(pending: O5PodStateManager.PendingDoseCommand, deliveredUnits: Double) {
        val finalUnits = deliveredUnits.coerceIn(0.0, pending.requestedUnits ?: deliveredUnits)
        pending.historyId?.let {
            history.markSent(it)
                .andThen(history.setTotalAmountDelivered(it, finalUnits))
                .andThen(history.markSuccess(it))
                .blockingAwait()
        }
        if (!pending.isBasalCorrection) {
            pumpSync.syncBolusWithPumpId(
                timestamp = pending.startedAt,
                amount = PumpInsulin(finalUnits),
                type = pending.bolusType ?: BS.Type.NORMAL,
                pumpId = pending.historyId ?: pending.startedAt,
                pumpType = PumpType.OMNIPOD_5,
                pumpSerial = serialNumber()
            )
        }
        if (pending.bolusCancelSequenceNumber?.let {
                (it.toInt() and 0x0f) == podStateManager.sequenceNumberOfLastProgrammingCommand?.toInt()?.and(0x0f)
            } == true
        ) {
            pending.bolusCancelHistoryId?.let {
                history.markSent(it).andThen(history.markSuccess(it)).blockingAwait()
            }
        }
        val deliveredPulses = if (pending.isBasalCorrection) {
            null
        } else {
            Math.round(finalUnits / PodConstants.POD_PULSE_BOLUS_UNITS).toShort()
        }
        podStateManager.completeBolus(
            startedAt = pending.startedAt,
            requestedUnits = pending.requestedUnits ?: finalUnits,
            deliveredUnits = finalUnits,
            deliveredPulses = deliveredPulses
        )
    }

    private fun cancelBolus(): Completable = ensureConnected().andThen(Completable.defer {
        val bolusBeeps = preferences.get(OmnipodBooleanPreferenceKey.BolusBeepsEnabled)
        val historyId = podStateManager.pendingDoseCommand?.bolusCancelHistoryId ?: history.createRecord(
            commandType = OmnipodCommandType.CANCEL_BOLUS,
            initialResult = InitialResult.NOT_SENT
        ).blockingGet()
        val cmd = StopDeliveryCommand.Builder()
            .setUniqueId(requirePodId())
            .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
            .setNonce(FIXED_NONCE)
            .setDeliveryType(StopDeliveryCommand.DeliveryType.BOLUS)
            .setBeepType(if (bolusBeeps) BeepType.LONG_SINGLE_BEEP else BeepType.SILENT)
            .build()
        podStateManager.pendingDoseCommand?.let { pending ->
            podStateManager.pendingDoseCommand = pending.copy(
                bolusCancelRequested = true,
                bolusCancelSequenceNumber = podStateManager.msgSequenceNumber.toShort(),
                bolusCancelHistoryId = historyId
            )
        }
        bleManager.sendCommand(cmd, DefaultStatusResponse::class)
            .doOnNext { event ->
                val response = (event as? PodEvent.ResponseReceived)?.response as? DefaultStatusResponse
                if (response != null && (response.sequenceNumberOfLastProgrammingCommand.toInt() and 0x0f) ==
                    podStateManager.pendingDoseCommand?.bolusCancelSequenceNumber?.toInt()?.and(0x0f)
                ) {
                    history.markSent(historyId).andThen(history.markSuccess(historyId)).blockingAwait()
                }
            }
            .ignoreElements()
            .doOnError {
                history.markSendingFailure(historyId).andThen(history.markFailure(historyId)).blockingAwait()
            }
    })

    override fun stopBolusDelivering() {
        aapsLogger.info(LTag.PUMP, "O5 stopBolusDelivering called")
        if (bolusDeliveryInProgress) {
            bolusCanceled = true
        }
    }


    override suspend fun setTempBasalAbsolute(
        absoluteRate: Double,
        durationInMinutes: Int,
        enforceNew: Boolean,
        tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult = doseMutex.withLock { setTempBasalAbsoluteLocked(absoluteRate, durationInMinutes, enforceNew, tbrType) }

    private suspend fun setTempBasalAbsoluteLocked(
        absoluteRate: Double,
        durationInMinutes: Int,
        enforceNew: Boolean,
        tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "O5 setTempBasalAbsolute: rate=$absoluteRate U/h duration=$durationInMinutes min enforce=$enforceNew type=$tbrType")
        if (!pendingDoseResolved()) return unresolvedDoseResult()
        var historyId: Long? = null
        return try {
            if (podStateManager.deliveryStatus?.tempBasalActive() == true) {
                cancelActiveTempBasal()
            }
            val tempBasalBeeps = preferences.get(OmnipodBooleanPreferenceKey.TbrBeepsEnabled)
            val startedAt = System.currentTimeMillis()
            historyId = history.createRecord(
                commandType = OmnipodCommandType.SET_TEMPORARY_BASAL,
                date = startedAt,
                initialResult = InitialResult.NOT_SENT,
                tempBasalRecord = TempBasalRecord(durationInMinutes, absoluteRate)
            ).blockingGet()
            podStateManager.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
                type = O5PodStateManager.PendingDoseType.TEMP_BASAL_START,
                requestedRate = absoluteRate,
                requestedDurationMinutes = durationInMinutes.toShort(),
                startedAt = startedAt,
                sequenceNumber = podStateManager.msgSequenceNumber.toShort(),
                historyId = historyId,
                tempBasalType = tbrType
            )
            armStatusChecker()
            val cmd = ProgramTempBasalCommand.Builder()
                .setUniqueId(requirePodId())
                .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                .setNonce(FIXED_NONCE)
                .setProgramReminder(ProgramReminder(atStart = tempBasalBeeps, atEnd = tempBasalBeeps, atInterval = 0))
                .setRateInUnitsPerHour(absoluteRate)
                .setDurationInMinutes(durationInMinutes.toShort())
                .build()
            bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()

            val accepted = pendingDoseAccepted()
            reconcilePendingDoseLocked()
            if (!accepted || podStateManager.pendingDoseCommand != null) return unresolvedDoseResult()
            queueBasalCorrectionIfNeeded()
            pumpEnactResultProvider.get().success(true).enacted(true).isPercent(false).absolute(absoluteRate).duration(durationInMinutes)
        } catch (e: Exception) {
            historyId?.let { history.markSendingFailure(it).andThen(history.markFailure(it)).blockingAwait() }
            aapsLogger.error(LTag.PUMP, "Error in O5 setTempBasalAbsolute", e)
            notifyUncertain(NotificationId.OMNIPOD_TBR_ALERTS, rh.gs(R.string.omnipod_5_error_setting_temp_basal_might_have_failed))
            pumpEnactResultProvider.get().success(false).enacted(false)
        }
    }

    override suspend fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        error("Pump doesn't support percent basal rate")

    override suspend fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult =
        doseMutex.withLock { cancelTempBasalLocked(enforceNew) }

    private suspend fun cancelTempBasalLocked(enforceNew: Boolean): PumpEnactResult {
        if (!pendingDoseResolved()) return unresolvedDoseResult()
        if (podStateManager.deliveryStatus?.tempBasalActive() != true && pumpSync.expectedPumpState().temporaryBasal == null) {
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }
        return try {
            cancelActiveTempBasal()
            queueBasalCorrectionIfNeeded()
            pumpEnactResultProvider.get().success(true).enacted(true)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error in O5 cancelTempBasal", e)
            notifyUncertain(NotificationId.OMNIPOD_TBR_ALERTS, rh.gs(R.string.omnipod_5_error_cancel_temp_basal_result_is_uncertain))
            pumpEnactResultProvider.get().success(false).enacted(false)
        }
    }

    private suspend fun cancelActiveTempBasal() {
        val startedAt = System.currentTimeMillis()
        val historyId = history.createRecord(
            commandType = OmnipodCommandType.CANCEL_TEMPORARY_BASAL,
            date = startedAt,
            initialResult = InitialResult.NOT_SENT
        ).blockingGet()
        podStateManager.pendingDoseCommand = O5PodStateManager.PendingDoseCommand(
            type = O5PodStateManager.PendingDoseType.TEMP_BASAL_CANCEL,
            startedAt = startedAt,
            sequenceNumber = podStateManager.msgSequenceNumber.toShort(),
            historyId = historyId
        )
        armStatusChecker()
        val tempBasalBeeps = preferences.get(OmnipodBooleanPreferenceKey.TbrBeepsEnabled)
        val cmd = StopDeliveryCommand.Builder()
            .setUniqueId(requirePodId())
            .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
            .setNonce(FIXED_NONCE)
            .setDeliveryType(StopDeliveryCommand.DeliveryType.TEMP_BASAL)
            .setBeepType(if (tempBasalBeeps) BeepType.LONG_SINGLE_BEEP else BeepType.SILENT)
            .build()
        bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()
        history.markSent(historyId).andThen(history.markSuccess(historyId)).blockingAwait()
        podStateManager.activeTempBasalStartTime = null
        podStateManager.activeTempBasalRate = null
        podStateManager.activeTempBasalDurationMinutes = null
        syncTempBasalCancelled(startedAt, historyId)
        notificationManager.dismiss(NotificationId.OMNIPOD_TBR_ALERTS)
        podStateManager.pendingDoseCommand = null
    }

    override suspend fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult =
        pumpEnactResultProvider.get().success(false).enacted(false)
            .comment(rh.gs(R.string.omnipod_5_error_extended_bolus_not_supported))

    override suspend fun cancelExtendedBolus(): PumpEnactResult =
        pumpEnactResultProvider.get().success(false).enacted(false)
            .comment(rh.gs(R.string.omnipod_5_error_extended_bolus_not_supported))

    override val pumpDescription: PumpDescription = Companion.pumpDescription
    override fun manufacturer(): ManufacturerType = ManufacturerType.Insulet
    override fun model(): PumpType = pumpDescription.pumpType
    override fun serialNumber(): String = podStateManager.podId?.toString() ?: UNPAIRED_SERIAL
    override val isFakingTempsByExtendedBoluses: Boolean = false

    override suspend fun loadTDDs(): PumpEnactResult =
        pumpEnactResultProvider.get().success(false).enacted(false)
            .comment(rh.gs(R.string.omnipod_5_error_tdd_not_supported))

    override fun canHandleDST(): Boolean = false

    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult =
        when (customCommand) {
            is CommandPairNewPod      -> pairNewPod()
            is CommandDeactivatePod   -> deactivatePod()
            is CommandSilenceAlerts   -> silenceAlerts()
            is CommandResumeDelivery  -> runBlocking { resumeOrHandleTimeChange() }
            is CommandSuspendDelivery -> suspendDelivery()
            is CommandPlayTestBeep    -> playTestBeep()
            is CommandHandleTimeChange -> runBlocking { resumeOrHandleTimeChange() }
            is CommandUpdateAlertConfiguration -> updateAlertConfiguration()
            is CommandDisableSuspendAlerts     -> disableSuspendAlerts()
            is CommandDeliverBasalCorrection   -> deliverBasalCorrection()
            else                      -> {
                aapsLogger.warn(LTag.PUMP, "Unsupported custom command: " + customCommand.javaClass.name)
                pumpEnactResultProvider.get().success(false).enacted(false).comment(
                    rh.gs(R.string.omnipod_common_error_unsupported_custom_command, customCommand.javaClass.name)
                )
            }
        }

    private fun pairNewPod(): PumpEnactResult =
        try {
            bleManager.pairNewPod().ignoreElements().blockingAwait()
            history.recordSuccess(OmnipodCommandType.INITIALIZE_POD)
            pumpEnactResultProvider.get().success(true).enacted(true)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error pairing new O5 pod", e)
            history.recordFailure(OmnipodCommandType.INITIALIZE_POD)
            pumpEnactResultProvider.get().success(false).enacted(false)
        }

    private fun deactivatePod(): PumpEnactResult =
        try {
            val cmd = DeactivateCommand.Builder()
                .setUniqueId(requirePodId())
                .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                .setNonce(FIXED_NONCE)
                .build()
            bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()
            val podSerial = serialNumber()
            bleManager.removeBond()
            podStateManager.reset()
            history.recordSuccess(OmnipodCommandType.DEACTIVATE_POD)
            notificationManager.dismiss(NotificationId.OMNIPOD_POD_FAULT)
            try {
                runBlocking { recordNoDelivery(pumpSync.expectedPumpState(), podSerial) }
            } catch (e: Exception) {
                aapsLogger.warn(LTag.PUMP, "O5 could not record zero delivery after deactivation: $e")
            }
            pumpEnactResultProvider.get().success(true).enacted(true)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error deactivating O5 pod", e)
            pumpEnactResultProvider.get().success(false).enacted(false)
        }

    private fun silenceAlerts(): PumpEnactResult =
        podStateManager.activeAlerts?.let { alerts ->
            try {
                val cmd = SilenceAlertsCommand.Builder()
                    .setUniqueId(requirePodId())
                    .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                    .setNonce(FIXED_NONCE)
                    .setAlertTypes(alerts)
                    .build()
                bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()
                history.recordSuccess(OmnipodCommandType.ACKNOWLEDGE_ALERTS)
                pumpEnactResultProvider.get().success(true).enacted(true)
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Error silencing O5 alerts", e)
                pumpEnactResultProvider.get().success(false).enacted(false)
            }
        } ?: pumpEnactResultProvider.get().success(false).enacted(false).comment(rh.gs(R.string.omnipod_5_error_no_active_alerts))

    private fun suspendDelivery(): PumpEnactResult =
        try {
            val basalBeeps = preferences.get(OmnipodBooleanPreferenceKey.BasalBeepsEnabled)
            val cmd = SuspendDeliveryCommand.Builder()
                .setUniqueId(requirePodId())
                .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                .setNonce(FIXED_NONCE)
                .setBeepType(if (basalBeeps) BeepType.LONG_SINGLE_BEEP else BeepType.SILENT)
                .build()
            bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()
            podStateManager.deliverySuspended = true
            podStateManager.suspendAlertsEnabled = true
            // Suspending stops any temp basal on the pod as well.
            podStateManager.activeTempBasalStartTime = null
            podStateManager.activeTempBasalRate = null
            podStateManager.activeTempBasalDurationMinutes = null
            history.recordSuccess(OmnipodCommandType.SUSPEND_DELIVERY)
            runBlocking { syncZeroTempBasal(serialNumber()) }
            pumpEnactResultProvider.get().success(true).enacted(true)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error suspending O5 delivery", e)
            pumpEnactResultProvider.get().success(false).enacted(false)
        }

    private fun playTestBeep(): PumpEnactResult =
        try {
            val silentReminder = ProgramReminder(atStart = false, atEnd = false, atInterval = 0)
            val cmd = ProgramBeepsCommand.Builder()
                .setUniqueId(requirePodId())
                .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                .setImmediateBeepType(BeepType.LONG_SINGLE_BEEP)
                .setBasalReminder(silentReminder)
                .setTempBasalReminder(silentReminder)
                .setBolusReminder(silentReminder)
                .build()
            bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()
            history.recordSuccess(OmnipodCommandType.PLAY_TEST_BEEP)
            pumpEnactResultProvider.get().success(true).enacted(true)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error playing O5 test beep", e)
            pumpEnactResultProvider.get().success(false).enacted(false)
        }

    /** Resume Delivery and Handle Time Change are both just "(re)program the current
     *  basal profile" - [ProgramBasalCommand] always sets the pod's current time and
     *  implicitly resumes delivery, same as Dash's identical handling of both. */
    private suspend fun resumeOrHandleTimeChange(): PumpEnactResult =
        pumpSync.expectedPumpState().profile?.let { setNewBasalProfile(it, OmnipodCommandType.RESUME_DELIVERY) }
            ?: pumpEnactResultProvider.get().success(false).enacted(false).comment(rh.gs(R.string.omnipod_5_error_no_active_profile))

    private fun notifyUncertain(id: NotificationId, message: String) {
        if (podStateManager.pendingDoseCommand != null) {
            notificationManager.post(id, message, sound = AlarmSound.BOLUS_ERROR)
        }
    }


    /** Re-pushes expiration/low-reservoir alert config to the pod when preferences have
     *  changed since the last successful push - safe to call often (see [getPumpStatus]'s
     *  call site), since it's a no-op whenever nothing has changed. Mirrors Dash's
     *  updateAlertConfiguration(), minus its isPodRunning/expiry-negative guards (O5's
     *  activationProgress check below covers the same intent). */
    private fun updateAlertConfiguration(): PumpEnactResult {
        val expirationReminderEnabled = preferences.get(OmnipodBooleanPreferenceKey.ExpirationReminder)
        val expirationReminderHours = preferences.get(OmnipodIntPreferenceKey.ExpirationReminderHours)
        val expirationAlarmEnabled = preferences.get(OmnipodBooleanPreferenceKey.ExpirationAlarm)
        val expirationAlarmHours = preferences.get(OmnipodIntPreferenceKey.ExpirationAlarmHours)
        val lowReservoirAlertEnabled = preferences.get(OmnipodBooleanPreferenceKey.LowReservoirAlert)
        val lowReservoirAlertUnits = preferences.get(OmnipodIntPreferenceKey.LowReservoirAlertUnits)
        val current = O5PodStateManager.SyncedAlertSettings(
            expirationReminderEnabled, expirationReminderHours,
            expirationAlarmEnabled, expirationAlarmHours,
            lowReservoirAlertEnabled, lowReservoirAlertUnits
        )

        if (podStateManager.syncedAlertSettings == current) {
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }
        if (podStateManager.activationProgress != ActivationProgress.COMPLETED) {
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }

        return try {
            val alerts = buildO5ExpirationAlerts(podStateManager, preferences, aapsLogger) + AlertConfiguration(
                AlertType.LOW_RESERVOIR,
                enabled = lowReservoirAlertEnabled,
                durationInMinutes = 0,
                autoOff = false,
                AlertTrigger.ReservoirVolumeTrigger((lowReservoirAlertUnits * 10).toShort()),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.XXX
            )
            val cmd = ProgramAlertsCommand.Builder()
                .setUniqueId(requirePodId())
                .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                .setNonce(FIXED_NONCE)
                .setAlertConfigurations(alerts)
                .build()
            bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()
            podStateManager.syncedAlertSettings = current
            history.recordSuccess(OmnipodCommandType.CONFIGURE_ALERTS)
            pumpEnactResultProvider.get().success(true).enacted(true)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error updating O5 alert configuration", e)
            pumpEnactResultProvider.get().success(false).enacted(false)
        }
    }

    /** Silences the pod's SUSPEND_ENDED alert once delivery has resumed - see
     *  [podStateManager]'s suspendAlertsEnabled doc comment. */
    private fun disableSuspendAlerts(): PumpEnactResult {
        if (!podStateManager.suspendAlertsEnabled) {
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }
        return try {
            val cmd = ProgramAlertsCommand.Builder()
                .setUniqueId(requirePodId())
                .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                .setNonce(FIXED_NONCE)
                .setAlertConfigurations(
                    listOf(
                        AlertConfiguration(
                            AlertType.SUSPEND_ENDED, enabled = false, durationInMinutes = 0, autoOff = false,
                            AlertTrigger.TimerTrigger(0), BeepType.FOUR_TIMES_BIP_BEEP, BeepRepetitionType.EVERY_MINUTE_AND_EVERY_15_MIN
                        )
                    )
                )
                .build()
            bleManager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements().blockingAwait()
            podStateManager.suspendAlertsEnabled = false
            history.recordSuccess(OmnipodCommandType.CONFIGURE_ALERTS)
            pumpEnactResultProvider.get().success(true).enacted(true)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error disabling O5 suspend alerts", e)
            pumpEnactResultProvider.get().success(false).enacted(false)
        }
    }


    /**
     * Uses the Dash correction thresholds, cooldown and drift reset.
     * A zero temp basal blocks correction, except within 5 minutes of a bolus (same as Dash).
     * Check only after a temp basal change is finished, not between cancel and replacement.
     * While a dose is pending, its bolus pulses may not be counted yet, so basal looks too high.
     * Drift is not read or reset then.
     */
    private fun needsBasalCorrection(): Boolean {
        val correctionThreshold = -PodConstants.POD_PULSE_BOLUS_UNITS / 2

        if (podStateManager.activationProgress != ActivationProgress.COMPLETED) return false
        if (podStateManager.deliverySuspended || podStateManager.alarmType != null) return false
        if (podStateManager.pendingDoseCommand != null) return false

        podStateManager.lastBasalCorrectionTime?.let {
            if (System.currentTimeMillis() - it < 2 * 60 * 1000L) return false
        }

        val drift = podStateManager.basalDrift

        if (drift >= PodConstants.POD_PULSE_BOLUS_UNITS * 2 || drift <= -PodConstants.POD_PULSE_BOLUS_UNITS * 2) {
            aapsLogger.warn(LTag.PUMP, "Resetting O5 basal drift: drift=${"%.3f".format(drift)}U")
            podStateManager.basalExpected = podStateManager.basalDelivered
            return false
        }

        if (drift > correctionThreshold) return false

        if (podStateManager.activeTempBasalRate == 0.0) {
            val timeSinceLastBolus = podStateManager.lastBolusStartTime?.let { System.currentTimeMillis() - it }
            if (timeSinceLastBolus == null || timeSinceLastBolus >= 5 * 60 * 1000L) return false
        }

        return true
    }

    /**
     * Queues the correction after the current command, like Dash. The queued command
     * checks the drift again before it sends anything.
     */
    private fun queueBasalCorrectionIfNeeded() {
        if (!commandQueue.isCustomCommandInQueue(CommandDeliverBasalCorrection::class) && needsBasalCorrection()) {
            // Queue-worker deadlock guard - don't unwrap the .launch. See CommandQueue kdoc.
            pluginScope.launch { commandQueue.customCommand(CommandDeliverBasalCorrection()) }
        }
    }

    /** Delivers a single POD_PULSE_BOLUS_UNITS (0.05U) correction bolus to true up basal
     *  drift - see [needsBasalCorrection]. Reuses [waitForBolusDeliveryToComplete] for the
     *  same progress-polling/confirmation logic a regular bolus uses. */
    private fun deliverBasalCorrection(): PumpEnactResult = runBlocking {
        doseMutex.withLock { deliverBasalCorrectionLocked() }
    }

    private fun deliverBasalCorrectionLocked(): PumpEnactResult {
        if (podStateManager.pendingDoseCommand != null) {
            aapsLogger.info(LTag.PUMP, "O5 basal correction skipped: an earlier dose is still unresolved")
            return pumpEnactResultProvider.get().success(false).enacted(false)
        }
        if (!needsBasalCorrection()) {
            aapsLogger.info(LTag.PUMP, "O5 basal correction no longer appropriate")
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }
        podStateManager.lastBasalCorrectionTime = System.currentTimeMillis()

        val requestedInsulinAmount = PodConstants.POD_PULSE_BOLUS_UNITS
        syncPumpFlows()
        if (requestedInsulinAmount > reservoirLevel.value.cU) {
            aapsLogger.info(LTag.PUMP, "O5 basal correction skipped: not enough insulin in reservoir")
            return pumpEnactResultProvider.get().success(false).enacted(false)
        }
        if (podStateManager.deliveryStatus?.bolusDeliveringActive() == true) {
            aapsLogger.info(LTag.PUMP, "O5 basal correction skipped: bolus already in progress")
            return pumpEnactResultProvider.get().success(false).enacted(false)
        }

        var commandMayHaveBeenSent = false
        var historyId: Long? = null
        return try {
            bolusDeliveryInProgress = true
            podStateManager.basalCorrectionInProgress = true
            aapsLogger.info(LTag.PUMP, "Delivering O5 basal correction")

            val startedAt = System.currentTimeMillis()
            historyId = history.createRecord(
                commandType = OmnipodCommandType.SET_BOLUS,
                date = startedAt,
                initialResult = InitialResult.NOT_SENT,
                bolusRecord = BolusRecord(requestedInsulinAmount, BolusType.BASAL_CORRECTION)
            ).blockingGet()
            val pendingDose = O5PodStateManager.PendingDoseCommand(
                type = O5PodStateManager.PendingDoseType.BOLUS,
                requestedUnits = requestedInsulinAmount,
                bolusType = BS.Type.NORMAL,
                startedAt = startedAt,
                isBasalCorrection = true,
                sequenceNumber = podStateManager.msgSequenceNumber.toShort(),
                historyId = historyId
            )
            podStateManager.pendingDoseCommand = pendingDose

            val cmd = ProgramBolusCommand.Builder()
                .setUniqueId(requirePodId())
                .setSequenceNumber(podStateManager.msgSequenceNumber.toShort())
                .setNonce(FIXED_NONCE)
                .setNumberOfUnits(requestedInsulinAmount)
                .setDelayBetweenPulsesInEighthSeconds(BOLUS_DELAY_BETWEEN_PULSES_EIGHTH_SECONDS)
                .setProgramReminder(ProgramReminder(atStart = false, atEnd = false, atInterval = 0))
                .setO5BolusInfo(mealUnits = 0.0, correctionUnits = requestedInsulinAmount)
                .build()
            bleManager.sendCommand(cmd, DefaultStatusResponse::class)
                .doOnNext { rememberBolusAcceptance(startedAt, it) }
                .filter { it.isCommandSent() }
                .doOnNext {
                    commandMayHaveBeenSent = true
                    markBolusRecordExpected(startedAt)
                }
                .ignoreElements()
                .blockingAwait()
            armStatusChecker()
            val completion = waitForBolusDeliveryToComplete(requestedInsulinAmount).blockingGet()
            val deliveredUnits = completion.deliveredUnits
            if (deliveredUnits != null && podStateManager.pendingDoseCommand?.confirmedByStatus == true) {
                runBlocking { finalizeBolus(podStateManager.pendingDoseCommand ?: pendingDose, deliveredUnits) }
                aapsLogger.info(LTag.PUMP, "O5 basal correction delivered: $deliveredUnits U")
                pumpEnactResultProvider.get().success(true).enacted(true).bolusDelivered(deliveredUnits)
            } else {
                pumpEnactResultProvider.get().success(true).enacted(true).bolusDelivered(requestedInsulinAmount)
                    .comment(rh.gs(R.string.omnipod_5_error_bolus_delivery_status_uncertain))
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "O5 basal correction delivery failed", e)
            if (!commandMayHaveBeenSent && podStateManager.pendingDoseCommand?.confirmedByStatus != true) {
                podStateManager.pendingDoseCommand = null
                historyId?.let { history.markSendingFailure(it).andThen(history.markFailure(it)).blockingAwait() }
            } else {
                armStatusChecker()
            }
            pumpEnactResultProvider.get().success(false).enacted(false)
        } finally {
            bolusDeliveryInProgress = false
            podStateManager.basalCorrectionInProgress = false
        }
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "omnipod_5_settings",
        titleResId = R.string.omnipod_5_name,
        items = listOfNotNull(
            PreferenceSubScreenDef(
                key = "omnipod_5_beeps",
                titleResId = R.string.omnipod_common_preferences_category_confirmation_beeps,
                items = listOf(
                    OmnipodBooleanPreferenceKey.BolusBeepsEnabled,
                    OmnipodBooleanPreferenceKey.BasalBeepsEnabled,
                    OmnipodBooleanPreferenceKey.SmbBeepsEnabled,
                    OmnipodBooleanPreferenceKey.TbrBeepsEnabled
                )
            ),
            PreferenceSubScreenDef(
                key = "omnipod_5_alerts",
                titleResId = R.string.omnipod_common_preferences_category_alerts,
                items = listOf(
                    OmnipodBooleanPreferenceKey.ExpirationReminder,
                    OmnipodIntPreferenceKey.ExpirationReminderHours,
                    OmnipodBooleanPreferenceKey.ExpirationAlarm,
                    OmnipodIntPreferenceKey.ExpirationAlarmHours,
                    OmnipodBooleanPreferenceKey.LowReservoirAlert,
                    OmnipodIntPreferenceKey.LowReservoirAlertUnits
                )
            ),
            PreferenceSubScreenDef(
                key = "omnipod_5_notifications",
                titleResId = R.string.omnipod_common_preferences_category_notifications,
                items = listOf(
                    OmnipodBooleanPreferenceKey.SoundUncertainTbrNotification,
                    OmnipodBooleanPreferenceKey.SoundUncertainSmbNotification,
                    OmnipodBooleanPreferenceKey.SoundUncertainBolusNotification,
                    DashBooleanPreferenceKey.SoundDeliverySuspendedNotification
                )
            ),
            if (config.isEngineeringMode()) {
                O5IntentKey.CertificateStore.withCompose(
                    ComposeScreenContent { onBack ->
                        O5CertificateStoreScreen(
                            rh = rh,
                            onBack = onBack,
                        )
                    }
                )
            } else {
                null
            },
            O5IntentKey.PodHistory.withCompose(
                ComposeScreenContent { onBack ->
                    val historyViewModel: O5PodHistoryViewModel = metroViewModel()
                    val records by historyViewModel.records.collectAsStateWithLifecycle()
                    OmnipodPodHistoryScreen(
                        records = records,
                        rh = historyViewModel.rh,
                        profileUtil = historyViewModel.profileUtil,
                        pumpType = PumpType.OMNIPOD_5,
                        onBack = onBack,
                    )
                }
            )
        ),
        icon = pluginDescription.icon
    )

}
