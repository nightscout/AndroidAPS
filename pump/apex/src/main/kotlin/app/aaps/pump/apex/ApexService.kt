package app.aaps.pump.apex

import app.aaps.pump.apex.interfaces.ApexBluetoothCallback
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.Preferences
import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.waitMillis
import app.aaps.pump.apex.connectivity.ApexBluetooth
import app.aaps.pump.apex.connectivity.ProtocolVersion
import app.aaps.pump.apex.connectivity.commands.device.Bolus
import app.aaps.pump.apex.connectivity.commands.device.CancelBolus
import app.aaps.pump.apex.connectivity.commands.device.CancelTemporaryBasal
import app.aaps.pump.apex.connectivity.commands.device.DeviceCommand
import app.aaps.pump.apex.connectivity.commands.device.ExtendedBolus
import app.aaps.pump.apex.connectivity.commands.device.GetValue
import app.aaps.pump.apex.connectivity.commands.device.NotifyAboutConnection
import app.aaps.pump.apex.connectivity.commands.device.SyncDateTime
import app.aaps.pump.apex.connectivity.commands.device.TemporaryBasal
import app.aaps.pump.apex.connectivity.commands.device.UpdateBasalProfileRates
import app.aaps.pump.apex.connectivity.commands.device.UpdateSystemState
import app.aaps.pump.apex.connectivity.commands.device.UpdateUsedBasalProfile
import app.aaps.pump.apex.connectivity.commands.pump.Alarm
import app.aaps.pump.apex.connectivity.commands.pump.AlarmLength
import app.aaps.pump.apex.connectivity.commands.pump.AlarmObject
import app.aaps.pump.apex.connectivity.commands.pump.BasalProfile
import app.aaps.pump.apex.connectivity.commands.pump.BolusEntry
import app.aaps.pump.apex.connectivity.commands.pump.CommandResponse
import app.aaps.pump.apex.connectivity.commands.pump.Heartbeat
import app.aaps.pump.apex.connectivity.commands.pump.PumpCommand
import app.aaps.pump.apex.connectivity.commands.pump.PumpObject
import app.aaps.pump.apex.connectivity.commands.pump.PumpObjectModel
import app.aaps.pump.apex.connectivity.commands.pump.StatusV1
import app.aaps.pump.apex.connectivity.commands.pump.TDDEntry
import app.aaps.pump.apex.connectivity.commands.pump.Version
import app.aaps.pump.apex.events.EventApexPumpDataChanged
import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.keys.ApexBooleanKey
import app.aaps.pump.apex.utils.keys.ApexDoubleKey
import app.aaps.pump.apex.utils.keys.ApexStringKey
import dagger.android.DaggerService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import org.joda.time.DateTime
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import kotlin.concurrent.schedule
import kotlin.math.abs

/**
 * @author Roman Rikhter (teledurak@gmail.com)
 */
class ApexService: DaggerService(), ApexBluetoothCallback {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var apexBluetooth: ApexBluetooth
    @Inject lateinit var apexDeviceInfo: ApexDeviceInfo
    @Inject lateinit var apexPumpPlugin: ApexPumpPlugin
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var pump: ApexPump

    companion object {
        const val USED_BASAL_PATTERN_INDEX = 7
        val FIRST_SUPPORTED_PROTO = ProtocolVersion.PROTO_4_10
        val LAST_SUPPORTED_PROTO = ProtocolVersion.PROTO_4_10
    }

    private data class InCommandResponse(
        var response: CommandResponse? = null,
        var waiting: Boolean = false,
    ) {
        fun clear(notify: Boolean = false) {
            response = null
            waiting = true
            if (notify) this.notifyAll()
        }
    }

    private data class InGetValueResult(
        var isSingleObject: Boolean = false,
        var targetObject: PumpObject? = null,
        var waiting: Boolean = false,
        var response: ArrayList<PumpObjectModel>? = null,
    ) {
        fun add(data: PumpObjectModel) {
            if (response == null) response = arrayListOf()
            response!!.add(data)
        }

        fun clear(notify: Boolean = false) {
            response = null
            targetObject = null
            isSingleObject = false
            waiting = true
            if (notify) this.notifyAll()
        }
    }

    private val commandLock = Mutex()
    private val disposable = CompositeDisposable()

    private val getValueResult = InGetValueResult()
    private val commandResponse = InCommandResponse()

    private var timer = Timer("ApexService-timer")

    private var getValueLastTaskTimestamp: Long = 0
    private var unreachableTimerTask: TimerTask? = null

    private var waitingForCurrentBolusInHistory = false

