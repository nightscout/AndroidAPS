package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.SoftwareVersion
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.DefaultStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.SetUniqueIdResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.VersionResponse
import java.io.Serializable

interface OmnipodDashPodStateManager {

    var activationProgress: ActivationProgress
    var lastConnectionTime: Long

    val messageSequenceNumber: Short
    val activationTime: Long?
    val uniqueId: Int?
    val bluetoothAddress: String?

    val bluetoothVersion: SoftwareVersion?
    val firmwareVersion: SoftwareVersion?
    val lotNumber: Int?
    val podSequenceNumber: Int?
    val pulseRate: Int?
    val primePulseRate: Int?
    val podLifeInHours: Int?
    val firstPrimeBolusVolume: Int?
    val secondPrimeBolusVolume: Int?

    val pulsesDelivered: Int?
    val pulsesRemaining: Int?
    val podStatus: PodStatus?
    val deliveryStatus: DeliveryStatus?

    val tempBasal: TempBasal?
    val tempBasalActive: Boolean
    val basalProgram: BasalProgram?

    fun increaseMessageSequenceNumber()
    fun updateFromDefaultStatusResponse(response: DefaultStatusResponse)
    fun updateFromVersionResponse(response: VersionResponse)
    fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse)
    fun updateFromAlarmStatusResponse(response: AlarmStatusResponse)
    fun reset()

    data class TempBasal(val startTime: Long, val rate: Double, val durationInMinutes: Int) : Serializable
}