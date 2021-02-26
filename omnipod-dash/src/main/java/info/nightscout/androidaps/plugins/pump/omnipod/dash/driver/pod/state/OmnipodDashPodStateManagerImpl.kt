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

    override var lastConnection: Long
        get() = podState.lastConnection
        set(value) {
            podState.lastConnection = value
            store()
        }

    override val lastUpdated: Long = podState.lastUpdated

    override val messageSequenceNumber: Short = podState.messageSequenceNumber

    override val sequenceNumberOfLastProgrammingCommand: Short? = podState.sequenceNumberOfLastProgrammingCommand

    override val activationTime: Long? = podState.activationTime

    override val uniqueId: Long? = podState.uniqueId

    override val bluetoothAddress: String? = podState.bluetoothAddress

    override val bluetoothVersion: SoftwareVersion? = podState.bleVersion

    override val firmwareVersion: SoftwareVersion? = podState.firmwareVersion

    override val lotNumber: Long? = podState.lotNumber

    override val podSequenceNumber: Long? = podState.podSequenceNumber

    override val pulseRate: Short? = podState.pulseRate

    override val primePulseRate: Short? = podState.primePulseRate

    override val podLifeInHours: Short? = podState.podLifeInHours

    override val firstPrimeBolusVolume: Short? = podState.firstPrimeBolusVolume

    override val secondPrimeBolusVolume: Short? = podState.secondPrimeBolusVolume

    override val pulsesDelivered: Short? = podState.pulsesDelivered

    override val pulsesRemaining: Short? = podState.pulsesRemaining

    override val podStatus: PodStatus? = podState.podStatus

    override val deliveryStatus: DeliveryStatus? = podState.deliveryStatus

    override val minutesSinceActivation: Short? = podState.minutesSinceActivation

    override val activeAlerts: EnumSet<AlertSlot>? = podState.activeAlerts

    override val tempBasal: OmnipodDashPodStateManager.TempBasal? = podState.tempBasal

    override val tempBasalActive: Boolean
        get() = tempBasal != null && tempBasal.startTime + tempBasal.durationInMinutes * 60 * 1000 > System.currentTimeMillis()

    override val basalProgram: BasalProgram? = podState.basalProgram

    override fun increaseMessageSequenceNumber() {
        podState.messageSequenceNumber = ((podState.messageSequenceNumber.toInt() + 1) and 0x0f).toShort()
        store()
    }

    override fun updateFromDefaultStatusResponse(response: DefaultStatusResponse) {
        podState.deliveryStatus = response.getDeliveryStatus()
        podState.podStatus = response.getPodStatus()
        podState.pulsesDelivered = response.getTotalPulsesDelivered()
        podState.pulsesRemaining = response.getReservoirPulsesRemaining()
        podState.sequenceNumberOfLastProgrammingCommand = response.getSequenceNumberOfLastProgrammingCommand()
        podState.minutesSinceActivation = response.getMinutesSinceActivation()

        // TODO active alerts

        podState.lastUpdated = System.currentTimeMillis()
        store();
    }

    override fun updateFromVersionResponse(response: VersionResponse) {
        podState.bleVersion = SoftwareVersion(response.getBleVersionMajor(), response.getBleVersionMinor(), response.getBleVersionInterim())
        podState.firmwareVersion = SoftwareVersion(response.getFirmwareVersionMajor(), response.getFirmwareVersionMinor(), response.getFirmwareVersionInterim())
        podState.podStatus = response.getPodStatus()
        podState.lotNumber = response.getLotNumber()
        podState.podSequenceNumber = response.getPodSequenceNumber()

        podState.lastUpdated = System.currentTimeMillis()
        store()
    }

    override fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse) {
        podState.pulseRate = response.getDeliveryRate()
        podState.primePulseRate = response.getPrimeRate()
        podState.firstPrimeBolusVolume = response.getNumberOfPrimePulses()
        podState.secondPrimeBolusVolume = response.getNumberOfEngagingClutchDrivePulses()
        podState.podLifeInHours = response.getPodExpirationTimeInHours()
        podState.bleVersion = SoftwareVersion(response.getBleVersionMajor(), response.getBleVersionMinor(), response.getBleVersionInterim())
        podState.firmwareVersion = SoftwareVersion(response.getFirmwareVersionMajor(), response.getFirmwareVersionMinor(), response.getFirmwareVersionInterim())
        podState.podStatus = response.getPodStatus()
        podState.lotNumber = response.getLotNumber()
        podState.podSequenceNumber = response.getPodSequenceNumber()
        podState.uniqueId = response.getUniqueIdReceivedInCommand()

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