    private var _bolusCompletable: CompletableDeferred<ApexPump.InProgressBolus?>? = null
    private var statusGetValue: GetValue.Value = GetValue.Value.StatusV1

    private var lastBolusDateTime = DateTime(0)
    private var lastConnectedTimestamp = System.currentTimeMillis()

    val lastConnected: Long
        get() = if (connectionStatus != ApexBluetooth.Status.CONNECTED) {
            lastConnectedTimestamp
        } else System.currentTimeMillis()

    fun getValue(value: GetValue.Value): List<PumpObjectModel>? = synchronized(commandLock) { synchronized(getValueResult) {
            if (connectionStatus != ApexBluetooth.Status.CONNECTED) return null
            aapsLogger.debug(LTag.PUMPCOMM, "Executing GetValue(${value.name})")

            getValueResult.clear()
            getValueResult.targetObject = when (value) {
                GetValue.Value.StatusV1 -> PumpObject.StatusV1
                GetValue.Value.TDDs -> PumpObject.TDDEntry
                GetValue.Value.Alarms -> PumpObject.AlarmEntry
                GetValue.Value.BasalProfiles -> PumpObject.BasalProfile
                GetValue.Value.Version -> PumpObject.FirmwareEntry
                GetValue.Value.BolusHistory, GetValue.Value.LatestBoluses -> PumpObject.BolusEntry
                GetValue.Value.LatestTemporaryBasals, GetValue.Value.StatusV2 -> return null // TODO 4.11 bring up
                GetValue.Value.WizardStatus -> return null
            }
            getValueResult.isSingleObject = when (value) {
                GetValue.Value.StatusV1, GetValue.Value.StatusV2, GetValue.Value.Version -> true
                else -> false
            }

            apexBluetooth.send(GetValue(apexDeviceInfo, value))
            try {
                aapsLogger.debug(LTag.PUMPCOMM, "${value.name} | Waiting for response")
                getValueResult.waitMillis(if (getValueResult.isSingleObject) 5000 else 60000)
            } catch (e: InterruptedException) {
                aapsLogger.error(LTag.PUMPCOMM, "getValue InterruptedException", e)
            }

            getValueResult.response
        }}

    private fun executeWithResponse(command: DeviceCommand): CommandResponse? = synchronized(commandLock) { synchronized(commandResponse) {
            if (connectionStatus != ApexBluetooth.Status.CONNECTED) return null

            aapsLogger.debug(LTag.PUMPCOMM, "Executing $command")
            commandResponse.clear()

            apexBluetooth.send(command)
            try {
                aapsLogger.debug(LTag.PUMPCOMM, "$command | Waiting for response")
                commandResponse.waitMillis(5000)
            } catch (e: InterruptedException) {
                aapsLogger.error(LTag.PUMPCOMM, "executeWithResponse InterruptedException", e)
            }

            commandResponse.response
        }}

