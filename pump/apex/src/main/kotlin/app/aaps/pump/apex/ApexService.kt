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
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
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
import app.aaps.pump.apex.connectivity.commands.pump.StatusV2
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
import kotlinx.coroutines.sync.Mutex
import org.joda.time.DateTime
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import kotlin.concurrent.schedule
import kotlin.math.abs
import kotlin.math.roundToInt

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
        val LAST_SUPPORTED_PROTO = ProtocolVersion.PROTO_4_11
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

    private var lastBolusDateTime = DateTime(0)
    private var lastConnectedTimestamp = System.currentTimeMillis()

    private var manualDisconnect = false
    private var doNotReconnect = false
    private var connectionFinished = false

    val isBusy: Boolean
        get() = commandLock.isLocked

    val lastConnected: Long
        get() = if (connectionStatus != ApexBluetooth.Status.CONNECTED) {
            lastConnectedTimestamp
        } else System.currentTimeMillis()

    private fun intGetValue(value: GetValue.Value): List<PumpObjectModel>? = synchronized(getValueResult) {
        if (connectionStatus != ApexBluetooth.Status.CONNECTED) {
            aapsLogger.debug(LTag.PUMPCOMM, "Get ${value.name} | Error - pump is disconnected")
            return null
        }

        getValueResult.clear()
        getValueResult.targetObject = when (value) {
            GetValue.Value.StatusV1 -> PumpObject.StatusV1
            GetValue.Value.StatusV2 -> PumpObject.StatusV2
            GetValue.Value.TDDs -> PumpObject.TDDEntry
            GetValue.Value.Alarms -> PumpObject.AlarmEntry
            GetValue.Value.BasalProfiles -> PumpObject.BasalProfile
            GetValue.Value.Version -> PumpObject.FirmwareEntry
            GetValue.Value.BolusHistory, GetValue.Value.LatestBoluses -> PumpObject.BolusEntry
            GetValue.Value.LatestTemporaryBasals -> return null
            GetValue.Value.WizardStatus -> return null
        }
        getValueResult.isSingleObject = when (value) {
            GetValue.Value.StatusV1, GetValue.Value.StatusV2, GetValue.Value.Version -> true
            else -> false
        }

        apexBluetooth.send(GetValue(apexDeviceInfo, value))
        try {
            aapsLogger.debug(LTag.PUMPCOMM, "Get ${value.name} | Waiting for response")
            getValueResult.waitMillis(if (getValueResult.isSingleObject) 5000 else 15000)
        } catch (e: InterruptedException) {
            aapsLogger.error(LTag.PUMPCOMM, "Get ${value.name} | Timed out")
            isGetThreadRunning = false
            return null
        }

        if (getValueResult.response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "Get ${value.name} | Timed out")
            isGetThreadRunning = false
            return null
        }

        aapsLogger.debug(LTag.PUMPCOMM, "Get ${value.name} | Completed")
        getValueResult.response
    }

    private fun intExecuteWithResponse(command: DeviceCommand): CommandResponse? = synchronized(commandResponse) {
        if (connectionStatus != ApexBluetooth.Status.CONNECTED) {
            aapsLogger.debug(LTag.PUMPCOMM, "$command | Error - pump is disconnected")
            return null
        }

        commandResponse.clear()
        apexBluetooth.send(command)
        try {
            aapsLogger.debug(LTag.PUMPCOMM, "$command | Waiting for response")
            commandResponse.waitMillis(5000)
        } catch (e: InterruptedException) {
            aapsLogger.error(LTag.PUMPCOMM, "$command | Timed out")
            commandResponse.waiting = false
            return null
        }

        if (commandResponse.response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "$command | Timed out")
            commandResponse.waiting = false
            return null
        }

        aapsLogger.debug(LTag.PUMPCOMM, "$command | Completed")
        commandResponse.response
    }

    fun getValue(value: GetValue.Value): List<PumpObjectModel>? {
        synchronized(commandLock) {
            val firstTry = intGetValue(value)
            if (firstTry != null || doNotReconnect || !connectionFinished) return@getValue firstTry
            doNotReconnect = true
        }

        disconnect(true)
        if (!ensureConnected()) {
            synchronized(commandLock) { doNotReconnect = false }
            return null
        }

        synchronized(commandLock) {
            val final = intGetValue(value)
            doNotReconnect = false
            return@getValue final
        }
    }

    private fun executeWithResponse(command: DeviceCommand): CommandResponse? {
        synchronized(commandLock) {
            val firstTry = intExecuteWithResponse(command)
            if (firstTry != null || doNotReconnect || !connectionFinished) return@executeWithResponse firstTry
            doNotReconnect = true
        }

        disconnect(true)
        if (!ensureConnected()) {
            synchronized(commandLock) { doNotReconnect = false }
            return null
        }

        synchronized(commandLock) {
            val final = intExecuteWithResponse(command)
            doNotReconnect = false
            return@executeWithResponse final
        }
    }

    private fun ensureConnected(): Boolean {
        var times = 0
        while (!connectionFinished && times < 50) {
            aapsLogger.debug(LTag.PUMPCOMM, "Waiting for successful connection")
            SystemClock.sleep(500)
            times++
        }
        return connectionFinished
    }

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

        val doseRaw = (dbi.insulin / 0.025).roundToInt()

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

        getStatus("ApexService-bolus")
        return true
    }

    fun extendedBolus(dose: Double, durationMinutes: Int, caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "extendedBolus - $caller")
        val doseRaw = (dose / 0.025).roundToInt()

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

        val doseRaw = (dose / 0.025).roundToInt()

        val durationRaw = durationMinutes / 15
        if (durationMinutes % 15 > 0) aapsLogger.warn(LTag.PUMPCOMM, "[temporaryBasal caller=$caller] Bolus duration is not aligned to 15 minute steps! Rounded down.")

        val response = executeWithResponse(TemporaryBasal(apexDeviceInfo, true, durationRaw, doseRaw))
        if (response == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[temporaryBasal caller=$caller] Timed out while trying to communicate with the pump")
            return false
        }

        if (response.code != CommandResponse.Code.Accepted) {
            aapsLogger.error(LTag.PUMPCOMM, "[caller=$caller] Failed to start temporary basal: ${response.code.name}")
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
        getStatus("ApexService-temporaryBasal")
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
        getStatus("ApexService-cancelBolus")
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

        getStatus("ApexService-cancelTBR")
        return true
    }

    fun updateSettings(caller: String): Boolean {
        aapsLogger.debug(LTag.PUMPCOMM, "updateSettings - $caller")
        val response = executeWithResponse(
            pump.lastV1!!.toUpdateSettingsV1(
                apexDeviceInfo,
                AlarmLength.valueOf(preferences.get(ApexStringKey.AlarmSoundLength)),
                maxSingleBolus = (preferences.get(ApexDoubleKey.MaxBolus) / 0.025).roundToInt(),
                maxBasalRate = (preferences.get(ApexDoubleKey.MaxBasal) / 0.025).roundToInt(),
                enableAdvancedBolus = false,
            )
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

        if (!commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) rxBus.send(EventProfileSwitchChanged())
        return true
    }

    fun updateCurrentBasalPattern(doses: List<Double>, caller: String): Boolean {
        require(doses.size == 48)

        aapsLogger.debug(LTag.PUMPCOMM, "updateCurrentBasalPattern - $caller")

        val response = executeWithResponse(UpdateBasalProfileRates(
            apexDeviceInfo,
            doses.map { (it / 0.025).roundToInt() }
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
        val responseV1 = getValue(GetValue.Value.StatusV1)
        if (responseV1 == null) {
            aapsLogger.error(LTag.PUMPCOMM, "[getStatus caller=$caller] V1 | Timed out while trying to communicate with the pump")
            return false
        }

        if ((pump.firmwareVersion?.protocolMinor ?: 0) >= 11) {
            val responseV2 = getValue(GetValue.Value.StatusV2)
            if (responseV2 == null) {
                aapsLogger.error(LTag.PUMPCOMM, "[getStatus caller=$caller] V2 | Timed out while trying to communicate with the pump")
                return false
            }
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
            percent = (bolus.currentDose / bolus.requestedDose * 100).roundToInt()
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
        Thread { getBoluses("ApexService-onBolusCompleted") }.start()
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
            Thread { getBoluses("ApexService-onBolusCompleted") }.start()
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "bolus entirely failed!")
            synchronized(pump.inProgressBolus!!) {
                pump.inProgressBolus!!.failed = true
                pump.inProgressBolus!!.notifyAll()
            }
            SystemClock.sleep(10)
            pump.inProgressBolus = null
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
        val prev = update.previous?.alarms
        // Alarm was dismissed
        if (!prev.isNullOrEmpty() && update.current.alarms.isEmpty()) {
            rxBus.send(EventDismissNotification(Notification.PUMP_ERROR))
            rxBus.send(EventDismissNotification(Notification.PUMP_WARNING))
        }

        // New alarms
        if (prev.isNullOrEmpty() && update.current.alarms.isNotEmpty()) {
            var anyUrgent = false

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
                    Alarm.CheckGlucose -> rh.gs(R.string.alarm_check_bg)
                    else -> rh.gs(R.string.alarm_unknown_error_name, alarm.name)
                }
                val isUrgent = when(alarm) {
                    Alarm.LowBattery, Alarm.LowReservoir, Alarm.CheckGlucose -> false
                    else -> true
                }
                if (isUrgent) anyUrgent = true

                uiInteraction.addNotification(
                    if (isUrgent) Notification.PUMP_ERROR else Notification.PUMP_WARNING,
                    rh.gs(R.string.alarm_label, name),
                    if (isUrgent) Notification.URGENT else Notification.NORMAL,
                )
                pumpSync.insertAnnouncement(
                    error = rh.gs(R.string.alarm_label, name),
                    pumpType = PumpType.APEX_TRUCARE_III,
                    pumpSerial = apexDeviceInfo.serialNumber,
                )
            }

            if (anyUrgent && pump.isBolusing) {
                // Pump sends early heartbeat while bolusing if there's an error while bolusing.
                aapsLogger.error(LTag.PUMP, "Bolus has failed!")
                Thread { onBolusFailed() }.start()
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
            if (cur.batteryLevel.percentage - 26 > old.batteryLevel.percentage && preferences.get(ApexBooleanKey.LogBatteryChange)) {
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

    private fun onStatusV1(status: StatusV1) {
        val update = pump.updateFromV1(status)
        aapsLogger.debug(LTag.PUMPCOMM, "Got V1 | Status updates: ${update.changes.joinToString(", ") { it.name }}")

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
        rxBus.send(EventApexPumpDataChanged())
    }

    private fun onStatusV2(status: StatusV2) {
        val update = pump.updateFromV2(status)
        aapsLogger.debug(LTag.PUMPCOMM, "Got V2 | Status updates: ${update.changes.joinToString(", ") { it.name }}")

        //onBatteryChanged(update)
        rxBus.send(EventApexPumpDataChanged())
    }

    private fun onHeartbeat() {
        aapsLogger.debug(LTag.PUMPCOMM, "Got heartbeat")

        // Pump sent heartbeat => connection is established.
        pump.gettingReady = false

        if (!getStatus("HeartbeatHandler")) return
        if (!getBoluses("HeartbeatHandler")) return

        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
    }

    private fun onVersion(version: Version) {
        aapsLogger.debug(LTag.PUMPCOMM, "Got version - $version")
    }

    @Synchronized
    private fun onBolusEntry(entry: BolusEntry) {
        // Extended bolus entries do not have duration stored, do not use them.
        if (entry.extendedDose > 0) return

        aapsLogger.debug(LTag.PUMP, "Processing bolus [${entry.standardDose * 0.025}U -> ${entry.standardPerformed * 0.025}U] on ${entry.dateTime}")

        if (entry.dateTime > lastBolusDateTime) {
            lastBolusDateTime = entry.dateTime
            pump.lastBolus = entry
            rxBus.send(EventApexPumpDataChanged())
        }

        // Find the bolus in history and sync it.
        // Pump may round up boluses, use 0.11 for failsafe.
        val ipb = pump.inProgressBolus
        if (ipb != null && entry.dateTime.millis - ipb.temporaryId >= -45000) {
            aapsLogger.debug(LTag.PUMP, "Syncing current bolus [${entry.standardDose * 0.025}U -> ${entry.standardPerformed * 0.025}U]")
            val delta = abs(entry.standardDose * 0.025 - ipb.currentDose)
            if (!ipb.cancelled && delta > 0.11) {
                aapsLogger.debug(LTag.PUMP, "Not this bolus: $delta > 0.11")
                return
            }

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
            synchronized(pump.inProgressBolus!!) {
                pump.inProgressBolus!!.notifyAll()
            }
            SystemClock.sleep(10)
            pump.inProgressBolus = null

            getStatus("ApexService-updateAfterBolus")
            return
        }
        if (ipb != null && entry.index < 2) return

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
        if (apexDeviceInfo.serialNumber.isEmpty()) return
        manualDisconnect = false
        apexBluetooth.connect()
    }

    fun disconnect(isReconnect: Boolean = false) {
        manualDisconnect = !isReconnect
        if (apexBluetooth.status != ApexBluetooth.Status.DISCONNECTED)
            apexBluetooth.disconnect()
        else if (isReconnect)
            apexBluetooth.connect()
    }


    override fun onConnect() {
        aapsLogger.debug(LTag.PUMPCOMM, "onConnect")

        val version = getValue(GetValue.Value.Version)?.firstOrNull()
        if (version !is Version) {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to get version - disconnecting.")
            return disconnect(true)
        }

        aapsLogger.debug(LTag.PUMPCOMM, version.toString())

        pump.firmwareVersion = version

        if (!version.isSupported(FIRST_SUPPORTED_PROTO, LAST_SUPPORTED_PROTO)) {
            aapsLogger.error(LTag.PUMPCOMM, "Unsupported protocol v${version.protocolMajor}.${version.protocolMinor} - disconnecting.")
            uiInteraction.addNotification(
                Notification.PUMP_ERROR,
                rh.gs(R.string.notification_pump_unsupported),
                Notification.URGENT,
            )
            return disconnect()
        }

        onVersion(version)

        if (!syncDateTime("BLE-onConnect")) {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to sync date and time - disconnecting.")
            return disconnect(true)
        }
        if (!notifyAboutConnection("BLE-onConnect")) {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to notify about connection - disconnecting.")
            return disconnect(true)
        }

        if (apexDeviceInfo.serialNumber != preferences.get(ApexStringKey.LastConnectedSerialNumber)) {
            onInitialConnection()
            preferences.put(ApexStringKey.LastConnectedSerialNumber, apexDeviceInfo.serialNumber)
        }

        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        if (!getStatus("BLE-onConnect")) {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to get status - disconnecting.")
            return disconnect(true)
        }
        if (!getBoluses("BLE-onConnect"))  {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to get boluses - disconnecting.")
            return disconnect(true)
        }

        unreachableTimerTask?.cancel()
        unreachableTimerTask = null
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        pump.gettingReady = false
        connectionFinished = true
    }

    private var isDisconnectLoopRunning = false
    private fun spawnLoop() {
        if (isDisconnectLoopRunning) return
        isDisconnectLoopRunning = true
        Thread {
            while (connectionStatus != ApexBluetooth.Status.CONNECTED && !manualDisconnect) {
                if (connectionStatus == ApexBluetooth.Status.DISCONNECTED) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Starting connection loop")
                    startConnection()
                }
                SystemClock.sleep(100)
            }

            if (manualDisconnect) {
                aapsLogger.debug(LTag.PUMPCOMM, "Manual disconnect detected!")
                disconnect()
            }

            aapsLogger.debug(LTag.PUMPCOMM, "Exiting")
            isDisconnectLoopRunning = false
        }.start()
    }

    override fun onDisconnect() {
        aapsLogger.debug(LTag.PUMPCOMM, "onDisconnect")
        connectionFinished = false

        isGetThreadRunning = false
        synchronized(getValueResult) {
            getValueResult.waiting = false
            getValueResult.notifyAll()
        }
        synchronized(commandResponse) {
            commandResponse.waiting = false
            commandResponse.notifyAll()
        }


        lastConnectedTimestamp = System.currentTimeMillis()

        if (unreachableTimerTask == null)
            unreachableTimerTask = timer.schedule(120000) {
                uiInteraction.addNotification(
                    Notification.PUMP_UNREACHABLE,
                    rh.gs(R.string.error_pump_unreachable),
                    Notification.URGENT,
                )
                aapsLogger.error(LTag.PUMP, "Pump unreachable!")
            }

        if (!manualDisconnect) spawnLoop()
    }

    private var isGetThreadRunning = false
    override fun onPumpCommand(command: PumpCommand) {
        if (command.id == null) {
            aapsLogger.error(LTag.PUMPCOMM, "Invalid command with crc ${command.checksum}")
            return
        }
        val type = PumpObject.findObject(command.id!!, command.objectData, aapsLogger)
        aapsLogger.debug(LTag.PUMPCOMM, "from PUMP: ${command.id!!.name}, ${type?.name}")

        if (type == null) return
        if (type == PumpObject.CommandResponse) return onCommandResponse(CommandResponse(command))

        notifyAboutResponse(command, type)
        Thread { processObject(command, type) }.start()
    }

    private fun processObject(command: PumpCommand, type: PumpObject) {
        when (type) {
            PumpObject.StatusV1        -> onStatusV1(StatusV1(command))
            PumpObject.StatusV2        -> onStatusV2(StatusV2(command))
            PumpObject.Heartbeat       -> onHeartbeat()
            PumpObject.BolusEntry      -> onBolusEntry(BolusEntry(command))
            PumpObject.TDDEntry        -> onTDDEntry(TDDEntry(command))
            else -> {}
        }
    }

    @Synchronized
    private fun notifyAboutResponse(command: PumpCommand, type: PumpObject) {
        if (!getValueResult.waiting) {
            aapsLogger.debug(LTag.PUMPCOMM, "Got pump command but not waiting for it")
            return
        }
        if (type != getValueResult.targetObject) {
            aapsLogger.debug(LTag.PUMPCOMM, "Got incorrect object type (${type.name} vs ${getValueResult.targetObject?.name})")
            return
        }

        // Pump may send fake bolus entry if there are no boluses in history.
        // We shouldn't handle it.
        if (command.objectData[2].toUInt().toInt() == 0xFF && command.objectData[3].toUInt().toInt() == 0xFF) {
            aapsLogger.debug(LTag.PUMPCOMM, "Got fake bolus entry - skipping")
            getValueResult.waiting = false
            getValueResult.response = arrayListOf()
            synchronized(getValueResult) {
                getValueResult.notifyAll()
            }
            return
        }

        getValueResult.add(
            when (type) {
                PumpObject.Heartbeat       -> Heartbeat()
                PumpObject.CommandResponse -> CommandResponse(command)
                PumpObject.StatusV1        -> StatusV1(command)
                PumpObject.StatusV2        -> StatusV2(command)
                PumpObject.BasalProfile    -> BasalProfile(command)
                PumpObject.AlarmEntry      -> AlarmObject(command)
                PumpObject.TDDEntry        -> TDDEntry(command)
                PumpObject.BolusEntry      -> BolusEntry(command)
                PumpObject.FirmwareEntry   -> Version(command)
                else                       -> return
            }
        )

        if (getValueResult.isSingleObject) {
            aapsLogger.debug(LTag.PUMPCOMM, "Got single value - everything is ready")
            getValueResult.waiting = false
            synchronized(getValueResult) {
                getValueResult.notifyAll()
            }
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Updating last timestamp")
            getValueLastTaskTimestamp = System.currentTimeMillis()
            runGetThread()
        }
    }

    @Synchronized
    private fun runGetThread() {
        if (isGetThreadRunning) return
        isGetThreadRunning = true
        aapsLogger.debug(LTag.PUMPCOMM, "Running GET thread")
        Thread {
            while (isGetThreadRunning) {
                val now = System.currentTimeMillis()
                if (now - getValueLastTaskTimestamp >= 500) {
                    break
                } else {
                    aapsLogger.debug(LTag.PUMPCOMM, "Response is not ready yet")
                }
                SystemClock.sleep(100)
            }
            if (!isGetThreadRunning) {
                aapsLogger.debug(LTag.PUMPCOMM, "GET thread killed")
                return@Thread
            }
            isGetThreadRunning = false

            aapsLogger.debug(LTag.PUMPCOMM, "Chunked response has completed")
            getValueResult.waiting = false
            synchronized(getValueResult) {
                getValueResult.notifyAll()
            }
        }.start()
        // Let thread start
        SystemClock.sleep(10)
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