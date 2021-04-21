package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state

import android.os.SystemClock
import com.google.gson.Gson
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.omnipod.dash.EventOmnipodDashPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair.PairResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session.EapSqn
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
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
            ?: true

    override val isPodRunning: Boolean
        get() = podState.podStatus?.isRunning() ?: false

    override var lastConnection: Long
        get() = podState.lastConnection
        set(lastConnection) {
            podState.lastConnection = lastConnection
            store()
        }

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

    override val tempBasal: OmnipodDashPodStateManager.TempBasal?
        get() = podState.tempBasal

    override val tempBasalActive: Boolean
        get() = tempBasal != null && tempBasal!!.startTime + tempBasal!!.durationInMinutes * 60 * 1000 > System.currentTimeMillis()

    override var basalProgram: BasalProgram?
        get() = podState.basalProgram
        set(basalProgram) {
            podState.basalProgram = basalProgram
            store()
        }

    override val lastStatusResponseReceived: Long
        get() = podState.lastStatusResponseReceived

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
    override fun createActiveCommand(historyId: String): Completable {
        return if (activeCommand == null) {
            podState.activeCommand = OmnipodDashPodStateManager.ActiveCommand(
                podState.messageSequenceNumber,
                createdRealtime = SystemClock.elapsedRealtime(),
                historyId = historyId
            )
            Completable.complete()
        } else {
            Completable.error(
                java.lang.IllegalStateException(
                    "Trying to send a command " +
                        "and the last command was not confirmed"
                )
            )
        }
    }

    @Synchronized
    override fun resetActiveCommand() {
        podState.activeCommand = null
    }

    @Synchronized
    override fun updateActiveCommand() = Maybe.create<PodEvent> { source ->
        podState.activeCommand?.run {
            logger.debug(
                "Trying to confirm active command with parameters: $activeCommand " +
                    "lastResponse=$lastStatusResponseReceived " +
                    "$sequenceNumberOfLastProgrammingCommand $historyId"
            )
            if (createdRealtime >= lastStatusResponseReceived)
                source.onComplete()
            else {
                podState.activeCommand = null
                if (sequenceNumberOfLastProgrammingCommand == sequence)
                    source.onSuccess(PodEvent.CommandConfirmed(historyId))
                else
                    source.onSuccess(PodEvent.CommandDenied(historyId))
            }
        }
            ?: source.onComplete() // no active programming command
    }

    override fun increaseEapAkaSequenceNumber(): ByteArray {
        podState.eapAkaSequenceNumber++
        return EapSqn(podState.eapAkaSequenceNumber).value
    }

    override fun commitEapAkaSequenceNumber() {
        store()
    }

    override fun updateFromDefaultStatusResponse(response: DefaultStatusResponse) {
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
        // TODO
        logger.error(
            LTag.PUMP,
            "Not implemented: OmnipodDashPodStateManagerImpl.updateFromAlarmStatusResponse(AlarmStatusResponse)"
        )

        store()
        rxBus.send(EventOmnipodDashPumpValuesChanged())
    }

    override fun updateFromPairing(uniqueId: Id, pairResult: PairResult) {
        podState.eapAkaSequenceNumber = 1
        podState.ltk = pairResult.ltk
        podState.uniqueId = uniqueId.toLong()
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
        var lastConnection: Long = 0
        var lastUpdatedSystem: Long = 0
        var lastStatusResponseReceived: Long = 0

        var messageSequenceNumber: Short = 0
        var sequenceNumberOfLastProgrammingCommand: Short? = null
        var activationTime: Long? = null
        var uniqueId: Long? = null
        var bluetoothAddress: String? = null
        var ltk: ByteArray? = null
        var eapAkaSequenceNumber: Long = 1

        var bleVersion: SoftwareVersion? = null
        var firmwareVersion: SoftwareVersion? = null
        var lotNumber: Long? = null
        var podSequenceNumber: Long? = null
        var pulseRate: Short? = null
        var primePulseRate: Short? = null
        var podLifeInHours: Short? = null
        var firstPrimeBolusVolume: Short? = null
        var secondPrimeBolusVolume: Short? = null

        var pulsesDelivered: Short? = null
        var pulsesRemaining: Short? = null
        var podStatus: PodStatus? = null
        var deliveryStatus: DeliveryStatus? = null
        var minutesSinceActivation: Short? = null
        var activeAlerts: EnumSet<AlertType>? = null

        var basalProgram: BasalProgram? = null
        var tempBasal: OmnipodDashPodStateManager.TempBasal? = null
        var activeCommand: OmnipodDashPodStateManager.ActiveCommand? = null
    }
}
