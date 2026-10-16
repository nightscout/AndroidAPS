package app.aaps.pump.omnipod.omnipod5.bledriver.pod.state

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.omnipod.common.bledriver.comm.pair.PairResult
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodConstants
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.SoftwareVersion
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoActivationTimeResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoTriggeredAlertsResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5IdRotation
import java.io.Serializable
import java.util.Calendar
import java.util.EnumSet
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Persisted/in-memory state needed to connect to, pair with, and read status from an
 * Omnipod 5 pod, plus the dosing/control state ([app.aaps.pump.omnipod.omnipod5.O5PumpPlugin]
 * needs to track basal program, last bolus, active temp basal, and
 * an in-flight dose command whose outcome is still uncertain (BLE response never
 * arrived) so it can be reconciled on the next status poll. Deliberately does NOT use a
 * Dash-style historyId/persisted-ledger (`OmnipodDashPodStateManager.ActiveCommand`)
 * to track in-flight commands - [app.aaps.pump.omnipod.omnipod5.bledriver.comm
 * .O5BleManager.sendCommand] already gives 1:1 request/response correlation per call
 * (unlike Dash's continuous connection-scoped event bus), so [pendingDoseCommand] only
 * needs to be a single flat marker, not a ledger.
 *
 * [updateFromDefaultStatusResponse] also saves command acceptance before another command
 * can replace its sequence. Responses are reused from [VersionResponse]/[DefaultStatusResponse], which turned
 * out to have no Dash-specific assumptions baked in (pure byte-offset parsing), matching
 * the same finding as [app.aaps.pump.omnipod.common.bledriver.pod.command.base
 * .HeaderEnabledCommand].
 */
interface O5PodStateManager {

    enum class BluetoothConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

    var bluetoothConnectionState: BluetoothConnectionState
    var connectionAttempts: Int
    var successfulConnections: Int

    /** The Bluetooth MAC address of the paired pod, once known. */
    var bluetoothAddress: String?

    /** The certificate-derived controller id used for this pod's O5 identity (see
     *  [app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5CertificateStore.controllerId]). */
    var controllerId: Long?

    /** The pod's own id, once paired. */
    var podId: Long?

    var nextPodId: Long?

    /** The pod's long-term key, once paired. Null until [updateFromPairing] is called. */
    var ltk: ByteArray?

    /** Message sequence number to resume from after pairing/reconnection. */
    var msgSequenceNumber: Byte

    /** Advances [msgSequenceNumber] by one, wrapping at 4 bits (0x0-0xf) - the pod's
     *  command-header sequence number, distinct from the BLE-packet-level session
     *  sequence number the [app.aaps.pump.omnipod.common.bledriver.comm.session.Session]
     *  layer manages on its own. Must be called after every command round trip (sent,
     *  send-unconfirmed, or response received) or the pod will NAK every command after
     *  the first in a session - mirrors [OmnipodDashPodStateManager
     *  .increaseMessageSequenceNumber]. */
    fun increaseMessageSequenceNumber()

    var eapAkaSequenceNumber: Long

    /** How far through physical pod activation (pairing, prime, cannula insertion) the
     *  pod has progressed - default [ActivationProgress.NOT_STARTED]. Drives
     *  [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin.isBusy] and lets
     *  [app.aaps.pump.omnipod.omnipod5.ui.wizard.compose.O5OmnipodWizardViewModel] resume
     *  a retried activation without resending already-completed steps. */
    var activationProgress: ActivationProgress


    var primePulseRate: Short?
    var firstPrimeBolusVolume: Short?
    var secondPrimeBolusVolume: Short?
    var podLifeInHours: Short?


    /** The basal program currently believed to be running on the pod. */
    var basalProgram: BasalProgram?

    /** Time zone id of the phone when the pod clock was last set (with [basalProgram]).
     *  Null until the first basal program is sent. */
    val timeZoneId: String?

    /** UTC offset (ms) of the phone when the pod clock was last set. Null until the first
     *  basal program is sent. */
    val timeZoneOffset: Int?

    /** Wall-clock time when [timeZoneOffset] was last set. */
    val timeZoneUpdated: Long?

    /** Records the phone's current time zone as the pod's time zone. Call after every
     *  successful basal program, because that command also sets the pod clock. Mirrors
     *  `OmnipodDashPodStateManager.updateTimeZone`. */
    fun updateTimeZone()

    /** False when the phone's current UTC offset differs from the pod's, so the pod runs
     *  the basal schedule at the wrong hours. True when the pod offset is not known yet
     *  (for example a pod activated before this was tracked), so no false warning is shown. */
    val sameTimeZone: Boolean
        get() {
            val podOffset = timeZoneOffset ?: return true
            return TimeZone.getDefault().getOffset(System.currentTimeMillis()) == podOffset
        }

    /** True while the pod's delivery is suspended (no basal/temp basal delivery). */
    var deliverySuspended: Boolean

    var lastBolusStartTime: Long?
    var lastBolusRequestedUnits: Double?

    /** Null until the bolus is confirmed delivered (fully or partially - see
     *  [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin]'s bolus-completion polling). */
    var lastBolusDeliveredUnits: Double?

    var activeTempBasalStartTime: Long?
    var activeTempBasalRate: Double?
    var activeTempBasalDurationMinutes: Short?

    /**
     * A single dose-affecting command whose outcome is not yet confirmed - set
     * optimistically right before the [app.aaps.pump.omnipod.omnipod5.bledriver.comm
     * .O5BleManager.sendCommand] `Observable` for a bolus/temp-basal/basal-program
     * command is subscribed, and cleared once the next status response confirms (or
     * denies) it actually took effect. This is the field that makes uncertain BLE
     * outcomes (dose sent but response never arrived) recoverable rather than silently
     * lost - see [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin]'s reconciliation logic.
     */
    var pendingDoseCommand: PendingDoseCommand?

    enum class PendingDoseType { BOLUS, TEMP_BASAL_START, TEMP_BASAL_CANCEL, BASAL_PROGRAM }

    data class PendingDoseCommand(
        val type: PendingDoseType,
        val requestedUnits: Double? = null,
        val requestedRate: Double? = null,
        val requestedDurationMinutes: Short? = null,
        /** Only set for [PendingDoseType.BOLUS] - needed to correctly finalize
         *  [app.aaps.core.interfaces.pump.PumpSync.syncBolusWithPumpId] if the original
         *  call never reached its own sync step. */
        val bolusType: BS.Type? = null,
        val startedAt: Long,
        /**
         * The 4-bit command sequence number this dose was sent with, captured from
         * [msgSequenceNumber] immediately before building the command.
         *
         * This is what makes an uncertain outcome *decidable* rather than guessable: the pod
         * reports the sequence number of the last programming command it accepted (see
         * [sequenceNumberOfLastProgrammingCommand]). A later bolus stop replaces that value,
         * so its sequence is saved separately and cannot reject the original bolus.
         * Without a sequence, reconciliation can only look at
         * whether delivery is currently active, which cannot tell a bolus that finished from
         * one that never started.
         *
         * Null only for a [PendingDoseCommand] restored from a state file written before this
         * field existed; reconciliation falls back to the delivery-status heuristic then.
         */
        val sequenceNumber: Short? = null,
        /** True only for the micro-bolus `O5PumpPlugin.deliverBasalCorrection`
         *  issues - excluded from [cumulativeBolusPulsesDelivered]
         *  tracking so it counts toward delivered *basal* insulin, not bolus insulin
         *  (the whole point of the correction is to true up basal drift). */
        val isBasalCorrection: Boolean = false,
        val bolusRecordExpected: Boolean = false,
        /** Pod acceptance saved from a matching sequence or observed bolus delivery,
         *  never from a BLE write alone. */
        val confirmedByStatus: Boolean = false,
        val historyId: Long? = null,
        val tempBasalType: PumpSync.TemporaryBasalType? = null,
        val requestedBasalProgram: BasalProgram? = null,
        /** A stop command replaces the pod's last programming sequence. Keep its intent
         *  separate from acceptance of the original bolus, including across restarts.
         *  Null in older state files, where cancellation intent was not saved. */
        val bolusCancelRequested: Boolean? = false,
        val bolusCancelSequenceNumber: Short? = null,
        val bolusCancelHistoryId: Long? = null,
        /** Ordinary bolus pulses already included in [cumulativeBolusPulsesDelivered].
         *  Saved with each status; completion adds only the remainder. Older files start at zero. */
        val accountedBolusPulses: Int = 0
    ) : Serializable {

        /** Acceptance alone does not make an unrelated status's remaining count belong to this bolus.
         *  A stop sequence is useful only if acceptance of the original bolus was already saved.
         *  Use remaining pulses, not total pulse growth: basal can run at the same time. */
        internal fun deliveredBolusPulses(podSequence: Short?, status: DeliveryStatus?, remaining: Short?): Int? {
            if (type != PendingDoseType.BOLUS || !confirmedByStatus ||
                status == null || status == DeliveryStatus.UNKNOWN || status == DeliveryStatus.PRIMING || remaining == null
            ) return null
            val matchesBolus = sequenceNumber != null && podSequence != null &&
                (sequenceNumber.toInt() and 0x0f) == (podSequence.toInt() and 0x0f)
            val matchesCancel = bolusCancelRequested == true && bolusCancelSequenceNumber != null && podSequence != null &&
                (bolusCancelSequenceNumber.toInt() and 0x0f) == (podSequence.toInt() and 0x0f)
            if (!matchesBolus && !matchesCancel && !status.bolusDeliveringActive()) return null
            val requestedPulses = ((requestedUnits ?: return null) / PodConstants.POD_PULSE_BOLUS_UNITS).roundToInt()
            if (remaining.toInt() !in 0..requestedPulses) return null
            return requestedPulses - remaining
        }

        /** Saved progress only grows, so repeated status never adds the same ordinary bolus pulses twice. */
        internal fun withPodProgress(podSequence: Short, status: DeliveryStatus, remaining: Short): PendingDoseCommand {
            val accepted = withPodAcceptance(podSequence, status)
            if (isBasalCorrection) return accepted
            val delivered = accepted.deliveredBolusPulses(podSequence, status, remaining) ?: return accepted
            return if (delivered > accountedBolusPulses) accepted.copy(accountedBolusPulses = delivered) else accepted
        }
    }


    val podStatus: PodStatus?
    val deliveryStatus: DeliveryStatus?

    val isActivationCompleted: Boolean
        get() = activationProgress == ActivationProgress.COMPLETED

    val isSuspended: Boolean
        get() = deliveryStatus?.equals(DeliveryStatus.SUSPENDED) == true

    val isPodRunning: Boolean
        get() = podStatus?.isRunning() == true

    /**
     * The pod has stopped delivering for good - it faulted or was deactivated.
     *
     * Derived from [podStatus], which every routine status poll refreshes, so this is the
     * dependable fault signal. [alarmType] carries the *reason* but only arrives with an
     * alarm-status response, which the pod sends solely in reply to an explicit request for
     * that page - so a fault would otherwise go unnoticed until something asked. Mirrors the
     * Dash driver's `isPodKaput`.
     */
    val isPodKaput: Boolean
        get() = podStatus in arrayOf(PodStatus.ALARM, PodStatus.DEACTIVATED) || isPodActivationTimeExceeded
    val isPodActivationTimeExceeded: Boolean
        get() = podStatus?.isActivationTimeExceeded() == true
    val firmwareVersion: SoftwareVersion?
    val bleVersion: SoftwareVersion?
    val lotNumber: Long?
    val podSequenceNumber: Long?
    val totalPulsesDelivered: Short?
    val bolusPulsesRemaining: Short?
    val reservoirPulsesRemaining: Short?
    val activeAlerts: EnumSet<AlertType>?
    val minutesSinceActivation: Short?
    val sequenceNumberOfLastProgrammingCommand: Short?

    /** System.currentTimeMillis() when the last status was received, or null if never. */
    val lastStatusResponseReceived: Long?


    val alarmType: AlarmType?
    /** Pod-clock minutes-since-activation timestamp of when the alarm occurred (not a
     *  wall-clock time - the pod has no wall clock of its own). */
    val alarmTime: Short?
    val occlusionAlarm: Boolean?
    val podStatusWhenAlarmOccurred: PodStatus?
    val rssi: Short?

    /** True once the current [alarmType] has already produced a user-facing notification
     *  and a [app.aaps.core.interfaces.pump.PumpSync.insertAnnouncement] entry - set by
     *  [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin]'s checkPodFault() so a still-faulted pod
     *  doesn't re-notify on every status poll. Mirrors [OmnipodDashPodStateManager
     *  .alarmSynced]; reset back to false only via [reset] (a new pod pairing). */
    var alarmSynced: Boolean


    /** Wall-clock epoch millis the pod was activated, computed from status page 5's
     *  date fields - unlike [minutesSinceActivation], this survives across app
     *  restarts without drifting (it isn't a relative counter). Null until page 5 has
     *  been fetched at least once. */
    val podActivatedAt: Long?

    /** Pod-clock minutes-since-activation each [AlertType] slot last triggered, from
     *  status page 1. Only contains entries for slots that have actually triggered
     *  (value 0 = never triggered, per OmnipodKit's own convention, so those are
     *  filtered out here). Null until page 1 has been fetched at least once. */
    val triggeredAlertTimes: Map<AlertType, Short>?


    /** True while the pod's SUSPEND_ENDED alert is still armed from a prior suspend -
     *  set by [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin]'s suspendDelivery(), cleared by
     *  its disableSuspendAlerts() once delivery resumes (silences the nuisance beep for a
     *  suspend/resume the user/algorithm caused intentionally). */
    var suspendAlertsEnabled: Boolean

    data class SyncedAlertSettings(
        val expirationReminderEnabled: Boolean,
        val expirationReminderHours: Int,
        val expirationAlarmEnabled: Boolean,
        val expirationAlarmHours: Int,
        val lowReservoirAlertEnabled: Boolean,
        val lowReservoirAlertUnits: Int
    ) : Serializable

    /** The alert preference values last successfully pushed to the pod - null until
     *  [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin]'s updateAlertConfiguration() first
     *  succeeds. Compared against current preferences to skip redundant re-syncs. */
    var syncedAlertSettings: SyncedAlertSettings?


    /** Integrated expected basal delivery (units) since [O5PodStateManager] started
     *  tracking it (right after activation completes) - compared against actual delivered
     *  basal insulin ([app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.basalDelivered])
     *  to detect drift. Null until the first post-activation status poll. */
    var basalExpected: Double?

    /** Wall-clock time of the last basal-drift correction bolus - a cooldown to prevent
     *  rapid repeated corrections. Null if none has ever been delivered. */
    var lastBasalCorrectionTime: Long?

    /** True while a basal-drift correction bolus is in flight - prevents
     *  [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin.deliverTreatment]
     *  concurrency guards from misreading it as a user-requested bolus. */
    var basalCorrectionInProgress: Boolean

    /** Cumulative pod pulses attributed to boluses (not basal) since activation completed -
     *  subtracted from [totalPulsesDelivered] to isolate delivered basal insulin. Excludes
     *  basal-correction boluses themselves (see [PendingDoseCommand.isBasalCorrection]).
     *  Initialized to [totalPulsesDelivered] at the moment activation completes (see
     *  [app.aaps.pump.omnipod.omnipod5.ui.wizard.compose.O5OmnipodWizardViewModel]), so
     *  priming/cannula-insertion pulses are excluded from the basal bucket too. */
    var cumulativeBolusPulsesDelivered: Short?

    fun updateFromVersionResponse(response: VersionResponse)
    fun updateFromDefaultStatusResponse(response: DefaultStatusResponse)
    fun updateFromAlarmStatusResponse(response: AlarmStatusResponse)

    /** Populates [podActivatedAt] plus [alarmType]/[alarmTime] (page 5 reports the same
     *  fault info [AlarmStatusResponse] does). */
    fun updateFromActivationTimeResponse(response: PodInfoActivationTimeResponse)

    /** Populates [triggeredAlertTimes] from page 1's 8 alert-slot values. */
    fun updateFromTriggeredAlertsResponse(response: PodInfoTriggeredAlertsResponse)

    /** Populates the prime-bolus parameters ([primePulseRate], [firstPrimeBolusVolume],
     *  [secondPrimeBolusVolume], [podLifeInHours]) plus version/status/lot/sequence
     *  fields - mirrors [OmnipodDashPodStateManager.updateFromSetUniqueIdResponse]. */
    fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse)

    /**
     * Returns the next EAP-AKA sequence number (as its 6-byte on-wire [EapSqn]
     * representation) without yet committing it - matching
     * [OmnipodDashPodStateManager]'s increment-then-commit-on-success pattern, so a
     * failed session establishment doesn't advance the persisted counter.
     */
    fun increaseEapAkaSequenceNumber(): ByteArray
    fun commitEapAkaSequenceNumber()

    fun updateFromPairing(controllerId: Long, podId: Long, pairResult: PairResult)

    fun completeBolus(startedAt: Long, requestedUnits: Double, deliveredUnits: Double, deliveredPulses: Short?)

    fun reset()
}

/**
 * Simple in-memory [O5PodStateManager]. Not persisted across process restarts - use
 * [PersistedO5PodStateManager] for that; this is mainly useful for tests.
 */
class InMemoryO5PodStateManager : O5PodStateManager {

    @Volatile override var bluetoothConnectionState: O5PodStateManager.BluetoothConnectionState =
        O5PodStateManager.BluetoothConnectionState.DISCONNECTED

    private val connectionAttemptsCounter = AtomicInteger(0)
    override var connectionAttempts: Int
        get() = connectionAttemptsCounter.get()
        set(value) { connectionAttemptsCounter.set(value) }

    private val successfulConnectionsCounter = AtomicInteger(0)
    override var successfulConnections: Int
        get() = successfulConnectionsCounter.get()
        set(value) { successfulConnectionsCounter.set(value) }

    @Volatile override var bluetoothAddress: String? = null
    @Volatile override var controllerId: Long? = null
    @Volatile override var podId: Long? = null
    @Volatile override var nextPodId: Long? = null
    @Volatile override var ltk: ByteArray? = null
    @Volatile override var msgSequenceNumber: Byte = 1

    @Volatile override var eapAkaSequenceNumber: Long = 0
    @Volatile private var pendingEapAkaSequenceNumber: Long = 0

    @Volatile override var activationProgress: ActivationProgress = ActivationProgress.NOT_STARTED
    @Volatile override var primePulseRate: Short? = null
    @Volatile override var firstPrimeBolusVolume: Short? = null
    @Volatile override var secondPrimeBolusVolume: Short? = null
    @Volatile override var podLifeInHours: Short? = null

    @Volatile override var basalProgram: BasalProgram? = null
    @Volatile override var timeZoneId: String? = null
        private set
    @Volatile override var timeZoneOffset: Int? = null
        private set
    @Volatile override var timeZoneUpdated: Long? = null
        private set
    @Volatile override var deliverySuspended: Boolean = false
    @Volatile override var lastBolusStartTime: Long? = null
    @Volatile override var lastBolusRequestedUnits: Double? = null
    @Volatile override var lastBolusDeliveredUnits: Double? = null
    @Volatile override var activeTempBasalStartTime: Long? = null
    @Volatile override var activeTempBasalRate: Double? = null
    @Volatile override var activeTempBasalDurationMinutes: Short? = null
    @Volatile override var pendingDoseCommand: O5PodStateManager.PendingDoseCommand? = null

    override fun increaseMessageSequenceNumber() {
        msgSequenceNumber = ((msgSequenceNumber.toInt() + 1) and 0x0f).toByte()
    }

    override fun updateTimeZone() {
        val timeZone = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        timeZoneOffset = timeZone.getOffset(now)
        timeZoneId = timeZone.id
        timeZoneUpdated = now
    }

    @Volatile override var podStatus: PodStatus? = null
        private set
    @Volatile override var deliveryStatus: DeliveryStatus? = null
        private set
    @Volatile override var firmwareVersion: SoftwareVersion? = null
        private set
    @Volatile override var bleVersion: SoftwareVersion? = null
        private set
    @Volatile override var lotNumber: Long? = null
        private set
    @Volatile override var podSequenceNumber: Long? = null
        private set
    @Volatile override var totalPulsesDelivered: Short? = null
        private set
    @Volatile override var bolusPulsesRemaining: Short? = null
        private set
    @Volatile override var reservoirPulsesRemaining: Short? = null
        private set
    @Volatile override var activeAlerts: EnumSet<AlertType>? = null
        private set
    @Volatile override var minutesSinceActivation: Short? = null
        private set
    @Volatile override var sequenceNumberOfLastProgrammingCommand: Short? = null
        private set
    @Volatile override var lastStatusResponseReceived: Long? = null
        private set

    @Volatile override var alarmType: AlarmType? = null
        private set
    @Volatile override var alarmTime: Short? = null
        private set
    @Volatile override var occlusionAlarm: Boolean? = null
        private set
    @Volatile override var podStatusWhenAlarmOccurred: PodStatus? = null
        private set
    @Volatile override var rssi: Short? = null
        private set
    @Volatile override var alarmSynced: Boolean = false

    @Volatile override var podActivatedAt: Long? = null
        private set
    @Volatile override var triggeredAlertTimes: Map<AlertType, Short>? = null
        private set

    @Volatile override var suspendAlertsEnabled: Boolean = true
    @Volatile override var syncedAlertSettings: O5PodStateManager.SyncedAlertSettings? = null
    @Volatile override var basalExpected: Double? = null
    @Volatile override var lastBasalCorrectionTime: Long? = null
    @Volatile override var basalCorrectionInProgress: Boolean = false
    @Volatile override var cumulativeBolusPulsesDelivered: Short? = null

    override fun increaseEapAkaSequenceNumber(): ByteArray {
        pendingEapAkaSequenceNumber = eapAkaSequenceNumber + 1
        return EapSqn(pendingEapAkaSequenceNumber).value
    }

    override fun commitEapAkaSequenceNumber() {
        eapAkaSequenceNumber = pendingEapAkaSequenceNumber
    }

    override fun updateFromPairing(controllerId: Long, podId: Long, pairResult: PairResult) {
        this.controllerId = controllerId
        this.podId = podId
        ltk = pairResult.ltk
        msgSequenceNumber = pairResult.msgSeq
    }

    override fun updateFromVersionResponse(response: VersionResponse) {
        podStatus = response.podStatus
        firmwareVersion = SoftwareVersion(response.firmwareVersionMajor, response.firmwareVersionMinor, response.firmwareVersionInterim)
        bleVersion = SoftwareVersion(response.bleVersionMajor, response.bleVersionMinor, response.bleVersionInterim)
        lotNumber = response.lotNumber
        podSequenceNumber = response.podSequenceNumber
        lastStatusResponseReceived = System.currentTimeMillis()
    }

    @Synchronized
    override fun updateFromDefaultStatusResponse(response: DefaultStatusResponse) {
        val previousUpdate = lastStatusResponseReceived
        val now = System.currentTimeMillis()
        totalPulsesDelivered = response.totalPulsesDelivered
        updatePendingBolus(response.sequenceNumberOfLastProgrammingCommand, response.deliveryStatus, response.bolusPulsesRemaining)
        basalExpected = nextBasalExpected(previousUpdate, now)
        podStatus = response.podStatus
        deliveryStatus = response.deliveryStatus
        bolusPulsesRemaining = response.bolusPulsesRemaining
        if (response.reservoirPulsesRemaining < 1023) {
            reservoirPulsesRemaining = response.reservoirPulsesRemaining
        }
        activeAlerts = response.activeAlerts
        minutesSinceActivation = response.minutesSinceActivation
        sequenceNumberOfLastProgrammingCommand = response.sequenceNumberOfLastProgrammingCommand
        lastStatusResponseReceived = now
    }

    @Synchronized
    override fun updateFromAlarmStatusResponse(response: AlarmStatusResponse) {
        podStatus = response.podStatus
        deliveryStatus = response.deliveryStatus
        totalPulsesDelivered = response.totalPulsesDelivered
        bolusPulsesRemaining = response.bolusPulsesRemaining
        if (response.reservoirPulsesRemaining < 1023) {
            reservoirPulsesRemaining = response.reservoirPulsesRemaining
        }
        activeAlerts = response.activeAlerts
        minutesSinceActivation = response.minutesSinceActivation
        sequenceNumberOfLastProgrammingCommand = response.sequenceNumberOfLastProgrammingCommand
        updatePendingBolus(response.sequenceNumberOfLastProgrammingCommand, response.deliveryStatus, response.bolusPulsesRemaining)
        alarmType = response.alarmType
        alarmTime = response.alarmTime
        occlusionAlarm = response.occlusionAlarm
        podStatusWhenAlarmOccurred = response.podStatusWhenAlarmOccurred
        rssi = response.rssi
        lastStatusResponseReceived = System.currentTimeMillis()
    }

    private fun updatePendingBolus(sequence: Short, status: DeliveryStatus, remaining: Short) {
        val pending = pendingDoseCommand ?: return
        val updated = pending.withPodProgress(sequence, status, remaining)
        val delta = updated.accountedBolusPulses - pending.accountedBolusPulses
        if (delta > 0) cumulativeBolusPulsesDelivered = ((cumulativeBolusPulsesDelivered ?: 0) + delta).toShort()
        pendingDoseCommand = updated
    }

    override fun updateFromActivationTimeResponse(response: PodInfoActivationTimeResponse) {
        val calendar = Calendar.getInstance()
        calendar.set(2000 + response.year, response.month - 1, response.day, response.hour, response.minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        podActivatedAt = calendar.timeInMillis
        alarmType = response.faultEventCode
        alarmTime = response.faultTime
    }

    override fun updateFromTriggeredAlertsResponse(response: PodInfoTriggeredAlertsResponse) {
        triggeredAlertTimes = response.alertActivations.filterValues { it != 0.toShort() }
    }

    override fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse) {
        primePulseRate = response.primePumpRate
        firstPrimeBolusVolume = response.numberOfEngagingClutchDrivePulses
        secondPrimeBolusVolume = response.numberOfPrimePulses
        podLifeInHours = response.podExpirationTimeInHours
        firmwareVersion = SoftwareVersion(response.firmwareVersionMajor, response.firmwareVersionMinor, response.firmwareVersionInterim)
        bleVersion = SoftwareVersion(response.bleVersionMajor, response.bleVersionMinor, response.bleVersionInterim)
        podStatus = response.podStatus
        lotNumber = response.lotNumber
        podSequenceNumber = response.podSequenceNumber
        lastStatusResponseReceived = System.currentTimeMillis()
    }

    @Synchronized
    override fun completeBolus(startedAt: Long, requestedUnits: Double, deliveredUnits: Double, deliveredPulses: Short?) {
        val alreadyCompleted = lastBolusStartTime == startedAt && lastBolusDeliveredUnits != null
        val pending = pendingDoseCommand?.takeIf { it.startedAt == startedAt }
        if (!alreadyCompleted && deliveredPulses != null && pending?.isBasalCorrection != true) {
            val remainder = (deliveredPulses.toInt() - (pending?.accountedBolusPulses ?: 0)).coerceAtLeast(0)
            cumulativeBolusPulsesDelivered = ((cumulativeBolusPulsesDelivered ?: 0) + remainder).toShort()
        }
        lastBolusStartTime = startedAt
        lastBolusRequestedUnits = requestedUnits
        lastBolusDeliveredUnits = deliveredUnits
        if (pendingDoseCommand?.startedAt == startedAt) {
            pendingDoseCommand = null
        }
    }

    override fun reset() {
        val retainedControllerId = controllerId
        val retainedNextPodId = podId?.let(O5IdRotation::nextPodId) ?: nextPodId
        bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.DISCONNECTED
        connectionAttemptsCounter.set(0)
        successfulConnectionsCounter.set(0)
        bluetoothAddress = null
        controllerId = retainedControllerId
        podId = null
        nextPodId = retainedNextPodId
        ltk = null
        msgSequenceNumber = 1
        eapAkaSequenceNumber = 0
        pendingEapAkaSequenceNumber = 0
        activationProgress = ActivationProgress.NOT_STARTED
        primePulseRate = null
        firstPrimeBolusVolume = null
        secondPrimeBolusVolume = null
        podLifeInHours = null
        basalProgram = null
        timeZoneId = null
        timeZoneOffset = null
        timeZoneUpdated = null
        deliverySuspended = false
        lastBolusStartTime = null
        lastBolusRequestedUnits = null
        lastBolusDeliveredUnits = null
        activeTempBasalStartTime = null
        activeTempBasalRate = null
        activeTempBasalDurationMinutes = null
        pendingDoseCommand = null
        podStatus = null
        deliveryStatus = null
        firmwareVersion = null
        bleVersion = null
        lotNumber = null
        podSequenceNumber = null
        totalPulsesDelivered = null
        bolusPulsesRemaining = null
        reservoirPulsesRemaining = null
        activeAlerts = null
        minutesSinceActivation = null
        sequenceNumberOfLastProgrammingCommand = null
        lastStatusResponseReceived = null
        alarmType = null
        alarmTime = null
        occlusionAlarm = null
        podStatusWhenAlarmOccurred = null
        rssi = null
        alarmSynced = false
        podActivatedAt = null
        triggeredAlertTimes = null
        suspendAlertsEnabled = true
        syncedAlertSettings = null
        basalExpected = null
        lastBasalCorrectionTime = null
        basalCorrectionInProgress = false
        cumulativeBolusPulsesDelivered = null
    }
}
