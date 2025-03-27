package app.aaps.pump.omnipod.dash.driver.pod.state

import android.os.SystemClock
import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.dash.EventOmnipodDashPumpValuesChanged
import app.aaps.pump.omnipod.dash.driver.comm.Id
import app.aaps.pump.omnipod.dash.driver.comm.pair.PairResult
import app.aaps.pump.omnipod.dash.driver.comm.session.EapSqn
import app.aaps.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlarmType
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertType
import app.aaps.pump.omnipod.dash.driver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodConstants
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.SoftwareVersion
import app.aaps.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.dash.driver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.dash.driver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.dash.driver.pod.response.VersionResponse
import app.aaps.pump.omnipod.dash.keys.DashStringNonPreferenceKey
import com.google.gson.Gson
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.EnumSet
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashPodStateManagerImpl @Inject constructor(
    private val logger: AAPSLogger,
    private val rxBus: RxBus,
    private val preferences: Preferences
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
        get() = podState.deliveryStatus?.equals(DeliveryStatus.SUSPENDED) == true

    override val isPodKaput: Boolean
        get() = podState.podStatus in arrayOf(PodStatus.ALARM, PodStatus.DEACTIVATED)

    override val isPodRunning: Boolean
        get() = podState.podStatus?.isRunning() == true

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

    override val successfulConnectionAttemptsAfterRetries: Int
        @Synchronized
        get() = podState.successfulConnectionAttemptsAfterRetries

    @Synchronized
    override fun incrementSuccessfulConnectionAttemptsAfterRetries() {
        podState.successfulConnectionAttemptsAfterRetries++
    }

    override val failedConnectionsAfterRetries: Int
        @Synchronized
        get() = podState.failedConnectionsAfterRetries

    override fun incrementFailedConnectionsAfterRetries() {
        podState.failedConnectionsAfterRetries++
    }

    override val timeZoneId: String?
        get() = podState.timeZone

    override val sameTimeZone: Boolean
        get() {
            val now = System.currentTimeMillis()
            val currentTimezone = TimeZone.getDefault()
            val currentOffset = currentTimezone.getOffset(now)
            val podOffset = podState.timeZoneOffset
            logger.debug(
                LTag.PUMPCOMM,
                "sameTimeZone " +
                    "currentOffset=$currentOffset " +
                    "podOffset=$podOffset"
            )
            return currentOffset == podOffset
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
        } == true

    override var basalProgram: BasalProgram?
        get() = podState.basalProgram
        set(basalProgram) {
            podState.basalProgram = basalProgram
            rxBus.send(EventOmnipodDashPumpValuesChanged())
            store()
        }

    override var suspendAlertsEnabled: Boolean
        get() = podState.suspendAlertsEnabled
        set(enabled) {
            podState.suspendAlertsEnabled = enabled
            store()
        }

    override val lastStatusResponseReceived: Long
        get() = podState.lastStatusResponseReceived

    override val time: ZonedDateTime?
        get() {
            val minutesSinceActivation = podState.minutesSinceActivation
            val activationTime = podState.activationTime
            val timeZoneOffset = podState.timeZoneOffset
            if ((activationTime != null) && (minutesSinceActivation != null) && (timeZoneOffset != null)) {
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(activationTime), ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(timeZoneOffset / 1000)))
                    .plusMinutes(minutesSinceActivation.toLong())
                    .plus(Duration.ofMillis(System.currentTimeMillis() - lastUpdatedSystem))
            }
            return null
        }

    override val timeDrift: Duration?
        get() {
            return time?.let {
                return Duration.between(ZonedDateTime.now(), it)
            }
        }

    override val timeZoneUpdated: Long?
        get() {
            return podState.timeZoneUpdated
        }

    override fun updateTimeZone() {
        val timeZone = TimeZone.getDefault()
        val now = System.currentTimeMillis()

        podState.timeZoneOffset = timeZone.getOffset(now)
        podState.timeZone = timeZone.id
        podState.timeZoneUpdated = now
    }

    override val expiry: ZonedDateTime?
        get() {
            val podLifeInHours = podLifeInHours
            val minutesSinceActivation = podState.minutesSinceActivation
            if (podLifeInHours != null && minutesSinceActivation != null) {
                return ZonedDateTime.now()
                    .plusHours(podLifeInHours.toLong())
                    .minusMinutes(minutesSinceActivation.toLong())
                    .minus(Duration.ofMillis(System.currentTimeMillis() - lastUpdatedSystem))
                    .minusHours(8)
            }
            return null
        }

    override var alarmSynced: Boolean
        get() = podState.alarmSynced
        set(value) {
            podState.alarmSynced = value
            store()
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
    override fun createLastBolus(requestedUnits: Double, historyId: Long, bolusType: BS.Type) {
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
            val remainingUnits = Round.roundTo(bolusPulsesRemaining * PodConstants.POD_PULSE_BOLUS_UNITS, PodConstants.POD_PULSE_BOLUS_UNITS)
            this.bolusUnitsRemaining = remainingUnits
            if (remainingUnits == 0.0) {
                this.deliveryComplete = true
            }
        }
    }

    @Synchronized
    override fun createActiveCommand(
        historyId: Long,
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
                logger.warn(LTag.PUMPCOMM, "Active command already existing: $activeCommand")
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
            PodStatus.FILLED                                                       ->
                ActivationProgress.NOT_STARTED

            PodStatus.UID_SET                                                      ->
                ActivationProgress.SET_UNIQUE_ID

            PodStatus.ENGAGING_CLUTCH_DRIVE, PodStatus.PRIMING                     ->
                return "Busy"

            PodStatus.CLUTCH_DRIVE_ENGAGED                                         ->
                ActivationProgress.PRIME_COMPLETED

            PodStatus.BASAL_PROGRAM_SET                                            ->
                ActivationProgress.PROGRAMMED_BASAL

            PodStatus.RUNNING_ABOVE_MIN_VOLUME, PodStatus.RUNNING_BELOW_MIN_VOLUME ->
                ActivationProgress.CANNULA_INSERTED

            else                                                                   ->
                null
        }
        newActivationProgress?.let {
            podState.activationProgress = it
        }
        return null
    }

    @Synchronized
    override fun updateActiveCommand(): Maybe<CommandConfirmed> = Maybe.create { source ->
        val activeCommand = podState.activeCommand
        if (activeCommand == null) {
            logger.error(LTag.PUMPCOMM, "No active command to update")
            source.onComplete()
            return@create
        }
        val cmdConfirmation = getCommandConfirmationFromState()
        logger.info(LTag.PUMPCOMM, "Update active command with confirmation: $cmdConfirmation")
        when (cmdConfirmation) {
            CommandSendingFailure      -> {
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

            CommandConfirmationDenied  -> {
                podState.activeCommand = null
                source.onSuccess(CommandConfirmed(activeCommand, false))
            }

            CommandConfirmationSuccess -> {
                podState.activeCommand = null

                source.onSuccess(CommandConfirmed(activeCommand, true))
            }

            NoActiveCommand            -> {
                source.onComplete()
            }
        }
    }

    override fun sameAlertSettings(
        expirationReminderEnabled: Boolean,
        expirationReminderHours: Int,
        expirationAlarmEnabled: Boolean,
        expirationAlarmHours: Int,
        lowReservoirAlertEnabled: Boolean,
        lowReservoirAlertUnits: Int
    ): Boolean {
        return podState.expirationReminderEnabled == expirationReminderEnabled &&
            podState.expirationReminderHours == expirationReminderHours &&
            podState.expirationAlarmEnabled == expirationAlarmEnabled &&
            podState.expirationAlarmHours == expirationAlarmHours &&
            podState.lowReservoirAlertEnabled == lowReservoirAlertEnabled &&
            podState.lowReservoirAlertUnits == lowReservoirAlertUnits
    }

    override fun updateExpirationAlertSettings(
        expirationReminderEnabled: Boolean,
        expirationReminderHours: Int,
        expirationAlarmEnabled: Boolean,
        expirationAlarmHours: Int
    ): Completable = Completable.defer {
        podState.expirationReminderEnabled = expirationReminderEnabled
        podState.expirationReminderHours = expirationReminderHours
        podState.expirationAlarmEnabled = expirationAlarmEnabled
        podState.expirationAlarmHours = expirationAlarmHours
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
                LTag.PUMPCOMM,
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
                createdRealtime <= sentRealtime                                 ->
                    CommandSendingNotConfirmed

                createdRealtime > sentRealtime                                  ->
                    CommandSendingFailure

                else                                                            -> // this can't happen, see the previous two conditions
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

            CommandSendingNotConfirmed                            -> {
                val now = SystemClock.elapsedRealtime()
                val newCommand = podState.activeCommand?.copy(
                    createdRealtime = now,
                    sentRealtime = now + 1
                )
                podState.activeCommand = newCommand
                podState.lastStatusResponseReceived = 0
            }

            CommandSendingFailure, NoActiveCommand                -> {
                podState.activeCommand = null
                podState.lastStatusResponseReceived = 0
            }
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
            LTag.PUMPCOMM,
            "Received AlarmStatusResponse: $response"
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
        if (failedConnectionsAfterRetries + successfulConnectionAttemptsAfterRetries == 0) {
            return 0.0F
        }
        return successfulConnectionAttemptsAfterRetries.toFloat() / (successfulConnectionAttemptsAfterRetries + failedConnectionsAfterRetries)
    }

    override fun reset() {
        podState = PodState()
        store()
    }

    private fun store() {
        try {
            val cleanPodState = podState.copy(ltk = byteArrayOf()) // do not log ltk
            logger.debug(LTag.PUMPCOMM, "Storing Pod state: ${Gson().toJson(cleanPodState)}")

            val serialized = Gson().toJson(podState)
            preferences.put(DashStringNonPreferenceKey.PodState, serialized)
        } catch (ex: Exception) {
            logger.error(LTag.PUMPCOMM, "Failed to store Pod state", ex)
        }
    }

    private fun load(): PodState {
        if (preferences.getIfExists(DashStringNonPreferenceKey.PodState) != null) {
            try {
                return Gson().fromJson(preferences.get(DashStringNonPreferenceKey.PodState), PodState::class.java)
            } catch (ex: Exception) {
                logger.error(LTag.PUMPCOMM, "Failed to deserialize Pod state", ex)
            }
        }
        return PodState()
    }

    data class PodState(
        var activationProgress: ActivationProgress = ActivationProgress.NOT_STARTED,
        var lastUpdatedSystem: Long = 0,
        var lastStatusResponseReceived: Long = 0,
        var bluetoothConnectionState: OmnipodDashPodStateManager.BluetoothConnectionState =
            OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED,
        var connectionAttempts: Int = 0,
        var successfulConnections: Int = 0,
        var successfulConnectionAttemptsAfterRetries: Int = 0,
        var failedConnectionsAfterRetries: Int = 0,
        var messageSequenceNumber: Short = 0,
        var sequenceNumberOfLastProgrammingCommand: Short? = null,
        var activationTime: Long? = null,
        var uniqueId: Long? = null,
        var bluetoothAddress: String? = null,
        var ltk: ByteArray? = null,
        var eapAkaSequenceNumber: Long = 1,
        var timeZone: String? = null, // TimeZone ID (e.g. "Europe/Amsterdam")
        var timeZoneOffset: Int? = null,
        var timeZoneUpdated: Long? = null,
        var alarmSynced: Boolean = false,
        var suspendAlertsEnabled: Boolean = false,

        var bleVersion: SoftwareVersion? = null,
        var firmwareVersion: SoftwareVersion? = null,
        var lotNumber: Long? = null,
        var podSequenceNumber: Long? = null,
        var pulseRate: Short? = null,
        var primePulseRate: Short? = null,
        var podLifeInHours: Short? = null,
        var firstPrimeBolusVolume: Short? = null,
        var secondPrimeBolusVolume: Short? = null,

        var expirationReminderEnabled: Boolean? = null,
        var expirationReminderHours: Int? = null,
        var expirationAlarmEnabled: Boolean? = null,
        var expirationAlarmHours: Int? = null,
        var lowReservoirAlertEnabled: Boolean? = null,
        var lowReservoirAlertUnits: Int? = null,

        var pulsesDelivered: Short? = null,
        var pulsesRemaining: Short? = null,
        var podStatus: PodStatus? = null,
        var deliveryStatus: DeliveryStatus? = null,
        var minutesSinceActivation: Short? = null,
        var activeAlerts: EnumSet<AlertType>? = null,
        var alarmType: AlarmType? = null,

        var basalProgram: BasalProgram? = null,
        var tempBasal: OmnipodDashPodStateManager.TempBasal? = null,
        var activeCommand: OmnipodDashPodStateManager.ActiveCommand? = null,
        var lastBolus: OmnipodDashPodStateManager.LastBolus? = null
    ) : Serializable
}