    override fun onCreate() {
        super.onCreate()
        aapsLogger.debug(LTag.PUMP, "Service created")
        apexBluetooth.setCallback(this)

        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           if (it.isChanged(ApexStringKey.SerialNumber.key)) {
                               onSerialChanged()
                           } else if (it.isChanged(ApexDoubleKey.MaxBolus.key) || it.isChanged(ApexDoubleKey.MaxBasal.key) || it.isChanged(ApexStringKey.AlarmSoundLength.key) && apexBluetooth.status == ApexBluetooth.Status.CONNECTED) {
                               updateSettings("ApexService-PreferencesListener")
                           }
                       }, fabricPrivacy::logException)

        pump.serialNumber = apexDeviceInfo.serialNumber
    }

    override fun onDestroy() {
        aapsLogger.debug(LTag.PUMP, "Service destroyed")
        disposable.clear()
        disconnect()
        super.onDestroy()
    }

    private fun onSerialChanged() {
        pump.serialNumber = apexDeviceInfo.serialNumber
        if (apexBluetooth.status != ApexBluetooth.Status.DISCONNECTED) disconnect()
        startConnection()
    }

    //////// Public methods

    fun syncDateTime(caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "syncDateTime - $caller")
        val response = executeWithResponse(SyncDateTime(apexDeviceInfo, DateTime.now()))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[syncDateTime caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to sync time: ${response.code.name}")
            return false
        }

        return true
    }

    fun notifyAboutConnection(caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "notifyAboutConnection - $caller")
        val response = executeWithResponse(NotifyAboutConnection(apexDeviceInfo))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[notifyAboutConnection caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to notify about connection: ${response.code.name}")
            return false
        }

        return true
    }

    fun bolus(dbi: DetailedBolusInfo, caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "bolus - $caller")
        if (dbi.insulin > pump.maxBolus) {
            aapsLogger.error(LTag.PUMP, "[bolus caller=$caller] Requested ${dbi.insulin}U is greater than maximum set ${pump.maxBolus}")
            return false
        }

        val doseRaw = (dbi.insulin / 0.025).toInt()
        if (dbi.insulin % 0.025 > 0.001) aapsLogger.warn(LTag.PUMPCOMM, "[bolus caller=$caller] Bolus dose is not aligned to 0.025U steps! Rounding down.")

        val response = executeWithResponse(Bolus(apexDeviceInfo, doseRaw))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[bolus caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to begin bolus: ${response.code.name}")
            return false
        }

        val syncResult = pumpSync.addBolusWithTempId(
            timestamp = dbi.timestamp,
            amount = dbi.insulin,
            temporaryId = dbi.timestamp,
            type = dbi.bolusType,
            pumpSerial = apexDeviceInfo.serialNumber,
            pumpType = PumpType.APEX_TRUCARE_III,
        )
        aapsLogger.debug(LTag.PUMP, "Initial bolus [${dbi.insulin}U] sync succeeded? $syncResult")

        pump.inProgressBolus = ApexPump.InProgressBolus(
            requestedDose = dbi.insulin,
            temporaryId = dbi.timestamp,
            detailedBolusInfo = dbi,
            treatment = EventOverviewBolusProgress.Treatment(
                insulin = dbi.insulin,
                carbs = dbi.carbs.toInt(),
                isSMB = dbi.bolusType == BS.Type.SMB,
                id = dbi.id
            )
        )

        _bolusCompletable = CompletableDeferred()
        return true
    }

    fun extendedBolus(dose: Double, durationMinutes: Int, caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "extendedBolus - $caller")
        val doseRaw = (dose / 0.025).toInt()
        if (dose % 0.025 > 0.001) aapsLogger.warn(LTag.PUMPCOMM, "[extendedBolus caller=$caller] Bolus dose is not aligned to 0.025U steps! Rounded down.")

        val durationRaw = durationMinutes / 15
        if (durationMinutes % 15 > 0) aapsLogger.warn(LTag.PUMPCOMM, "[extendedBolus caller=$caller] Bolus duration is not aligned to 15 minute steps! Rounded down.")

        val response = executeWithResponse(ExtendedBolus(apexDeviceInfo, doseRaw, durationRaw))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[extendedBolus caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to begin extended bolus: ${response.code.name}")
            return false
        }

        return true
    }

    fun temporaryBasal(dose: Double, durationMinutes: Int, type: PumpSync.TemporaryBasalType? = null, caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "temporaryBasal - $caller")
        if (dose > pump.maxBasal) {
            aapsLogger.error(LTag.PUMP, "[temporaryBasal caller=$caller] Requested ${dose}U is greater than maximum set ${pump.maxBasal}U")
            return false
        }

        val doseRaw = (dose / 0.025).toInt()
        if (dose % 0.025 > 0.001) aapsLogger.warn(LTag.PUMPCOMM, "[extendedBolus caller=$caller] Bolus dose is not aligned to 0.025U steps! Rounded down.")

        val durationRaw = durationMinutes / 15
        if (durationMinutes % 15 > 0) aapsLogger.warn(LTag.PUMPCOMM, "[extendedBolus caller=$caller] Bolus duration is not aligned to 15 minute steps! Rounded down.")

        val response = executeWithResponse(TemporaryBasal(apexDeviceInfo, true, doseRaw, durationRaw))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[extendedBolus caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to begin extended bolus: ${response.code.name}")
            return false
        }

        val id = System.currentTimeMillis()
        pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = id,
            pumpId = id,
            pumpType = PumpType.APEX_TRUCARE_III,
            pumpSerial = apexDeviceInfo.serialNumber,
            rate = dose,
            duration = durationMinutes.toLong() * 60 * 1000,
            isAbsolute = true,
            type = type,
        )

        aapsLogger.debug(LTag.PUMP, "Started TBR ${dose}U for ${durationMinutes}min by $caller")
        return true
    }

    fun cancelBolus(caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "cancelBolus - $caller")
        val response = executeWithResponse(CancelBolus(apexDeviceInfo))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[cancelBolus caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to cancel bolus: ${response.code.name}")
            return false
        }

        onBolusFailed(true)
        return true
    }

    fun cancelTemporaryBasal(caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "cancelTemporaryBasal - $caller")
        val response = executeWithResponse(CancelTemporaryBasal(apexDeviceInfo))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[cancelTemporaryBasal caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to cancel temporary basal: ${response.code.name}")
            return false
        }

        val stop = System.currentTimeMillis()
        pumpSync.syncStopTemporaryBasalWithPumpId(
            timestamp = stop,
            endPumpId = stop,
            pumpType = PumpType.APEX_TRUCARE_III,
            pumpSerial = apexDeviceInfo.serialNumber,
        )

        return true
    }

    fun updateSettings(caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "updateSettings - $caller")
        val response = executeWithResponse(
            if (pump.isV1)
                pump.lastV1!!.toUpdateSettingsV1(
                    apexDeviceInfo,
                    AlarmLength.valueOf(preferences.get(ApexStringKey.AlarmSoundLength)),
                    maxSingleBolus = (preferences.get(ApexDoubleKey.MaxBolus) / 0.025).toInt(),
                    maxBasalRate = (preferences.get(ApexDoubleKey.MaxBasal) / 0.025).toInt(),
                    enableAdvancedBolus = false,
                )
            else
                return false
        )
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[updateSettings caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to update settings: ${response.code.name}")
            return false
        }

        return true
    }

    fun updateSystemState(suspend: Boolean, caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "updateSystemState - $caller")
        val response = executeWithResponse(UpdateSystemState(apexDeviceInfo, suspend))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[updateSystemState caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to update system state: ${response.code.name}")
            return false
        }

        return true
    }

    fun updateBasalPatternIndex(id: Int, caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "updateBasalPatternIndex - $caller")
        val response = executeWithResponse(UpdateUsedBasalProfile(apexDeviceInfo, id))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[updateBasalPatternIndex caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to update basal pattern index: ${response.code.name}")
            return false
        }

        return true
    }

    fun updateCurrentBasalPattern(doses: List<Double>, caller: String): Boolean {
        require(doses.size == 48)

        aapsLogger.debug(LTag.PUMPCOMM, "updateCurrentBasalPattern - $caller")

        val response = executeWithResponse(UpdateBasalProfileRates(
            apexDeviceInfo,
            doses.map { (it / 0.025).toInt() }
        ))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[updateBasalPatternIndex caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to update basal pattern index: ${response.code.name}")
            return false
        }

        return true
    }

    fun getTDDs(caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "getTDDs - $caller")
        val response = getValue(GetValue.Value.TDDs)
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[getTDDs caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        return true
    }

    fun getBoluses(caller: String, isFullHistory: Boolean = false): Boolean {
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_boluses)))

        aapsLogger.debug(LTag.PUMPCOMM, "getBoluses - $caller")
        val response = getValue(if (isFullHistory) GetValue.Value.BolusHistory else GetValue.Value.LatestBoluses)
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[getBoluses full=$isFullHistory caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        return true
    }

    fun getStatus(caller: String): Boolean {
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_pump_status)))
        aapsLogger.debug(LTag.PUMPCOMM, "getStatus - $caller")
        val response = getValue(statusGetValue)
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[getStatus caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        return true
    }

    fun getBasalProfiles(caller: String): Map<Int, List<Double>>? {
        val ret = mutableMapOf<Int, List<Double>>()

        aapsLogger.debug(LTag.PUMPCOMM, "getBasalProfiles - $caller")

        val response = getValue(GetValue.Value.BasalProfiles)
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[getBasalProfiles caller=$caller] Timed out while trying to communicate with the pump")
            return null
        }

        for (i in response) {
            require(i is BasalProfile)
            ret[i.index] = i.rates.map { it * 0.025 }
        }

        return ret
    }

    //////// Public values

    val connectionStatus: ApexBluetooth.Status
        get() = apexBluetooth.status

    val bolusCompletable: CompletableDeferred<ApexPump.InProgressBolus?>?
        get() = _bolusCompletable

    //////// Pump commands handlers

    private fun onBolusProgress(dose: Double) {
        aapsLogger.debug(LTag.PUMPCOMM, "bolus progress $dose")
        pump.inProgressBolus?.currentDose = dose

        val bolus = pump.inProgressBolus ?: return

        if (bolus.detailedBolusInfo.bolusType == BS.Type.SMB) {
            rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.ui.R.string.smb_bolus_u, bolus.requestedDose)))
        } else {
            rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.ui.R.string.bolus_u_min, bolus.requestedDose)))
        }

        rxBus.send(EventOverviewBolusProgress.apply {
            t = bolus.treatment
            percent = (bolus.currentDose / bolus.requestedDose * 100).toInt()
            status = rh.gs(R.string.status_delivering, dose)
        })
    }

    private fun onBolusCompleted(dose: Double) {
        aapsLogger.debug(LTag.PUMPCOMM, "bolus completed")
        if (pump.inProgressBolus == null) return
        pump.inProgressBolus!!.currentDose = dose

        rxBus.send(EventOverviewBolusProgress.apply {
            percent = 100
            status = rh.gs(R.string.status_delivered, dose)
        })

        // Request new bolus history to fixup bolus ID.
        getBoluses("ApexService-onBolusCompleted")
    }

    private fun onBolusFailed(cancelled: Boolean = false) {
        aapsLogger.debug(LTag.PUMPCOMM, "bolus failed (cancelled? $cancelled)")
        if (pump.inProgressBolus == null) return

        if (cancelled) {
            pump.inProgressBolus!!.cancelled = true
            rxBus.send(EventOverviewBolusProgress.apply {
                status = rh.gs(R.string.status_bolus_cancelled)
            })
        }

        if (pump.inProgressBolus!!.currentDose >= 0.025) {
            // Request new bolus history to fixup bolus ID and delivered amount.
            getBoluses("ApexService-onBolusCompleted")
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "bolus entirely failed!")
            // TODO: how to handle fully failed boluses?
            pump.inProgressBolus = null
            _bolusCompletable?.complete(null)
        }
    }

    private fun onCommandResponse(response: CommandResponse) {
        aapsLogger.debug(LTag.PUMPCOMM, "got command response - ${response.code.name} / ${response.dose}")
        when (response.code) {
            CommandResponse.Code.Accepted, CommandResponse.Code.Invalid -> {
                if (!commandResponse.waiting) return
                commandResponse.response = response
                commandResponse.waiting = false
                synchronized(commandResponse) {
                    commandResponse.notifyAll()
                }
            }
            CommandResponse.Code.StandardBolusProgress -> onBolusProgress(response.dose * 0.025)
            CommandResponse.Code.ExtendedBolusProgress -> return
            CommandResponse.Code.Completed             -> onBolusCompleted(response.dose * 0.025)
            else                                       -> return
        }
    }

    private fun onAlarmsChanged(update: ApexPump.StatusUpdate) {
        // Alarm was dismissed
        if (pump.isAlarmPresent && update.current.alarms.isEmpty()) {
            pump.isAlarmPresent = false
            rxBus.send(EventDismissNotification(Notification.PUMP_ERROR))
            rxBus.send(EventDismissNotification(Notification.PUMP_WARNING))
        }

        // New alarms
        if (!pump.isAlarmPresent && update.current.alarms.isNotEmpty()) {
            if (pump.isBolusing) {
                // Pump sends early heartbeat while bolusing if there's an error while bolusing.
                aapsLogger.error(LTag.PUMP, "Bolus has failed!")
                onBolusFailed()
            }

            for (alarm in update.current.alarms) {
                val name = when (alarm) {
                    Alarm.NoDosage, Alarm.NoDelivery -> rh.gs(R.string.alarm_occlusion)
                    Alarm.NoReservoir -> rh.gs(R.string.alarm_reservoir_empty)
                    Alarm.DeadBattery -> rh.gs(R.string.alarm_battery_dead)
                    Alarm.LowBattery -> rh.gs(R.string.alarm_w_battery_low)
                    Alarm.LowReservoir -> rh.gs(R.string.alarm_w_reservoir_low)
                    Alarm.EncoderError, Alarm.FRAMError, Alarm.ClockError, Alarm.TimeError,
                    Alarm.TimeAnomalyError, Alarm.MotorAbnormal, Alarm.MotorPowerAbnormal,
                    Alarm.MotorError -> rh.gs(R.string.alarm_hardware_fault, alarm.name)
                    Alarm.Unknown -> rh.gs(R.string.alarm_unknown_error)
                    else -> rh.gs(R.string.alarm_unknown_error_name, alarm.name)
                }
                val isUrgent = when(alarm) {
                    Alarm.LowBattery, Alarm.LowReservoir -> false
                    else -> true
                }

                uiInteraction.addNotification(
                    if (isUrgent) Notification.PUMP_ERROR else Notification.PUMP_WARNING,
                    name,
                    if (isUrgent) Notification.URGENT else Notification.NORMAL,
                )
                pumpSync.insertAnnouncement(
                    error = name,
                    pumpType = PumpType.APEX_TRUCARE_III,
                    pumpSerial = apexDeviceInfo.serialNumber,
                )
            }
        }
    }

    private fun onBasalChanged(update: ApexPump.StatusUpdate) {
        if (update.current.basal == null) {
            uiInteraction.addNotification(
                Notification.PUMP_SUSPENDED,
                rh.gs(R.string.notification_pump_is_suspended),
                if (pump.isBolusing) Notification.URGENT else Notification.NORMAL,
            )
            commandQueue.loadEvents(null)
            return
        } else {
            rxBus.send(EventDismissNotification(Notification.PUMP_SUSPENDED))
        }
    }

    private fun onSettingsChanged(update: ApexPump.StatusUpdate) {
        if (pump.settingsAreUnadvised && preferences.get(ApexDoubleKey.MaxBasal) != 0.0 && preferences.get(ApexDoubleKey.MaxBolus) != 0.0) updateSettings("ApexService-onSettingsChanged")
        if (update.current.currentBasalPattern != USED_BASAL_PATTERN_INDEX) updateBasalPatternIndex(USED_BASAL_PATTERN_INDEX, "ApexService-onSettingsChanged")
    }

    private fun onBatteryChanged(update: ApexPump.StatusUpdate) {
        val cur = update.current
        update.previous?.let { old ->
            // Percentage became higher - battery was changed.
            if (cur.batteryLevel.percentage - 2 > old.batteryLevel.percentage && preferences.get(ApexBooleanKey.LogBatteryChange)) {
                pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = System.currentTimeMillis(),
                    pumpType = PumpType.APEX_TRUCARE_III,
                    pumpSerial = apexDeviceInfo.serialNumber,
                    type = TE.Type.PUMP_BATTERY_CHANGE,
                )
                aapsLogger.debug(LTag.PUMP, "Logged battery change")
            }
        }
    }

    private fun onReservoirChanged(update: ApexPump.StatusUpdate) {
        val cur = update.current
        update.previous?.let { old ->
            // Reservoir level became higher - insulin was changed.
            if (cur.reservoirLevel - 2 > old.reservoirLevel && preferences.get(ApexBooleanKey.LogInsulinChange)) {
                pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = System.currentTimeMillis(),
                    pumpType = PumpType.APEX_TRUCARE_III,
                    pumpSerial = apexDeviceInfo.serialNumber,
                    type = TE.Type.INSULIN_CHANGE,
                )
                aapsLogger.debug(LTag.PUMP, "Logged insulin change")
            }
        }
    }

    private fun onTBRChanged(update: ApexPump.StatusUpdate) {
        // if (update.current.tbr == null && update.previous?.tbr != null) {
        //     val stop = System.currentTimeMillis()
        //     pumpSync.syncStopTemporaryBasalWithPumpId(
        //         timestamp = stop,
        //         endPumpId = stop,
        //         pumpType = PumpType.APEX_TRUCARE_III,
        //         pumpSerial = apexDeviceInfo.serialNumber,
        //     )
        //     aapsLogger.debug(LTag.PUMP, "Detected TBR cancellation")
        // }
    }

    private fun onConstraintsChanged(update: ApexPump.StatusUpdate) {
        preferences.put(ApexDoubleKey.MaxBasal, update.current.maxBasal)
        preferences.put(ApexDoubleKey.MaxBolus, update.current.maxBolus)
    }

    private fun onStatusCommon(update: ApexPump.StatusUpdate) {
        aapsLogger.debug(LTag.PUMPCOMM, "Status updates: ${update.changes.joinToString(", ") { it.name }}")

        preferences.put(ApexDoubleKey.MaxBasal, update.current.maxBasal)
        preferences.put(ApexDoubleKey.MaxBolus, update.current.maxBolus)
        apexPumpPlugin.updatePumpDescription()

        onAlarmsChanged(update)
        onBasalChanged(update)
        onSettingsChanged(update)
        onBatteryChanged(update)
        onReservoirChanged(update)
        onTBRChanged(update)
        onConstraintsChanged(update)
    }

    private fun onStatusV1(status: StatusV1) {
        aapsLogger.debug(LTag.PUMPCOMM, "Got status V1")
        val updates = pump.updateFromV1(status)
        onStatusCommon(updates)
    }

    private fun onHeartbeat() {
        aapsLogger.debug(LTag.PUMPCOMM, "Got heartbeat")
        if (pump.gettingReady) return

        if (!getStatus("HeartbeatHandler")) return
        if (!getBoluses("HeartbeatHandler")) return

        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
    }

    private fun onVersion(version: Version) {
        aapsLogger.debug(LTag.PUMPCOMM, "Got version")
        if (version.atleastProto(ProtocolVersion.PROTO_4_11)) {
            statusGetValue = GetValue.Value.StatusV2
        }
    }

    private fun onBolusEntry(entry: BolusEntry) {
        // Extended bolus entries do not have duration stored, do not use them.
        if (entry.extendedDose > 0) return

        if (entry.dateTime > lastBolusDateTime) {
            lastBolusDateTime = entry.dateTime
            pump.lastBolus = entry
            rxBus.send(EventApexPumpDataChanged())
        }

        // Find the bolus in history and sync it.
        // Pump may round up boluses, use 0.11 for failsafe.
        val ipb = pump.inProgressBolus
        if (waitingForCurrentBolusInHistory && ipb != null && entry.dateTime.millis >= ipb.temporaryId) {
            if (!ipb.cancelled && abs(entry.standardDose - ipb.currentDose) > 0.11) return

            val syncResult = pumpSync.syncBolusWithTempId(
                timestamp = entry.dateTime.millis,
                temporaryId = ipb.temporaryId,
                amount = entry.standardPerformed * 0.025,
                pumpId = entry.dateTime.millis,
                pumpType = PumpType.APEX_TRUCARE_III,
                pumpSerial = apexDeviceInfo.serialNumber,
                type = ipb.detailedBolusInfo.bolusType,
            )
            aapsLogger.debug(LTag.PUMP, "Final bolus [${entry.standardDose * 0.025}U -> ${entry.standardPerformed * 0.025}U] sync succeeded? $syncResult")
            pump.inProgressBolus = null
            waitingForCurrentBolusInHistory = false
            _bolusCompletable?.complete(ipb)
            _bolusCompletable = null
            return
        }

        // Otherwise, just sync the bolus with the DB
        pumpSync.syncBolusWithPumpId(
            timestamp = entry.dateTime.millis,
            pumpId = entry.dateTime.millis,
            amount = entry.standardPerformed * 0.025,
            pumpType = PumpType.APEX_TRUCARE_III,
            pumpSerial = apexDeviceInfo.serialNumber,
            type = null,
        )
        aapsLogger.debug(LTag.PUMP, "Synced bolus ${entry.standardPerformed * 0.025}U on ${entry.dateTime}")
    }

    // !! Unreliable on 6.25 firmware, TODO: think about solution
    private fun onTDDEntry(entry: TDDEntry) {
        pumpSync.createOrUpdateTotalDailyDose(
            timestamp = entry.dateTime.millis,
            pumpId = entry.dateTime.millis,
            pumpType = PumpType.APEX_TRUCARE_III,
            pumpSerial = apexDeviceInfo.serialNumber,
            bolusAmount = entry.bolus * 0.025,
            basalAmount = entry.basal * 0.025 + entry.temporaryBasal * 0.025,
            totalAmount = entry.total * 0.025,
        )
        aapsLogger.debug(LTag.PUMP, "Synced TDD ${entry.total * 0.025}U on ${entry.dateTime}")
    }

    //////// BLE

    private fun onInitialConnection() {
        preferences.put(ApexDoubleKey.MaxBasal, 0.0)
        preferences.put(ApexDoubleKey.MaxBolus, 0.0)
        pumpSync.connectNewPump()
    }

    fun startConnection() {
        if (apexDeviceInfo.serialNumber.isNotEmpty()) apexBluetooth.connect()
    }

    fun disconnect() {
        if (apexBluetooth.status != ApexBluetooth.Status.DISCONNECTED) apexBluetooth.disconnect()
    }


    override fun onConnect() = Thread {
        aapsLogger.debug(LTag.PUMPCOMM, "onConnect")

        val version = getValue(GetValue.Value.Version)?.firstOrNull()
        if (version !is Version) {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to get version - disconnecting.")
            return@Thread disconnect()
        }

        pump.firmwareVersion = version

        if (!version.isSupported(FIRST_SUPPORTED_PROTO, LAST_SUPPORTED_PROTO)) {
            aapsLogger.error(LTag.PUMPCOMM, "Unsupported protocol v${version.protocolMajor}.${version.protocolMinor} - disconnecting.")
            uiInteraction.addNotification(
                Notification.PUMP_ERROR,
                rh.gs(R.string.notification_pump_unsupported),
                Notification.URGENT,
            )
            return@Thread disconnect()
        }

        onVersion(version)
        aapsLogger.debug(LTag.PUMPCOMM, "Protocol v${version.protocolMajor}.${version.protocolMinor}")

        if (!syncDateTime("BLE-onConnect")) return@Thread
        if (!notifyAboutConnection("BLE-onConnect")) return@Thread

        if (apexDeviceInfo.serialNumber != preferences.get(ApexStringKey.LastConnectedSerialNumber)) {
            onInitialConnection()
            preferences.put(ApexStringKey.LastConnectedSerialNumber, apexDeviceInfo.serialNumber)
        }

        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        if (!getStatus("BLE-onConnect")) return@Thread
        if (!getBoluses("BLE-onConnect")) return@Thread

        unreachableTimerTask?.cancel()
        unreachableTimerTask = null
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        pump.gettingReady = false
    }.start()

    private var isDisconnectLoopRunning = false
    private fun spawnLoop() {
        if (isDisconnectLoopRunning) return
        isDisconnectLoopRunning = true
        Thread {
            while (connectionStatus != ApexBluetooth.Status.CONNECTED) {
                if (connectionStatus == ApexBluetooth.Status.DISCONNECTED) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Starting connection loop")
                    startConnection()
                }
                SystemClock.sleep(250)
            }
            aapsLogger.debug(LTag.PUMPCOMM, "Exiting")
            isDisconnectLoopRunning = false
        }.start()
    }

    override fun onDisconnect() = Thread {
        aapsLogger.debug(LTag.PUMPCOMM, "onDisconnect")
        getValueResult.clear()
        synchronized(getValueResult) {
            getValueResult.notifyAll()
        }
        commandResponse.clear()
        synchronized(commandResponse) {
            commandResponse.notifyAll()
        }
        lastConnectedTimestamp = System.currentTimeMillis()

        if (unreachableTimerTask == null)
            unreachableTimerTask = timer.schedule(60000) {
                uiInteraction.addNotification(
                    Notification.PUMP_UNREACHABLE,
                    rh.gs(R.string.error_pump_unreachable),
                    Notification.URGENT,
                )
                aapsLogger.error(LTag.PUMP, "Pump unreachable!")
            }

        spawnLoop()
        pump.gettingReady = true
    }.start()

    private var isGetThreadRunning = false
    override fun onPumpCommand(command: PumpCommand) = Thread {
        if (command.id == null) {
            aapsLogger.error(LTag.PUMPCOMM, "Invalid command with crc ${command.checksum}")
            return@Thread
        }
        val type = PumpObject.findObject(command.id!!, command.objectType, command.objectData)
        aapsLogger.debug(LTag.PUMPCOMM, "from PUMP: ${command.id!!.name}, ${type?.name}")

        when (type) {
            PumpObject.CommandResponse -> onCommandResponse(CommandResponse(command))
            PumpObject.StatusV1        -> onStatusV1(StatusV1(command))
            PumpObject.Heartbeat       -> onHeartbeat()
            PumpObject.BolusEntry      -> onBolusEntry(BolusEntry(command))
            PumpObject.TDDEntry        -> onTDDEntry(TDDEntry(command))
            else -> {}
        }

        if (!getValueResult.waiting) return@Thread
        if (type != getValueResult.targetObject) return@Thread

        getValueResult.add(
            when (type) {
                PumpObject.Heartbeat       -> Heartbeat()
                PumpObject.CommandResponse -> CommandResponse(command)
                PumpObject.StatusV1        -> StatusV1(command)
                PumpObject.BasalProfile    -> BasalProfile(command)
                PumpObject.AlarmEntry      -> AlarmObject(command)
                PumpObject.TDDEntry        -> TDDEntry(command)
                PumpObject.BolusEntry      -> BolusEntry(command)
                PumpObject.FirmwareEntry   -> Version(command)
                else                       -> return@Thread
            }
        )

        if (getValueResult.isSingleObject) {
            getValueResult.waiting = false
            synchronized(getValueResult) {
                getValueResult.notifyAll()
            }
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Updating last timestamp")
            getValueLastTaskTimestamp = System.currentTimeMillis()
            runGetThread()
        }
    }.start()

    private fun runGetThread() {
        if (isGetThreadRunning) return
        isGetThreadRunning = true
        Thread {
            while (true) {
                val now = System.currentTimeMillis()
                if (now - getValueLastTaskTimestamp >= 500) {
                    break
                } else {
                    aapsLogger.debug(LTag.PUMPCOMM, "Response is not ready yet")
                }
                SystemClock.sleep(250)
            }
            isGetThreadRunning = false

            aapsLogger.debug(LTag.PUMPCOMM, "Chunked response has completed")
            getValueResult.waiting = false
            synchronized(getValueResult) {
                getValueResult.notifyAll()
            }
        }.start()
    }

    //////// Binder

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder {
        aapsLogger.debug(LTag.PUMP, "Binding service")
        return binder
    }

    inner class LocalBinder : Binder() {
        val serviceInstance: ApexService
            get() = this@ApexService
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        aapsLogger.debug(LTag.PUMP, "Service started")
        return START_STICKY
    }
}