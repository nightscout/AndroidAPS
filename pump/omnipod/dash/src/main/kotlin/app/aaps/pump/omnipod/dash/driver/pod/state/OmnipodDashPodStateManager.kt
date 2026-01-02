package app.aaps.pump.omnipod.dash.driver.pod.state

import app.aaps.core.data.model.BS
import app.aaps.pump.omnipod.dash.driver.comm.Id
import app.aaps.pump.omnipod.dash.driver.comm.pair.PairResult
import app.aaps.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlarmType
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertType
import app.aaps.pump.omnipod.dash.driver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.SoftwareVersion
import app.aaps.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.dash.driver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.dash.driver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.dash.driver.pod.response.VersionResponse
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import java.io.Serializable
import java.time.ZonedDateTime
import java.util.EnumSet

sealed class CommandConfirmationFromState
object CommandSendingFailure : CommandConfirmationFromState()
object CommandSendingNotConfirmed : CommandConfirmationFromState()
object CommandConfirmationDenied : CommandConfirmationFromState()
object CommandConfirmationSuccess : CommandConfirmationFromState()
object NoActiveCommand : CommandConfirmationFromState()

interface OmnipodDashPodStateManager {

    var activationProgress: ActivationProgress
    val isUniqueIdSet: Boolean
    val isActivationCompleted: Boolean
    val isSuspended: Boolean
    val isPodRunning: Boolean
    val isPodKaput: Boolean
    var bluetoothConnectionState: BluetoothConnectionState
    var connectionAttempts: Int
    var successfulConnections: Int
    val successfulConnectionAttemptsAfterRetries: Int
    val failedConnectionsAfterRetries: Int

    val timeZoneId: String?
    val timeZoneUpdated: Long?
    val sameTimeZone: Boolean // The TimeZone is the same on the phone and on the pod
    val lastUpdatedSystem: Long // System.currentTimeMillis()
    val lastStatusResponseReceived: Long
    val time: ZonedDateTime?
    val timeDrift: java.time.Duration?
    val expiry: ZonedDateTime?
    var alarmSynced: Boolean

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
    val alarmType: AlarmType?

    var tempBasal: TempBasal?
    val tempBasalActive: Boolean
    var basalProgram: BasalProgram?
    val activeCommand: ActiveCommand?
    val lastBolus: LastBolus?
    var suspendAlertsEnabled: Boolean

    fun increaseMessageSequenceNumber()
    fun increaseEapAkaSequenceNumber(): ByteArray
    fun commitEapAkaSequenceNumber()
    fun updateFromDefaultStatusResponse(response: DefaultStatusResponse)
    fun updateFromVersionResponse(response: VersionResponse)
    fun updateFromSetUniqueIdResponse(response: SetUniqueIdResponse)
    fun updateFromAlarmStatusResponse(response: AlarmStatusResponse)
    fun updateFromPairing(uniqueId: Id, pairResult: PairResult)
    fun reset()
    fun connectionSuccessRatio(): Float
    fun incrementSuccessfulConnectionAttemptsAfterRetries()
    fun incrementFailedConnectionsAfterRetries()
    fun updateTimeZone()

    fun createActiveCommand(
        historyId: Long,
        basalProgram: BasalProgram? = null,
        tempBasal: TempBasal? = null,
        requestedBolus: Double? = null
    ): Single<ActiveCommand>

    fun updateActiveCommand(): Maybe<CommandConfirmed>
    fun observeNoActiveCommand(): Completable
    fun getCommandConfirmationFromState(): CommandConfirmationFromState

    fun createLastBolus(requestedUnits: Double, historyId: Long, bolusType: BS.Type)
    fun markLastBolusComplete(): LastBolus?
    fun onStart()

    /*
    This is called only:. It overwrites activationStatus
       - when activation was interrupted(application crash, killed, etc)
       - after getPodStatus was successful(we have an up-to-date podStatus)
     */
    fun recoverActivationFromPodStatus(): String?
    fun sameAlertSettings(
        expirationReminderEnabled: Boolean,
        expirationReminderHours: Int,
        expirationAlarmEnabled: Boolean,
        expirationAlarmHours: Int,
        lowReservoirAlertEnabled: Boolean,
        lowReservoirAlertUnits: Int
    ): Boolean

    fun updateExpirationAlertSettings(
        expirationReminderEnabled: Boolean,
        expirationReminderHours: Int,
        expirationAlarmEnabled: Boolean,
        expirationAlarmHours: Int
    ): Completable

    fun updateLowReservoirAlertSettings(lowReservoirAlertEnabled: Boolean, lowReservoirAlertUnits: Int): Completable

    data class ActiveCommand(
        val sequence: Short,
        val createdRealtime: Long,
        var sentRealtime: Long = 0,
        val historyId: Long,
        var sendError: Throwable?,
        var basalProgram: BasalProgram?,
        val tempBasal: TempBasal?,
        val requestedBolus: Double?
    )

    // TODO: set created to "now" on boot
    data class TempBasal(val startTime: Long, val rate: Double, val durationInMinutes: Short) : Serializable

    data class LastBolus(
        val startTime: Long,
        val requestedUnits: Double,
        var bolusUnitsRemaining: Double,
        var deliveryComplete: Boolean,
        val historyId: Long,
        val bolusType: BS.Type
    ) {

        fun deliveredUnits(): Double? {
            return if (deliveryComplete) {
                requestedUnits - bolusUnitsRemaining
            } else {
                null
            }
        }

        fun markComplete(): Double {
            this.deliveryComplete = true
            return requestedUnits - bolusUnitsRemaining
        }
    }

    enum class BluetoothConnectionState {
        CONNECTING, CONNECTED, DISCONNECTED
    }
}
