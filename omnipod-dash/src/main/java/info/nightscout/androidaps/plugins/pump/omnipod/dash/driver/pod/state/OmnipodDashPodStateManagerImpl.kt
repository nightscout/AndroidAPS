package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state

import android.os.SystemClock
import com.google.gson.Gson
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.omnipod.dash.EventOmnipodDashPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair.PairResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session.EapSqn
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.DefaultStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.SetUniqueIdResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.VersionResponse
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashPodStateManagerImpl @Inject constructor(
    private val logger: AAPSLogger,
    private val sharedPreferences: SP,
    private val rxBus: RxBusWrapper
) : OmnipodDashPodStateManager {

    private var podState: PodState

    init {
        podState = load()
    }

    override var activationProgress: ActivationProgress
        get() = podState.activationProgress
        set(activationProgress) {
            podState.activationProgress = activationProgress
            store()
        }

    override val isUniqueIdSet: Boolean
        get() = activationProgress.isAtLeast(ActivationProgress.SET_UNIQUE_ID)

    override val isActivationCompleted: Boolean
        get() = activationProgress == ActivationProgress.COMPLETED

    override val isSuspended: Boolean
        get() = podState.deliveryStatus?.equals(DeliveryStatus.SUSPENDED)
            ?: false

    override val isPodKaput: Boolean
        get() = podState.podStatus in arrayOf(PodStatus.ALARM, PodStatus.DEACTIVATED)

    override val isPodRunning: Boolean
        get() = podState.podStatus?.isRunning() ?: false

    override val lastUpdatedSystem: Long
        get() = podState.lastUpdatedSystem

    override val messageSequenceNumber: Short
        get() = podState.messageSequenceNumber

    override val sequenceNumberOfLastProgrammingCommand: Short?
        get() = podState.sequenceNumberOfLastProgrammingCommand

    override val activationTime: Long?
        get() = podState.activationTime

    override var uniqueId: Long?
        get() = podState.uniqueId
        set(uniqueId) {
            if (podState.uniqueId == null) {
                podState.uniqueId = uniqueId
                store()
            } else if (uniqueId != podState.uniqueId) {
                throw IllegalStateException("Trying to set Unique ID to $uniqueId, but it is already set to ${podState.uniqueId}")
            }
        }

    override var bluetoothAddress: String?
        get() = podState.bluetoothAddress
        set(bluetoothAddress) {
            if (podState.bluetoothAddress == null) {
                podState.bluetoothAddress = bluetoothAddress
                store()
            } else if (bluetoothAddress != podState.bluetoothAddress) {
                throw IllegalStateException("Trying to set Bluetooth Address to $bluetoothAddress, but it is already set to ${podState.bluetoothAddress}")
            }
        }

    override var connectionAttempts: Int
        @Synchronized
        get() = podState.connectionAttempts
        @Synchronized
        set(value) {
            podState.connectionAttempts = value
        }

    override var successfulConnections: Int
        @Synchronized
        get() = podState.successfulConnections
        @Synchronized
        set(value) {
            podState.successfulConnections = value
        }

    override var timeZone: TimeZone
        get() = TimeZone.getTimeZone(podState.timeZone)
        set(tz) {
            podState.timeZone = tz.getDisplayName(false, TimeZone.SHORT)
            store()
        }

    override val sameTimeZone: Boolean
        get() {
            val now = System.currentTimeMillis()
            return TimeZone.getDefault().getOffset(now) == timeZone.getOffset(now)
        }

    override val bluetoothVersion: SoftwareVersion?
        get() = podState.bleVersion

    override val firmwareVersion: SoftwareVersion?
        get() = podState.firmwareVersion

    override val lotNumber: Long?
        get() = podState.lotNumber

    override val podSequenceNumber: Long?
        get() = podState.podSequenceNumber

    override val pulseRate: Short?
        get() = podState.pulseRate

    override val primePulseRate: Short?
        get() = podState.primePulseRate

    override val podLifeInHours: Short?
        get() = podState.podLifeInHours

    override val firstPrimeBolusVolume: Short?
        get() = podState.firstPrimeBolusVolume

    override val secondPrimeBolusVolume: Short?
        get() = podState.secondPrimeBolusVolume

    override val pulsesDelivered: Short?
        get() = podState.pulsesDelivered

    override val pulsesRemaining: Short?
        get() = podState.pulsesRemaining

    override val podStatus: PodStatus?
        get() = podState.podStatus

    override val deliveryStatus: DeliveryStatus?
        get() = podState.deliveryStatus

    override val minutesSinceActivation: Short?
        get() = podState.minutesSinceActivation

    override val activeAlerts: EnumSet<AlertType>?
        get() = podState.activeAlerts

    override val alarmType: AlarmType?
        get() = podState.alarmType

    override var tempBasal: OmnipodDashPodStateManager.TempBasal?
        get() = podState.tempBasal
        set(tempBasal) {
            podState.tempBasal = tempBasal
            rxBus.send(EventOmnipodDashPumpValuesChanged())
            store()
        }

    override val lastBolus: OmnipodDashPodStateManager.LastBolus?
        @Synchronized
        get() = podState.lastBolus

    override val tempBasalActive: Boolean
        get() = !isSuspended && tempBasal?.let {
            it.startTime + it.durationInMinutes * 60 * 1000 > System.currentTimeMillis()
        } ?: false

    override var basalProgram: BasalProgram?
        get() = podState.basalProgram
        set(basalProgram) {
            podState.basalProgram = basalProgram
            rxBus.send(EventOmnipodDashPumpValuesChanged())
            store()
        }

    override val lastStatusResponseReceived: Long
        get() = podState.lastStatusResponseReceived

    override val time: ZonedDateTime?
        get() {
            val minutesSinceActivation = podState.minutesSinceActivation
            val activationTime = podState.activationTime
            if ((activationTime != null) && (minutesSinceActivation != null)) {
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(activationTime), timeZone.toZoneId())
                    .plusMinutes(minutesSinceActivation.toLong())
                    .plus(Duration.ofMillis(System.currentTimeMillis() - lastUpdatedSystem))
            }
            return null
        }

    override val timeDrift: Duration?
        get() {
            return Duration.between(ZonedDateTime.now(), time)
        }

    override val expiry: ZonedDateTime?
        get() {
            val podLifeInHours = podLifeInHours
            val minutesSinceActivation = podState.minutesSinceActivation
            if (podLifeInHours != null && minutesSinceActivation != null) {
                return ZonedDateTime.now()
                    .plusHours(podLifeInHours.toLong())
                    .minusMinutes(minutesSinceActivation.toLong())
                    .plus(Duration.ofMillis(System.currentTimeMillis() - lastUpdatedSystem))
            }
            return null
        }

    override var bluetoothConnectionState: OmnipodDashPodStateManager.BluetoothConnectionState
        @Synchronized
        get() = podState.bluetoothConnectionState
        @Synchronized
        set(bluetoothConnectionState) {
            podState.bluetoothConnectionState = bluetoothConnectionState
            rxBus.send(EventOmnipodDashPumpValuesChanged())
            // do not store
        }

    override fun increaseMessageSequenceNumber() {
        podState.messageSequenceNumber = ((podState.messageSequenceNumber.toInt() + 1) and 0x0f).toShort()
        store()
    }

    override var eapAkaSequenceNumber: Long
        get() = podState.eapAkaSequenceNumber
        set(eapAkaSequenceNumber) {
            podState.eapAkaSequenceNumber = eapAkaSequenceNumber
            store()
        }

    override var ltk: ByteArray?
        get() = podState.ltk
        set(ltk) {
            podState.ltk = ltk
            store()
        }

    override val activeCommand: OmnipodDashPodStateManager.ActiveCommand?
        get() = podState.activeCommand

    @Synchronized
    override fun createLastBolus(requestedUnits: Double, historyId: String, bolusType: DetailedBolusInfo.BolusType) {
        podState.lastBolus = OmnipodDashPodStateManager.LastBolus(
            startTime = System.currentTimeMillis(),
            requestedUnits = requestedUnits,
            bolusUnitsRemaining = requestedUnits,
            deliveryComplete = false, // cancelled, delivered 100% or pod failure
            historyId = historyId,
            bolusType = bolusType
        )
    }

    @Synchronized
    override fun markLastBolusComplete(): OmnipodDashPodStateManager.LastBolus? {
        val lastBolus = podState.lastBolus

        lastBolus?.run {
            this.deliveryComplete = true
        }
            ?: logger.error(LTag.PUMP, "Trying to mark null bolus as complete")

        return lastBolus
    }

    private fun updateLastBolusFromResponse(bolusPulsesRemaining: Short) {
        podState.lastBolus?.run {
            val remainingUnits = bolusPulsesRemaining.toDouble() * 0.05
            this.bolusUnitsRemaining = remainingUnits
            if (remainingUnits == 0.0) {
                this.deliveryComplete = true
            }
        }
    }

    @Synchronized
    override fun createActiveCommand(
        historyId: String,
        basalProgram: BasalProgram?,
        tempBasal: OmnipodDashPodStateManager.TempBasal?,
        requestedBolus: Double?
    ):
        Single<OmnipodDashPodStateManager.ActiveCommand> {
        return Single.create { source ->
            if (activeCommand == null) {
                val command = OmnipodDashPodStateManager.ActiveCommand(
                    podState.messageSequenceNumber,
                    createdRealtime = SystemClock.elapsedRealtime(),
                    historyId = historyId,
                    sendError = null,
                    basalProgram = basalProgram,
                    tempBasal = tempBasal,
                    requestedBolus = requestedBolus
                )
                podState.activeCommand = command
                source.onSuccess(command)
            } else {
                source.onError(
                    java.lang.IllegalStateException(
                        "Trying to send a command " +
                            "and the last command was not confirmed"
                    )
                )
            }
        }
    }

    @Synchronized
    override fun observeNoActiveCommand(): Completable {
        return Completable.defer {
            if (activeCommand == null) {
                Completable.complete()
            } else {
                logger.warn(LTag.PUMP, "Active command already existing: $activeCommand")
                Completable.error(
                    java.lang.IllegalStateException(
                        "Trying to send a command " +
                            "and the last command was not confirmed"
                    )
                )
            }
        }
    }

    override fun recoverActivationFromPodStatus(): String? {
        val newActivationProgress = when (podState.podStatus) {
            PodStatus.FILLED ->
                ActivationProgress.NOT_STARTED
            PodStatus.UID_SET ->
                ActivationProgress.SET_UNIQUE_ID
            PodStatus.ENGAGING_CLUTCH_DRIVE, PodStatus.PRIMING ->
                return "Busy"
            PodStatus.CLUTCH_DRIVE_ENGAGED ->
                ActivationProgress.PRIME_COMPLETED
            PodStatus.BASAL_PROGRAM_SET ->
                ActivationProgress.PROGRAMMED_BASAL
            PodStatus.RUNNING_ABOVE_MIN_VOLUME, PodStatus.RUNNING_BELOW_MIN_VOLUME ->
                ActivationProgress.CANNULA_INSERTED
            else ->
                null
        }
        newActivationProgress?.let {
            podState.activationProgress = it
        }
        return null
    }

    @Synchronized
    override fun updateActiveCommand() = Maybe.create<CommandConfirmed> { source ->
        val activeCommand = podState.activeCommand
        if (activeCommand == null) {
            logger.error("No active command to update")
            source.onComplete()
            return@create
        }
        val cmdConfirmation = getCommandConfirmationFromState()
        logger.info(LTag.PUMPCOMM, "Update active command with confirmation: $cmdConfirmation")
        when (cmdConfirmation) {
            CommandSendingFailure -> {
                podState.activeCommand = null
                source.onError(
                    activeCommand.sendError
                        ?: java.lang.IllegalStateException(
                            "Could not send command and sendError is " +
                                "missing"
                        )
                )
            }

            CommandSendingNotConfirmed -> {
                // we did not receive a valid response yet
                source.onComplete()
            }

            CommandConfirmationDenied -> {
                podState.activeCommand = null
                source.onSuccess(CommandConfirmed(activeCommand, false))
            }

            CommandConfirmationSuccess -> {
                podState.activeCommand = null

                source.onSuccess(CommandConfirmed(activeCommand, true))
            }

            NoActiveCommand -> {
                source.onComplete()
            }
        }
    }

    override fun differentAlertSettings(
        expirationReminderEnabled: Boolean,
        expirationHours: Int,
        lowReservoirAlertEnabled: Boolean,
        lowReservoirAlertUnits: Int
    ): Boolean {
        return podState.expirationReminderEnabled == expirationReminderEnabled &&
            podState.expirationHours == expirationHours &&
            podState.lowReservoirAlertEnabled == lowReservoirAlertEnabled &&
            podState.lowReservoirAlertUnits == lowReservoirAlertUnits
    }

    override fun updateExpirationAlertSettings(expirationReminderEnabled: Boolean, expirationHours: Int):
        Completable = Completable.defer {
        podState.expirationReminderEnabled = expirationReminderEnabled
        podState.expirationHours = expirationHours
        Completable.complete()
    }

    override fun updateLowReservoirAlertSettings(lowReservoirAlertEnabled: Boolean, lowReservoirAlertUnits: Int):
        Completable = Completable.defer {
        podState.lowReservoirAlertEnabled = lowReservoirAlertEnabled
        podState.lowReservoirAlertUnits = lowReservoirAlertUnits
        Completable.complete()
    }

    @Synchronized
    override fun getCommandConfirmationFromState(): CommandConfirmationFromState {
        return podState.activeCommand?.run {
            logger.debug(
                "Getting command state with parameters: $activeCommand " +
                    "lastResponse=$lastStatusResponseReceived " +
                    "$sequenceNumberOfLastProgrammingCommand $historyId"
            )
            when {
                createdRealtime <= podState.lastStatusResponseReceived &&
                    sequence == podState.sequenceNumberOfLastProgrammingCommand ->
                    CommandConfirmationSuccess
                createdRealtime <= podState.lastStatusResponseReceived &&
                    sequence != podState.sequenceNumberOfLastProgrammingCommand ->
                    CommandConfirmationDenied
                // no response received after this point
                createdRealtime <= sentRealtime ->
                    CommandSendingNotConfirmed
                createdRealtime > sentRealtime ->
                    CommandSendingFailure
                else -> // this can't happen, see the previous two conditions
                    NoActiveCommand
            }
        } ?: NoActiveCommand
    }

    override fun increaseEapAkaSequenceNumber(): ByteArray {
        podState.eapAkaSequenceNumber++
        return EapSqn(podState.eapAkaSequenceNumber).value
    }

    override fun commitEapAkaSequenceNumber() {
        store()
    }

    override fun onStart() {
        when (getCommandConfirmationFromState()) {
            CommandConfirmationSuccess, CommandConfirmationDenied -> {
                val now = SystemClock.elapsedRealtime()
                val newCommand = podState.activeCommand?.copy(
                    createdRealtime = now,
                    sentRealtime = now + 1
                )
                podState.lastStatusResponseReceived = now + 2
                podState.activeCommand = newCommand
            }

            CommandSendingNotConfirmed -> {
                val now = SystemClock.elapsedRealtime()
                val newCommand = podState.activeCommand?.copy(
                    createdRealtime = now,
                    sentRealtime = now + 1
                )
                podState.activeCommand = newCommand
                podState.lastStatusResponseReceived = 0
            }

            CommandSendingFailure, NoActiveCommand ->
                podState.activeCommand = null
        }
    }

    override fun updateFromDefaultStatusResponse(response: DefaultStatusResponse) {
        logger.debug(LTag.PUMPCOMM, "Default status response :$response")
        podState.deliveryStatus = response.deliveryStatus
        podState.podStatus = response.podStatus
        podState.pulsesDelivered = response.totalPulsesDelivered
        if (response.reservoirPulsesRemaining < 1023) {
            podState.pulsesRemaining = response.reservoirPulsesRemaining
        }
        podState.sequenceNumberOfLastProgrammingCommand = response.sequenceNumberOfLastProgrammingCommand
        podState.minutesSinceActivation = response.minutesSinceActivation
        podState.activeAlerts = response.activeAlerts

        podState.lastUpdatedSystem = System.currentTimeMillis()
        podState.lastStatusResponseReceived = SystemClock.elapsedRealtime()
        updateLastBolusFromResponse(response.bolusPulsesRemaining)
        if (podState.activationTime == null) {
            podState.activationTime = System.currentTimeMillis() - (response.minutesSinceActivation * 60_000)
        }

        store()
        rxBus.send(EventOmnipodDashPumpValuesChanged())
    }

    override fun updateFromVersionResponse(response: VersionResponse) {
        podState.bleVersion = SoftwareVersion(
            response.bleVersionMajor,
            response.bleVersionMinor,
            response.bleVersionInterim
        )
        podState.firmwareVersion = SoftwareVersion(
            response.firmwareVersionMajor,
            response.firmwareVersionMinor,
            response.firmwareVersionInterim
        )
        podState.podStatus = response.podStatus
        podState.lotNumber = response.lotNumber
        podState.podSequenceNumber = response.podSequenceNumber

        podState.lastUpdatedSystem = System.currentTimeMillis()

        store()
        rxBus.send(EventOmnipodDashPumpValuesChanged())
    }

    override fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse) {
        podState.pulseRate = response.pumpRate
        podState.primePulseRate = response.primePumpRate
        podState.firstPrimeBolusVolume = response.numberOfEngagingClutchDrivePulses
        podState.secondPrimeBolusVolume = response.numberOfPrimePulses
        podState.podLifeInHours = response.podExpirationTimeInHours
        podState.bleVersion = SoftwareVersion(
            response.bleVersionMajor,
            response.bleVersionMinor,
            response.bleVersionInterim
        )
        podState.firmwareVersion = SoftwareVersion(
            response.firmwareVersionMajor,
            response.firmwareVersionMinor,
            response.firmwareVersionInterim
        )
        podState.podStatus = response.podStatus
        podState.lotNumber = response.lotNumber
        podState.podSequenceNumber = response.podSequenceNumber
        podState.uniqueId = response.uniqueIdReceivedInCommand

        podState.lastUpdatedSystem = System.currentTimeMillis()
        store()
        rxBus.send(EventOmnipodDashPumpValuesChanged())
    }

    override fun updateFromAlarmStatusResponse(response: AlarmStatusResponse) {
        logger.info(
            LTag.PUMP,
            "Received AlarmStatusReponse: $response"
        )
        podState.deliveryStatus = response.deliveryStatus
        podState.podStatus = response.podStatus
        podState.pulsesDelivered = response.totalPulsesDelivered

        if (response.reservoirPulsesRemaining < 1023) {
            podState.pulsesRemaining = response.reservoirPulsesRemaining
        }
        podState.sequenceNumberOfLastProgrammingCommand = response.sequenceNumberOfLastProgrammingCommand
        podState.minutesSinceActivation = response.minutesSinceActivation
        podState.activeAlerts = response.activeAlerts
        podState.alarmType = response.alarmType

        podState.lastUpdatedSystem = System.currentTimeMillis()
        podState.lastStatusResponseReceived = SystemClock.elapsedRealtime()
        updateLastBolusFromResponse(response.bolusPulsesRemaining)

        store()
        rxBus.send(EventOmnipodDashPumpValuesChanged())
    }

    override fun updateFromPairing(uniqueId: Id, pairResult: PairResult) {
        podState.eapAkaSequenceNumber = 1
        podState.ltk = pairResult.ltk
        podState.uniqueId = uniqueId.toLong()
    }

    override fun connectionSuccessRatio(): Float {
        val attempts = connectionAttempts
        if (attempts == 0) {
            return 1.0F
        }
        return successfulConnections.toFloat() * 100 / attempts.toFloat()
    }

    override fun reset() {
        podState = PodState()
        store()
    }

    private fun store() {
        try {
            val serialized = Gson().toJson(podState)
            logger.debug(LTag.PUMP, "Storing Pod state: $serialized")
            sharedPreferences.putString(R.string.key_omnipod_dash_pod_state, serialized)
        } catch (ex: Exception) {
            logger.error(LTag.PUMP, "Failed to store Pod state", ex)
        }
    }

    private fun load(): PodState {
        if (sharedPreferences.contains(R.string.key_omnipod_dash_pod_state)) {
            try {
                return Gson().fromJson(
                    sharedPreferences.getString(R.string.key_omnipod_dash_pod_state, ""),
                    PodState::class.java
                )
            } catch (ex: Exception) {
                logger.error(LTag.PUMP, "Failed to deserialize Pod state", ex)
            }
        }
        logger.debug(LTag.PUMP, "Creating new Pod state")
        return PodState()
    }

    class PodState : Serializable {

        var activationProgress: ActivationProgress = ActivationProgress.NOT_STARTED
        var lastUpdatedSystem: Long = 0
        var lastStatusResponseReceived: Long = 0
        var bluetoothConnectionState: OmnipodDashPodStateManager.BluetoothConnectionState =
            OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
        var connectionAttempts = 0
        var successfulConnections = 0
        var messageSequenceNumber: Short = 0
        var sequenceNumberOfLastProgrammingCommand: Short? = null
        var activationTime: Long? = null
        var uniqueId: Long? = null
        var bluetoothAddress: String? = null
        var ltk: ByteArray? = null
        var eapAkaSequenceNumber: Long = 1
        var bolusPulsesRemaining: Short = 0
        var timeZone: String = "" // TimeZone ID (e.g. "Europe/Amsterdam")

        var bleVersion: SoftwareVersion? = null
        var firmwareVersion: SoftwareVersion? = null
        var lotNumber: Long? = null
        var podSequenceNumber: Long? = null
        var pulseRate: Short? = null
        var primePulseRate: Short? = null
        var podLifeInHours: Short? = null
        var firstPrimeBolusVolume: Short? = null
        var secondPrimeBolusVolume: Short? = null

        var expirationReminderEnabled: Boolean? = null
        var expirationHours: Int? = null
        var lowReservoirAlertEnabled: Boolean? = null
        var lowReservoirAlertUnits: Int? = null

        var pulsesDelivered: Short? = null
        var pulsesRemaining: Short? = null
        var podStatus: PodStatus? = null
        var deliveryStatus: DeliveryStatus? = null
        var minutesSinceActivation: Short? = null
        var activeAlerts: EnumSet<AlertType>? = null
        var alarmType: AlarmType? = null

        var basalProgram: BasalProgram? = null
        var tempBasal: OmnipodDashPodStateManager.TempBasal? = null
        var activeCommand: OmnipodDashPodStateManager.ActiveCommand? = null
        var lastBolus: OmnipodDashPodStateManager.LastBolus? = null
    }
}
