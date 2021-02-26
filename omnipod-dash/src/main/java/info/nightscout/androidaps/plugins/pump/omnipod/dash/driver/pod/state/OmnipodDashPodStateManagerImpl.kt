package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state

import com.google.gson.Gson
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.SoftwareVersion
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.DefaultStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.SetUniqueIdResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.VersionResponse
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.io.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashPodStateManagerImpl @Inject constructor(
    private val resourceHelper: ResourceHelper,
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

    override var lastConnectionTime: Long
        get() = podState.lastConnectionTime
        set(value) {
            podState.lastConnectionTime = value
            store()
        }

    override val messageSequenceNumber: Short = podState.messageSequenceNumber

    override val activationTime: Long? = podState.activationTime

    override val uniqueId: Int? = podState.uniqueId

    override val bluetoothAddress: String? = podState.bluetoothAddress

    override val bluetoothVersion: SoftwareVersion? = podState.bluetoothVersion

    override val firmwareVersion: SoftwareVersion? = podState.firmwareVersion

    override val lotNumber: Int? = podState.lotNumber

    override val podSequenceNumber: Int? = podState.podSequenceNumber

    override val pulseRate: Int? = podState.pulseRate

    override val primePulseRate: Int? = podState.primePulseRate

    override val podLifeInHours: Int? = podState.podLifeInHours

    override val firstPrimeBolusVolume: Int? = podState.firstPrimeBolusVolume

    override val secondPrimeBolusVolume: Int? = podState.secondPrimeBolusVolume

    override val pulsesDelivered: Int? = podState.pulsesDelivered

    override val pulsesRemaining: Int? = podState.pulsesRemaining

    override val podStatus: PodStatus? = podState.podStatus

    override val deliveryStatus: DeliveryStatus? = podState.deliveryStatus

    override val tempBasal: OmnipodDashPodStateManager.TempBasal? = podState.tempBasal

    override val tempBasalActive: Boolean
        get() = tempBasal != null && tempBasal.startTime + tempBasal.durationInMinutes * 60 * 1000 > System.currentTimeMillis()

    override val basalProgram: BasalProgram? = podState.basalProgram

    override fun increaseMessageSequenceNumber() {
        podState.messageSequenceNumber = ((podState.messageSequenceNumber.toInt() + 1) and 0x0f).toShort()
        store()
    }

    override fun updateFromDefaultStatusResponse(response: DefaultStatusResponse) {
        TODO("Not yet implemented")
    }

    override fun updateFromVersionResponse(response: VersionResponse) {
        TODO("Not yet implemented")
    }

    override fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse) {
        response.getBleVersionInterim()
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
        var lastConnectionTime: Long = 0

        var messageSequenceNumber: Short = 0
        var activationTime: Long? = null
        var uniqueId: Int? = null
        var bluetoothAddress: String? = null

        var bluetoothVersion: SoftwareVersion? = null
        var firmwareVersion: SoftwareVersion? = null
        var lotNumber: Int? = null
        var podSequenceNumber: Int? = null
        var pulseRate: Int? = null
        var primePulseRate: Int? = null
        var podLifeInHours: Int? = null
        var firstPrimeBolusVolume: Int? = null
        var secondPrimeBolusVolume: Int? = null

        var pulsesDelivered: Int? = null
        var pulsesRemaining: Int? = null
        var podStatus: PodStatus? = null
        var deliveryStatus: DeliveryStatus? = null

        var basalProgram: BasalProgram? = null
        var tempBasal: OmnipodDashPodStateManager.TempBasal? = null
    }
}