package info.nightscout.pump.common

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.text.format.DateFormat
import com.google.gson.GsonBuilder
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpPluginBase
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.PumpSync.TemporaryBasalType
import info.nightscout.interfaces.pump.defs.ManufacturerType
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.utils.DecimalFormatter.to0Decimal
import info.nightscout.interfaces.utils.DecimalFormatter.to2Decimal
import info.nightscout.pump.common.data.PumpStatus
import info.nightscout.pump.common.defs.PumpDriverState
import info.nightscout.pump.common.sync.PumpDbEntryCarbs
import info.nightscout.pump.common.sync.PumpSyncStorage
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.events.EventCustomActionsChanged
import info.nightscout.rx.events.EventOverviewBolusProgress
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by andy on 23.04.18.
 */
// When using this class, make sure that your first step is to create mConnection (see MedtronicPumpPlugin)
abstract class PumpPluginAbstract protected constructor(
    pluginDescription: PluginDescription,
    pumpType: PumpType,
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    commandQueue: CommandQueue,
    var rxBus: RxBus,
    var activePlugin: ActivePlugin,
    var sp: SP,
    var context: Context,
    var fabricPrivacy: FabricPrivacy,
    var dateUtil: DateUtil,
    var aapsSchedulers: AapsSchedulers,
    var pumpSync: PumpSync,
    var pumpSyncStorage: PumpSyncStorage
) : PumpPluginBase(pluginDescription, injector, aapsLogger, rh, commandQueue), Pump, Constraints, info.nightscout.pump.common.sync.PumpSyncEntriesCreator {

    protected val disposable = CompositeDisposable()

    // Pump capabilities
    final override var pumpDescription = PumpDescription()
    //protected set

    protected var serviceConnection: ServiceConnection? = null
    protected var serviceRunning = false
    protected var pumpState = PumpDriverState.NotInitialized
    protected var displayConnectionMessages = false

    var pumpType: PumpType = PumpType.GENERIC_AAPS
        set(value) {
            field = value
            pumpDescription.fillFor(value)
        }

    protected var gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    abstract fun initPumpStatusData()

    open fun hasService(): Boolean {
        return true
    }

    override fun onStart() {
        super.onStart()
        initPumpStatusData()
        if (hasService()) {
            val intent = Intent(context, serviceClass)
            context.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
            disposable.add(rxBus
                               .toObservable(EventAppExit::class.java)
                               .observeOn(aapsSchedulers.io)
                               .subscribe({ _ -> context.unbindService(serviceConnection!!) }) { throwable: Throwable? -> fabricPrivacy.logException(throwable!!) }
            )
        }
        serviceRunning = true
        onStartScheduledPumpActions()
    }

    override fun onStop() {
        aapsLogger.debug(LTag.PUMP, model().model + " onStop()")
        if (hasService()) {
            context.unbindService(serviceConnection!!)
        }
        serviceRunning = false
        disposable.clear()
        super.onStop()
    }

    /**
     * If we need to run any custom actions in onStart (triggering events, etc)
     */
    abstract fun onStartScheduledPumpActions()

    /**
     * Service class (same one you did serviceConnection for)
     *
     * @return Class
     */
    abstract val serviceClass: Class<*>?
    abstract val pumpStatusData: PumpStatus

    override fun isInitialized(): Boolean = pumpState.isInitialized()
    override fun isSuspended(): Boolean = pumpState == PumpDriverState.Suspended
    override fun isBusy(): Boolean = pumpState == PumpDriverState.Busy

    override fun isConnected(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "isConnected [PumpPluginAbstract].")
        return pumpState.isConnected()
    }

    override fun isConnecting(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "isConnecting [PumpPluginAbstract].")
        return pumpState === PumpDriverState.Connecting
    }

    override fun connect(reason: String) {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "connect (reason={}) [PumpPluginAbstract] - default (empty) implementation.$reason")
    }

    override fun disconnect(reason: String) {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "disconnect (reason={}) [PumpPluginAbstract] - default (empty) implementation.$reason")
    }

    override fun stopConnecting() {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "stopConnecting [PumpPluginAbstract] - default (empty) implementation.")
    }

    override fun isHandshakeInProgress(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "isHandshakeInProgress [PumpPluginAbstract] - default (empty) implementation.")
        return false
    }

    override fun finishHandshaking() {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "finishHandshaking [PumpPluginAbstract] - default (empty) implementation.")
    }

    // Upload to pump new basal profile
    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "setNewBasalProfile [PumpPluginAbstract] - Not implemented.")
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver)
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        aapsLogger.debug(LTag.PUMP, "isThisProfileSet [PumpPluginAbstract] - Not implemented.")
        return true
    }

    override fun lastDataTime(): Long {
        aapsLogger.debug(LTag.PUMP, "lastDataTime [PumpPluginAbstract].")
        return pumpStatusData.lastConnection
    }

    // base basal rate, not temp basal
    override val baseBasalRate: Double
        get() {
            aapsLogger.debug(LTag.PUMP, "getBaseBasalRate [PumpPluginAbstract] - Not implemented.")
            return 0.0
        }

    override fun stopBolusDelivering() {
        aapsLogger.debug(LTag.PUMP, "stopBolusDelivering [PumpPluginAbstract] - Not implemented.")
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute [PumpPluginAbstract] - Not implemented.")
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver)
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "setTempBasalPercent [PumpPluginAbstract] - Not implemented.")
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver)
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "setExtendedBolus [PumpPluginAbstract] - Not implemented.")
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver)
    }

    // some pumps might set a very short temp close to 100% as cancelling a temp can be noisy
    // when the cancel request is requested by the user (forced), the pump should always do a real cancel
    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "cancelTempBasal [PumpPluginAbstract] - Not implemented.")
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver)
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus [PumpPluginAbstract] - Not implemented.")
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver)
    }

    // Status to be passed to NS
    // public JSONObject getJSONStatus(Profile profile, String profileName) {
    // return pumpDriver.getJSONStatus(profile, profileName);
    // }
    open fun deviceID(): String {
        aapsLogger.debug(LTag.PUMP, "deviceID [PumpPluginAbstract] - Not implemented.")
        return "FakeDevice"
    }

    // Short info for SMS, Wear etc
    override val isFakingTempsByExtendedBoluses: Boolean
        get() {
            aapsLogger.debug(LTag.PUMP, "isFakingTempsByExtendedBoluses [PumpPluginAbstract] - Not implemented.")
            return false
        }

    override fun loadTDDs(): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "loadTDDs [PumpPluginAbstract] - Not implemented.")
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver)
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        if (pumpStatusData.lastConnection + 60 * 60 * 1000L < System.currentTimeMillis()) {
            return JSONObject()
        }
        val now = System.currentTimeMillis()
        val pump = JSONObject()
        val battery = JSONObject()
        val status = JSONObject()
        val extended = JSONObject()
        try {
            battery.put("percent", pumpStatusData.batteryRemaining)
            status.put("status", pumpStatusData.pumpRunningState.status)
            extended.put("Version", version)
            try {
                extended.put("ActiveProfile", profileName)
            } catch (ignored: Exception) {
            }
            val tb = pumpSync.expectedPumpState().temporaryBasal
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.convertedToAbsolute(now, profile))
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.timestamp))
                extended.put("TempBasalRemaining", tb.plannedRemainingMinutes)
            }
            val eb = pumpSync.expectedPumpState().extendedBolus
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.rate)
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.timestamp))
                extended.put("ExtendedBolusRemaining", eb.plannedRemainingMinutes)
            }
            status.put("timestamp", dateUtil.toISOString(dateUtil.now()))
            pump.put("battery", battery)
            pump.put("status", status)
            pump.put("extended", extended)
            pump.put("reservoir", pumpStatusData.reservoirRemainingUnits)
            pump.put("clock", dateUtil.toISOString(dateUtil.now()))
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return pump
    }

    // FIXME i18n, null checks: iob, TDD
    override fun shortStatus(veryShort: Boolean): String {
        var ret = ""

        ret += if (pumpStatusData.lastConnection == 0L) {
            "LastConn: never\n"
        } else {
            val agoMin = ((System.currentTimeMillis() - pumpStatusData.lastConnection) / 60.0 / 1000.0).toInt()
            "LastConn: $agoMin min ago\n"
        }

        pumpStatusData.lastBolusTime?.let {
            if (it.time != 0L) {
                ret += "LastBolus: ${to2Decimal(pumpStatusData.lastBolusAmount!!)}U @${DateFormat.format("HH:mm", it)}\n"
            }
        }
        pumpSync.expectedPumpState().temporaryBasal?.let { ret += "Temp: ${it.toStringFull(dateUtil)}\n" }
        pumpSync.expectedPumpState().extendedBolus?.let { ret += "Extended: ${it.toStringFull(dateUtil)}\n" }
        ret += "IOB: ${pumpStatusData.iob}U\n"
        ret += "Reserv: ${to0Decimal(pumpStatusData.reservoirRemainingUnits)}U\n"
        ret += "Batt: ${pumpStatusData.batteryRemaining}\n"
        return ret
    }

    @Synchronized
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        return try {
            if (detailedBolusInfo.insulin == 0.0 && detailedBolusInfo.carbs == 0.0) {
                // neither carbs nor bolus requested
                aapsLogger.error("deliverTreatment: Invalid input")
                PumpEnactResult(injector).success(false).enacted(false).bolusDelivered(0.0).carbsDelivered(0.0)
                    .comment(R.string.invalid_input)
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                deliverBolus(detailedBolusInfo)
            } else {
                detailedBolusInfo.timestamp = System.currentTimeMillis()

                // no bolus required, carb only treatment
                pumpSyncStorage.addCarbs(PumpDbEntryCarbs(detailedBolusInfo, this))

                val bolusingEvent = EventOverviewBolusProgress
                bolusingEvent.t = EventOverviewBolusProgress.Treatment(0.0, detailedBolusInfo.carbs.toInt(), detailedBolusInfo.bolusType === DetailedBolusInfo.BolusType.SMB, detailedBolusInfo.id)
                bolusingEvent.percent = 100
                rxBus.send(bolusingEvent)
                aapsLogger.debug(LTag.PUMP, "deliverTreatment: Carb only treatment.")
                PumpEnactResult(injector).success(true).enacted(true).bolusDelivered(0.0)
                    .carbsDelivered(detailedBolusInfo.carbs).comment(R.string.common_resultok)
            }
        } finally {
            triggerUIChange()
        }
    }

    protected fun refreshCustomActionsList() {
        rxBus.send(EventCustomActionsChanged())
    }

    override fun manufacturer(): ManufacturerType = pumpType.manufacturer!!
    override fun model(): PumpType = pumpType
    override fun canHandleDST(): Boolean = false

    protected abstract fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult

    protected abstract fun triggerUIChange()

    private fun getOperationNotSupportedWithCustomText(resourceId: Int): PumpEnactResult =
        PumpEnactResult(injector).success(false).enacted(false).comment(resourceId)

    init {
        pumpDescription.fillFor(pumpType)
        this.pumpType = pumpType
    }
}
