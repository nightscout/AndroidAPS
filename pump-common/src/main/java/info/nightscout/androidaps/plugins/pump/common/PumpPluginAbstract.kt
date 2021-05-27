package info.nightscout.androidaps.plugins.pump.common

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.text.format.DateFormat
import com.google.gson.GsonBuilder
import dagger.android.HasAndroidInjector

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.events.EventCustomActionsChanged
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.plannedRemainingMinutes
import info.nightscout.androidaps.extensions.toStringFull
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.interfaces.PumpSync.TemporaryBasalType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter.to0Decimal
import info.nightscout.androidaps.utils.DecimalFormatter.to2Decimal
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by andy on 23.04.18.
 */
// When using this class, make sure that your first step is to create mConnection (see MedtronicPumpPlugin)
abstract class PumpPluginAbstract protected constructor(
    pluginDescription: PluginDescription?,
    pumpType: PumpType,
    injector: HasAndroidInjector?,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    commandQueue: CommandQueueProvider,
    var rxBus: RxBusWrapper,
    var activePlugin: ActivePlugin,
    var sp: SP,
    var context: Context,
    var fabricPrivacy: FabricPrivacy,
    var dateUtil: DateUtil,
    var aapsSchedulers: AapsSchedulers,
    var pumpSync: PumpSync,
    var pumpSyncStorage: info.nightscout.androidaps.plugins.pump.common.sync.PumpSyncStorage
) : PumpPluginBase(pluginDescription!!, injector!!, aapsLogger, resourceHelper, commandQueue), Pump, Constraints, info.nightscout.androidaps.plugins.pump.common.sync.PumpSyncEntriesCreator {

    private val disposable = CompositeDisposable()

    // Pump capabilities
    final override var pumpDescription = PumpDescription()
    //protected set

    @JvmField protected var serviceConnection: ServiceConnection? = null
    @JvmField protected var serviceRunning = false
    @JvmField protected var pumpState = PumpDriverState.NotInitialized
    @JvmField protected var displayConnectionMessages = false

    var pumpType: PumpType = PumpType.GENERIC_AAPS
        get() = field
        set(value) {
            field = value
            pumpDescription.fillFor(value)
        }

    protected var gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    abstract fun initPumpStatusData()

    override fun onStart() {
        super.onStart()
        initPumpStatusData()
        val intent = Intent(context, serviceClass)
        context.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
        serviceRunning = true
        disposable.add(rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ _ -> context.unbindService(serviceConnection!!) }) { throwable: Throwable? -> fabricPrivacy.logException(throwable!!) }
        )
        onStartCustomActions()
    }

    override fun onStop() {
        aapsLogger.debug(LTag.PUMP, deviceID() + " onStop()")
        context.unbindService(serviceConnection!!)
        serviceRunning = false
        disposable.clear()
        super.onStop()
    }

    /**
     * If we need to run any custom actions in onStart (triggering events, etc)
     */
    abstract fun onStartCustomActions()

    /**
     * Service class (same one you did serviceConnection for)
     *
     * @return Class
     */
    abstract val serviceClass: Class<*>?
    abstract val pumpStatusData: PumpStatus

    override fun isInitialized(): Boolean {
        return pumpState.isInitialized()
    }

    override fun isSuspended(): Boolean {
        return pumpState === PumpDriverState.Suspended
    }

    override fun isBusy(): Boolean {
        return pumpState === PumpDriverState.Busy
    }

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
            status.put("status", pumpStatusData.pumpStatusType.status)
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

        if (pumpStatusData.lastConnection == 0L) {
            ret += "LastConn: never\n"
        } else {
            val agoMsec = System.currentTimeMillis() - pumpStatusData.lastConnection
            val agoMin = (agoMsec / 60.0 / 1000.0).toInt()
            ret += "LastConn: $agoMin min ago\n"
        }

        if (pumpStatusData.lastBolusTime != null && pumpStatusData.lastBolusTime!!.time != 0L) {
            ret += """
                LastBolus: ${to2Decimal(pumpStatusData.lastBolusAmount!!)}U @${DateFormat.format("HH:mm", pumpStatusData.lastBolusTime)}
                
                """.trimIndent()
        }
        val activeTemp = pumpSync.expectedPumpState().temporaryBasal
        if (activeTemp != null) {
            ret += """
                Temp: ${activeTemp.toStringFull(dateUtil)}
                
                """.trimIndent()
        }
        val activeExtendedBolus = pumpSync.expectedPumpState().extendedBolus
        if (activeExtendedBolus != null) {
            ret += """
                Extended: ${activeExtendedBolus.toStringFull(dateUtil)}
                
                """.trimIndent()
        }
        // if (!veryShort) {
        // ret += "TDD: " + DecimalFormatter.to0Decimal(pumpStatus.dailyTotalUnits) + " / "
        // + pumpStatus.maxDailyTotalUnits + " U\n";
        // }
        ret += """
            IOB: ${pumpStatusData.iob}U
            
            """.trimIndent()
        ret += """
            Reserv: ${to0Decimal(pumpStatusData.reservoirRemainingUnits)}U
            
            """.trimIndent()
        ret += """
            Batt: ${pumpStatusData.batteryRemaining}
            
            """.trimIndent()
        return ret
    }

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        return try {
            if (detailedBolusInfo.insulin == 0.0 && detailedBolusInfo.carbs == 0.0) {
                // neither carbs nor bolus requested
                aapsLogger.error("deliverTreatment: Invalid input")
                PumpEnactResult(injector).success(false).enacted(false).bolusDelivered(0.0).carbsDelivered(0.0)
                    .comment(R.string.invalidinput)
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                deliverBolus(detailedBolusInfo)
            } else {
                detailedBolusInfo.timestamp = System.currentTimeMillis()

                // no bolus required, carb only treatment
                pumpSyncStorage.addCarbs(info.nightscout.androidaps.plugins.pump.common.sync.PumpDbEntryCarbs(detailedBolusInfo, this))

                val bolusingEvent = EventOverviewBolusProgress
                bolusingEvent.t = EventOverviewBolusProgress.Treatment(0.0, detailedBolusInfo.carbs.toInt(), detailedBolusInfo.bolusType === DetailedBolusInfo.BolusType.SMB)
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

    override fun manufacturer(): ManufacturerType {
        return pumpType.manufacturer!!
    }

    override fun model(): PumpType {
        return pumpType
    }

    override fun canHandleDST(): Boolean {
        return false
    }

    protected abstract fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult

    protected abstract fun triggerUIChange()

    private fun getOperationNotSupportedWithCustomText(resourceId: Int): PumpEnactResult {
        return PumpEnactResult(injector).success(false).enacted(false).comment(resourceId)
    }

    init {
        pumpDescription.fillFor(pumpType)
        this.pumpType = pumpType
    }
}