package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state

import com.google.gson.Gson
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.DefaultStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.SetUniqueIdResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.VersionResponse
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.io.Serializable
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashPodStateManagerImpl @Inject constructor(
    private val logger: AAPSLogger,
    private val sharedPreferences: SP
) : OmnipodDashPodStateManager {

    private var podState: PodState

    init {
        podState = load()
    }

    override var activationProgress: ActivationProgress
        get() = podState.activationProgress
        set(value) {
            podState.activationProgress = value
            store()
        }

    // TODO: dynamic get() fun instead of assignment

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
        set(value) {
            podState.lastConnection = value
            store()
        }

    override val lastUpdated: Long
        get() = podState.lastUpdated

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

    override val activeAlerts: EnumSet<AlertSlot>?
        get() = podState.activeAlerts

    override val tempBasal: OmnipodDashPodStateManager.TempBasal?
        get() = podState.tempBasal

    override val tempBasalActive: Boolean
        get() = tempBasal != null && tempBasal!!.startTime + tempBasal!!.durationInMinutes * 60 * 1000 > System.currentTimeMillis()

    override val basalProgram: BasalProgram?
        get() = podState.basalProgram

    override fun increaseMessageSequenceNumber() {
        podState.messageSequenceNumber = ((podState.messageSequenceNumber.toInt() + 1) and 0x0f).toShort()
        store()
    }

    override fun updateFromDefaultStatusResponse(response: DefaultStatusResponse) {
        podState.deliveryStatus = response.deliveryStatus
        podState.podStatus = response.podStatus
        podState.pulsesDelivered = response.totalPulsesDelivered
        podState.pulsesRemaining = response.reservoirPulsesRemaining
        podState.sequenceNumberOfLastProgrammingCommand = response.sequenceNumberOfLastProgrammingCommand
        podState.minutesSinceActivation = response.minutesSinceActivation

        // TODO active alerts

        podState.lastUpdated = System.currentTimeMillis()
        store()
    }

    override fun updateFromVersionResponse(response: VersionResponse) {
        podState.bleVersion = SoftwareVersion(response.bleVersionMajor, response.bleVersionMinor, response.bleVersionInterim)
        podState.firmwareVersion = SoftwareVersion(response.firmwareVersionMajor, response.firmwareVersionMinor, response.firmwareVersionInterim)
        podState.podStatus = response.podStatus
        podState.lotNumber = response.lotNumber
        podState.podSequenceNumber = response.podSequenceNumber

        podState.lastUpdated = System.currentTimeMillis()
        store()
    }

    override fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse) {
        podState.pulseRate = response.pumpRate
        podState.primePulseRate = response.primePumpRate
        podState.firstPrimeBolusVolume = response.numberOfPrimePulses
        podState.secondPrimeBolusVolume = response.numberOfEngagingClutchDrivePulses
        podState.podLifeInHours = response.podExpirationTimeInHours
        podState.bleVersion = SoftwareVersion(response.bleVersionMajor, response.bleVersionMinor, response.bleVersionInterim)
        podState.firmwareVersion = SoftwareVersion(response.firmwareVersionMajor, response.firmwareVersionMinor, response.firmwareVersionInterim)
        podState.podStatus = response.podStatus
        podState.lotNumber = response.lotNumber
        podState.podSequenceNumber = response.podSequenceNumber
        podState.uniqueId = response.uniqueIdReceivedInCommand

        podState.lastUpdated = System.currentTimeMillis()
        store()
    }

    override fun updateFromAlarmStatusResponse(response: AlarmStatusResponse) {
        TODO("Not yet implemented")
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
                return Gson().fromJson(sharedPreferences.getString(R.string.key_omnipod_dash_pod_state, ""), PodState::class.java)
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
        var lastUpdated: Long = 0

        var messageSequenceNumber: Short = 0
        var sequenceNumberOfLastProgrammingCommand: Short? = null
        var activationTime: Long? = null
        var uniqueId: Long? = null
        var bluetoothAddress: String? = null

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
        var activeAlerts: EnumSet<AlertSlot>? = null

        var basalProgram: BasalProgram? = null
        var tempBasal: OmnipodDashPodStateManager.TempBasal? = null
    }
}