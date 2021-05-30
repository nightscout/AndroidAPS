package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair.PairResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.DefaultStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.SetUniqueIdResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.VersionResponse
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import java.io.Serializable
import java.util.*

interface OmnipodDashPodStateManager {

    var activationProgress: ActivationProgress
    val isUniqueIdSet: Boolean
    val isActivationCompleted: Boolean
    val isSuspended: Boolean
    val isPodRunning: Boolean
    var lastConnection: Long
    var bluetoothConnectionState: BluetoothConnectionState

    val lastUpdatedSystem: Long // System.currentTimeMillis()
    val lastStatusResponseReceived: Long

    val messageSequenceNumber: Short
    val sequenceNumberOfLastProgrammingCommand: Short?
    val activationTime: Long?
    var uniqueId: Long? // TODO make Int
    var bluetoothAddress: String?
    var ltk: ByteArray?
    var eapAkaSequenceNumber: Long

    val bluetoothVersion: SoftwareVersion?
    val firmwareVersion: SoftwareVersion?
    val lotNumber: Long?
    val podSequenceNumber: Long?
    val pulseRate: Short?
    val primePulseRate: Short?
    val podLifeInHours: Short?
    val firstPrimeBolusVolume: Short?
    val secondPrimeBolusVolume: Short?

    val pulsesDelivered: Short?
    val pulsesRemaining: Short?
    val podStatus: PodStatus?
    val deliveryStatus: DeliveryStatus?
    val minutesSinceActivation: Short?
    val activeAlerts: EnumSet<AlertType>?

    val tempBasal: TempBasal?
    val tempBasalActive: Boolean
    var basalProgram: BasalProgram?
    val activeCommand: ActiveCommand?

    fun increaseMessageSequenceNumber()
    fun increaseEapAkaSequenceNumber(): ByteArray
    fun commitEapAkaSequenceNumber()
    fun updateFromDefaultStatusResponse(response: DefaultStatusResponse)
    fun updateFromVersionResponse(response: VersionResponse)
    fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse)
    fun updateFromAlarmStatusResponse(response: AlarmStatusResponse)
    fun updateFromPairing(uniqueId: Id, pairResult: PairResult)
    fun reset()

    fun createActiveCommand(historyId: String): Single<ActiveCommand>
    fun updateActiveCommand(): Maybe<CommandConfirmed>
    fun observeNoActiveCommand(): Observable<PodEvent>
    fun maybeMarkActiveCommandFailed()

    data class ActiveCommand(
        val sequence: Short,
        val createdRealtime: Long,
        var sentRealtime: Long = 0,
        val historyId: String
    )
    // TODO: set created to "now" on boot
    data class TempBasal(val startTime: Long, val rate: Double, val durationInMinutes: Short) : Serializable

    enum class BluetoothConnectionState {
        CONNECTING, CONNECTED, DISCONNECTED
    }
}
