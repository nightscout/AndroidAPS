package app.aaps.pump.omnipod.omnipod5.bledriver.pod.state

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
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
import app.aaps.pump.omnipod.omnipod5.keys.O5StringNonPreferenceKey
import com.google.gson.Gson
import java.io.Serializable
import java.util.Calendar
import java.util.EnumSet
import java.util.TimeZone
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * [O5PodStateManager] persisted across app restarts, mirroring
 * `OmnipodDashPodStateManagerImpl`'s Gson + [Preferences] store/load pattern exactly -
 * just for O5's smaller state surface (see [O5PodStateManager]'s class doc for why).
 */
@ContributesBinding(AppScope::class, binding = binding<O5PodStateManager>())
@SingleIn(AppScope::class)
class PersistedO5PodStateManager @Inject constructor(
    private val logger: AAPSLogger,
    private val preferences: Preferences
) : O5PodStateManager {

    private val gson = Gson()

    private var _podState: PodState? = null

    /** Internal (rather than private) to allow unit testing within this module.
     *  Lazily deserialized on first access, matching Dash's rationale: keep Gson
     *  reflection off the main thread during app startup (Dagger constructs this
     *  @Singleton eagerly). */
    internal var podState: PodState
        get() = _podState ?: synchronized(this) { _podState ?: load().also { _podState = it } }
        set(value) {
            synchronized(this) { _podState = value }
        }

    override var bluetoothConnectionState: O5PodStateManager.BluetoothConnectionState
        get() = podState.bluetoothConnectionState
        set(value) {
            podState.bluetoothConnectionState = value
            store()
        }

    override var connectionAttempts: Int
        get() = podState.connectionAttempts
        set(value) {
            podState.connectionAttempts = value
            store()
        }

    override var successfulConnections: Int
        get() = podState.successfulConnections
        set(value) {
            podState.successfulConnections = value
            store()
        }

    override var bluetoothAddress: String?
        get() = podState.bluetoothAddress
        set(value) {
            podState.bluetoothAddress = value
            store()
        }

    override var controllerId: Long?
        get() = podState.controllerId
        set(value) {
            podState.controllerId = value
            store()
        }

    override var podId: Long?
        get() = podState.podId
        set(value) {
            podState.podId = value
            store()
        }

    override var nextPodId: Long?
        get() = podState.nextPodId
        set(value) {
            podState.nextPodId = value
            store()
        }

    override var ltk: ByteArray?
        get() = podState.ltk
        set(value) {
            podState.ltk = value
            store()
        }

    override var msgSequenceNumber: Byte
        get() = podState.msgSequenceNumber
        set(value) {
            podState.msgSequenceNumber = value
            store()
        }

    override var eapAkaSequenceNumber: Long
        get() = podState.eapAkaSequenceNumber
        set(value) {
            podState.eapAkaSequenceNumber = value
            store()
        }

    private var pendingEapAkaSequenceNumber: Long = 0

    override fun increaseMessageSequenceNumber() {
        podState.msgSequenceNumber = ((podState.msgSequenceNumber.toInt() + 1) and 0x0f).toByte()
        store()
    }

    override var activationProgress: ActivationProgress
        get() = podState.activationProgress
        set(value) {
            podState.activationProgress = value
            store()
        }

    override var primePulseRate: Short?
        get() = podState.primePulseRate
        set(value) {
            podState.primePulseRate = value
            store()
        }

    override var firstPrimeBolusVolume: Short?
        get() = podState.firstPrimeBolusVolume
        set(value) {
            podState.firstPrimeBolusVolume = value
            store()
        }

    override var secondPrimeBolusVolume: Short?
        get() = podState.secondPrimeBolusVolume
        set(value) {
            podState.secondPrimeBolusVolume = value
            store()
        }

    override var podLifeInHours: Short?
        get() = podState.podLifeInHours
        set(value) {
            podState.podLifeInHours = value
            store()
        }

    override var basalProgram: BasalProgram?
        get() = podState.basalProgram
        set(value) {
            podState.basalProgram = value
            store()
        }

    override val timeZoneId: String? get() = podState.timeZone
    override val timeZoneOffset: Int? get() = podState.timeZoneOffset
    override val timeZoneUpdated: Long? get() = podState.timeZoneUpdated

    override fun updateTimeZone() {
        val timeZone = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        podState.timeZoneOffset = timeZone.getOffset(now)
        podState.timeZone = timeZone.id
        podState.timeZoneUpdated = now
        store()
    }

    override var deliverySuspended: Boolean
        get() = podState.deliverySuspended
        set(value) {
            podState.deliverySuspended = value
            store()
        }

    override var lastBolusStartTime: Long?
        get() = podState.lastBolusStartTime
        set(value) {
            podState.lastBolusStartTime = value
            store()
        }

    override var lastBolusRequestedUnits: Double?
        get() = podState.lastBolusRequestedUnits
        set(value) {
            podState.lastBolusRequestedUnits = value
            store()
        }

    override var lastBolusDeliveredUnits: Double?
        get() = podState.lastBolusDeliveredUnits
        set(value) {
            podState.lastBolusDeliveredUnits = value
            store()
        }

    override var activeTempBasalStartTime: Long?
        get() = podState.activeTempBasalStartTime
        set(value) {
            podState.activeTempBasalStartTime = value
            store()
        }

    override var activeTempBasalRate: Double?
        get() = podState.activeTempBasalRate
        set(value) {
            podState.activeTempBasalRate = value
            store()
        }

    override var activeTempBasalDurationMinutes: Short?
        get() = podState.activeTempBasalDurationMinutes
        set(value) {
            podState.activeTempBasalDurationMinutes = value
            store()
        }

    override var pendingDoseCommand: O5PodStateManager.PendingDoseCommand?
        get() = podState.pendingDoseCommand
        set(value) {
            podState.pendingDoseCommand = value
            store()
        }

    override val podStatus: PodStatus? get() = podState.podStatus
    override val deliveryStatus: DeliveryStatus? get() = podState.deliveryStatus
    override val firmwareVersion: SoftwareVersion? get() = podState.firmwareVersion
    override val bleVersion: SoftwareVersion? get() = podState.bleVersion
    override val lotNumber: Long? get() = podState.lotNumber
    override val podSequenceNumber: Long? get() = podState.podSequenceNumber
    override val totalPulsesDelivered: Short? get() = podState.totalPulsesDelivered
    override val bolusPulsesRemaining: Short? get() = podState.bolusPulsesRemaining
    override val reservoirPulsesRemaining: Short? get() = podState.reservoirPulsesRemaining?.takeIf { it < 1023 }
    override val activeAlerts: EnumSet<AlertType>? get() = podState.activeAlerts
    override val minutesSinceActivation: Short? get() = podState.minutesSinceActivation
    override val sequenceNumberOfLastProgrammingCommand: Short? get() = podState.sequenceNumberOfLastProgrammingCommand
    override val lastStatusResponseReceived: Long? get() = podState.lastStatusResponseReceived

    override val alarmType: AlarmType? get() = podState.alarmType
    override val pdmRef: String? get() = podState.pdmRef
    override val alarmTime: Short? get() = podState.alarmTime
    override val occlusionAlarm: Boolean? get() = podState.occlusionAlarm
    override val podStatusWhenAlarmOccurred: PodStatus? get() = podState.podStatusWhenAlarmOccurred
    override val rssi: Short? get() = podState.rssi

    private inline fun logBasalTracking(block: () -> Unit) {
        val driftBefore = basalDrift.takeIf { isActivationCompleted } ?: 0.0
        block()
        if (isActivationCompleted) {
            logger.info(
                LTag.PUMP,
                "PUMP_BASAL act=%.2fU (tot=%.2fU bol=%.2fU) exp=%.4fU err=%+.4fU dErr=%+.4fU".format(
                    basalDelivered,
                    (podState.totalPulsesDelivered ?: 0) * PodConstants.POD_PULSE_BOLUS_UNITS,
                    (podState.cumulativeBolusPulsesDelivered ?: 0) * PodConstants.POD_PULSE_BOLUS_UNITS,
                    podState.basalExpected ?: 0.0,
                    basalDrift,
                    basalDrift - driftBefore
                )
            )
        }
    }

    override var alarmSynced: Boolean
        get() = podState.alarmSynced
        set(value) {
            podState.alarmSynced = value
            store()
        }

    override val podActivatedAt: Long? get() = podState.podActivatedAt
    override val triggeredAlertTimes: Map<AlertType, Short>? get() = podState.triggeredAlertTimes

    override var suspendAlertsEnabled: Boolean
        get() = podState.suspendAlertsEnabled
        set(value) {
            podState.suspendAlertsEnabled = value
            store()
        }

    override var syncedAlertSettings: O5PodStateManager.SyncedAlertSettings?
        get() = podState.syncedAlertSettings
        set(value) {
            podState.syncedAlertSettings = value
            store()
        }

    override var basalExpected: Double?
        get() = podState.basalExpected
        set(value) {
            podState.basalExpected = value
            store()
        }

    override var lastBasalCorrectionTime: Long?
        get() = podState.lastBasalCorrectionTime
        set(value) {
            podState.lastBasalCorrectionTime = value
            store()
        }

    override var basalCorrectionInProgress: Boolean
        get() = podState.basalCorrectionInProgress
        set(value) {
            podState.basalCorrectionInProgress = value
            store()
        }

    override var cumulativeBolusPulsesDelivered: Short?
        get() = podState.cumulativeBolusPulsesDelivered
        set(value) {
            podState.cumulativeBolusPulsesDelivered = value
            store()
        }

    override fun increaseEapAkaSequenceNumber(): ByteArray {
        pendingEapAkaSequenceNumber = eapAkaSequenceNumber + 1
        return EapSqn(pendingEapAkaSequenceNumber).value
    }

    override fun commitEapAkaSequenceNumber() {
        eapAkaSequenceNumber = pendingEapAkaSequenceNumber
        store()
    }

    override fun updateFromPairing(controllerId: Long, podId: Long, pairResult: PairResult) {
        podState.controllerId = controllerId
        podState.podId = podId
        podState.ltk = pairResult.ltk
        podState.msgSequenceNumber = pairResult.msgSeq
        store()
    }

    override fun updateFromVersionResponse(response: VersionResponse) {
        podState.podStatus = response.podStatus
        podState.firmwareVersion = SoftwareVersion(
            response.firmwareVersionMajor, response.firmwareVersionMinor, response.firmwareVersionInterim
        )
        podState.bleVersion = SoftwareVersion(
            response.bleVersionMajor, response.bleVersionMinor, response.bleVersionInterim
        )
        podState.lotNumber = response.lotNumber
        podState.podSequenceNumber = response.podSequenceNumber
        podState.lastStatusResponseReceived = System.currentTimeMillis()
        store()
    }

    @Synchronized
    override fun updateFromDefaultStatusResponse(response: DefaultStatusResponse) {
        val previousUpdate = podState.lastStatusResponseReceived
        val now = System.currentTimeMillis()
        logger.debug(LTag.PUMPCOMM, "Default status response :$response")
        logBasalTracking {
            updatePodState(
                response.totalPulsesDelivered,
                response.podStatus,
                response.deliveryStatus,
                response.bolusPulsesRemaining,
                response.reservoirPulsesRemaining,
                response.activeAlerts,
                response.minutesSinceActivation,
                response.sequenceNumberOfLastProgrammingCommand,
                now
            )
            podState.basalExpected = nextBasalExpected(previousUpdate, now)
        }
        store()
    }

    private fun updatePodState(
        totalPulsesDelivered: Short,
        podStatus: PodStatus,
        deliveryStatus: DeliveryStatus,
        bolusPulsesRemaining: Short,
        reservoirPulsesRemaining: Short,
        activeAlerts: EnumSet<AlertType>,
        minutesSinceActivation: Short,
        sequenceNumberOfLastProgrammingCommand: Short,
        now: Long
    ) {
        podState.totalPulsesDelivered = totalPulsesDelivered
        podState.podStatus = podStatus
        podState.deliveryStatus = deliveryStatus
        podState.bolusPulsesRemaining = bolusPulsesRemaining
        if (reservoirPulsesRemaining < 1023) {
            podState.reservoirPulsesRemaining = reservoirPulsesRemaining
        }
        podState.activeAlerts = activeAlerts
        podState.minutesSinceActivation = minutesSinceActivation
        podState.sequenceNumberOfLastProgrammingCommand = sequenceNumberOfLastProgrammingCommand
        podState.pendingDoseCommand?.let { pending ->
            val updated = pending.withPodProgress(sequenceNumberOfLastProgrammingCommand, deliveryStatus, bolusPulsesRemaining)
            val delta = updated.accountedBolusPulses - pending.accountedBolusPulses
            if (delta > 0) podState.cumulativeBolusPulsesDelivered = ((podState.cumulativeBolusPulsesDelivered ?: 0) + delta).toShort()
            podState.pendingDoseCommand = updated
        }
        podState.lastStatusResponseReceived = now
    }

    @Synchronized
    override fun updateFromAlarmStatusResponse(response: AlarmStatusResponse) {
        logger.info(LTag.PUMPCOMM, "Received AlarmStatusResponse: $response")
        logBasalTracking {
            updatePodState(
                response.totalPulsesDelivered,
                response.podStatus,
                response.deliveryStatus,
                response.bolusPulsesRemaining,
                response.reservoirPulsesRemaining,
                response.activeAlerts,
                response.minutesSinceActivation,
                response.sequenceNumberOfLastProgrammingCommand,
                System.currentTimeMillis()
            )
        }
        podState.alarmType = response.alarmType
        podState.alarmTime = response.alarmTime
        // Keep the first fault snapshot, including across app restarts.
        if (podState.pdmRef == null) podState.pdmRef = response.pdmRef
        podState.occlusionAlarm = response.occlusionAlarm
        podState.podStatusWhenAlarmOccurred = response.podStatusWhenAlarmOccurred
        podState.rssi = response.rssi
        store()
    }

    override fun updateFromActivationTimeResponse(response: PodInfoActivationTimeResponse) {
        val calendar = Calendar.getInstance()
        calendar.set(2000 + response.year, response.month - 1, response.day, response.hour, response.minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        podState.podActivatedAt = calendar.timeInMillis
        podState.alarmType = response.faultEventCode
        podState.alarmTime = response.faultTime
        store()
    }

    override fun updateFromTriggeredAlertsResponse(response: PodInfoTriggeredAlertsResponse) {
        podState.triggeredAlertTimes = response.alertActivations.filterValues { it != 0.toShort() }
        store()
    }

    override fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse) {
        podState.primePulseRate = response.primePumpRate
        podState.firstPrimeBolusVolume = response.numberOfEngagingClutchDrivePulses
        podState.secondPrimeBolusVolume = response.numberOfPrimePulses
        podState.podLifeInHours = response.podExpirationTimeInHours
        podState.firmwareVersion = SoftwareVersion(
            response.firmwareVersionMajor, response.firmwareVersionMinor, response.firmwareVersionInterim
        )
        podState.bleVersion = SoftwareVersion(
            response.bleVersionMajor, response.bleVersionMinor, response.bleVersionInterim
        )
        podState.podStatus = response.podStatus
        podState.lotNumber = response.lotNumber
        podState.podSequenceNumber = response.podSequenceNumber
        podState.lastStatusResponseReceived = System.currentTimeMillis()
        store()
    }

    @Synchronized
    override fun completeBolus(startedAt: Long, requestedUnits: Double, deliveredUnits: Double, deliveredPulses: Short?) {
        val alreadyCompleted = podState.lastBolusStartTime == startedAt && podState.lastBolusDeliveredUnits != null
        val pending = podState.pendingDoseCommand?.takeIf { it.startedAt == startedAt }
        if (!alreadyCompleted && deliveredPulses != null && pending?.isBasalCorrection != true) {
            val remainder = (deliveredPulses.toInt() - (pending?.accountedBolusPulses ?: 0)).coerceAtLeast(0)
            podState.cumulativeBolusPulsesDelivered =
                ((podState.cumulativeBolusPulsesDelivered ?: 0) + remainder).toShort()
        }
        podState.lastBolusStartTime = startedAt
        podState.lastBolusRequestedUnits = requestedUnits
        podState.lastBolusDeliveredUnits = deliveredUnits
        if (pending != null) {
            podState.pendingDoseCommand = null
        }
        store()
    }

    override fun reset() {
        val retainedControllerId = podState.controllerId
        val retainedNextPodId = podState.podId?.let(O5IdRotation::nextPodId) ?: podState.nextPodId
        podState = PodState(controllerId = retainedControllerId, nextPodId = retainedNextPodId)
        store()
    }

    private fun store() {
        try {
            val cleanPodState = podState.copy(ltk = null)
            logger.debug(LTag.PUMPCOMM, "Storing O5 Pod state: ${gson.toJson(cleanPodState)}")

            val serialized = gson.toJson(podState)
            preferences.put(O5StringNonPreferenceKey.PodState, serialized)
        } catch (ex: Exception) {
            logger.error(LTag.PUMPCOMM, "Failed to store O5 Pod state", ex)
        }
    }

    private fun load(): PodState {
        if (preferences.getIfExists(O5StringNonPreferenceKey.PodState) != null) {
            try {
                return gson.fromJson(preferences.get(O5StringNonPreferenceKey.PodState), PodState::class.java)
            } catch (ex: Exception) {
                logger.error(LTag.PUMPCOMM, "Failed to deserialize O5 Pod state", ex)
            }
        }
        return PodState()
    }

    /** Internal (rather than private) to allow unit testing within this module. */
    internal data class PodState(
        var bluetoothConnectionState: O5PodStateManager.BluetoothConnectionState =
            O5PodStateManager.BluetoothConnectionState.DISCONNECTED,
        var connectionAttempts: Int = 0,
        var successfulConnections: Int = 0,
        var bluetoothAddress: String? = null,
        var controllerId: Long? = null,
        var podId: Long? = null,
        var nextPodId: Long? = null,
        var ltk: ByteArray? = null,
        var msgSequenceNumber: Byte = 1,
        var eapAkaSequenceNumber: Long = 0,
        var activationProgress: ActivationProgress = ActivationProgress.NOT_STARTED,
        var primePulseRate: Short? = null,
        var firstPrimeBolusVolume: Short? = null,
        var secondPrimeBolusVolume: Short? = null,
        var podLifeInHours: Short? = null,
        var basalProgram: BasalProgram? = null,
        var timeZone: String? = null, // TimeZone ID (e.g. "Europe/Amsterdam")
        var timeZoneOffset: Int? = null,
        var timeZoneUpdated: Long? = null,
        var deliverySuspended: Boolean = false,
        var lastBolusStartTime: Long? = null,
        var lastBolusRequestedUnits: Double? = null,
        var lastBolusDeliveredUnits: Double? = null,
        var activeTempBasalStartTime: Long? = null,
        var activeTempBasalRate: Double? = null,
        var activeTempBasalDurationMinutes: Short? = null,
        var pendingDoseCommand: O5PodStateManager.PendingDoseCommand? = null,
        var podStatus: PodStatus? = null,
        var deliveryStatus: DeliveryStatus? = null,
        var firmwareVersion: SoftwareVersion? = null,
        var bleVersion: SoftwareVersion? = null,
        var lotNumber: Long? = null,
        var podSequenceNumber: Long? = null,
        var totalPulsesDelivered: Short? = null,
        var bolusPulsesRemaining: Short? = null,
        var reservoirPulsesRemaining: Short? = null,
        var activeAlerts: EnumSet<AlertType>? = null,
        var minutesSinceActivation: Short? = null,
        var sequenceNumberOfLastProgrammingCommand: Short? = null,
        var lastStatusResponseReceived: Long? = null,
        var alarmType: AlarmType? = null,
        var pdmRef: String? = null,
        var alarmTime: Short? = null,
        var occlusionAlarm: Boolean? = null,
        var podStatusWhenAlarmOccurred: PodStatus? = null,
        var rssi: Short? = null,
        var alarmSynced: Boolean = false,
        var podActivatedAt: Long? = null,
        var triggeredAlertTimes: Map<AlertType, Short>? = null,
        var suspendAlertsEnabled: Boolean = true,
        var syncedAlertSettings: O5PodStateManager.SyncedAlertSettings? = null,
        var basalExpected: Double? = null,
        var lastBasalCorrectionTime: Long? = null,
        var basalCorrectionInProgress: Boolean = false,
        var cumulativeBolusPulsesDelivered: Short? = null
    ) : Serializable
}
