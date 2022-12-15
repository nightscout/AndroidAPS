package info.nightscout.pump.virtual

import android.os.SystemClock
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.core.events.EventNewNotification
import info.nightscout.core.extensions.convertedToAbsolute
import info.nightscout.core.extensions.plannedRemainingMinutes
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.core.utils.fabric.InstanceId
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.nsclient.ProcessedDeviceStatusData
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.BolusProgressData
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpPluginBase
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.VirtualPump
import info.nightscout.interfaces.pump.defs.ManufacturerType
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.utils.TimeChangeType
import info.nightscout.pump.virtual.events.EventVirtualPumpUpdateGui
import info.nightscout.pump.virtual.extensions.toText
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventOverviewBolusProgress
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
open class VirtualPumpPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private var fabricPrivacy: FabricPrivacy,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    private val iobCobCalculator: IobCobCalculator,
    commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val processedDeviceStatusData: ProcessedDeviceStatusData
) : PumpPluginBase(
    PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(VirtualPumpFragment::class.java.name)
        .pluginIcon(info.nightscout.core.main.R.drawable.ic_virtual_pump)
        .pluginName(info.nightscout.core.ui.R.string.virtual_pump)
        .shortName(R.string.virtual_pump_shortname)
        .preferencesId(R.xml.pref_virtual_pump)
        .description(R.string.description_pump_virtual)
        .setDefault()
        .neverVisible(config.NSCLIENT),
    injector, aapsLogger, rh, commandQueue
), Pump, VirtualPump {

    private val disposable = CompositeDisposable()
    var batteryPercent = 50
    var reservoirInUnits = 50

    var pumpType: PumpType? = null
        private set
    private var lastDataTime: Long = 0
    override val pumpDescription = PumpDescription().also {
        it.isBolusCapable = true
        it.bolusStep = 0.1
        it.isExtendedBolusCapable = true
        it.extendedBolusStep = 0.05
        it.extendedBolusDurationStep = 30.0
        it.extendedBolusMaxDuration = 8 * 60.0
        it.isTempBasalCapable = true
        it.tempBasalStyle = PumpDescription.PERCENT or PumpDescription.ABSOLUTE
        it.maxTempPercent = 500
        it.tempPercentStep = 10
        it.tempDurationStep = 30
        it.tempDurationStep15mAllowed = true
        it.tempDurationStep30mAllowed = true
        it.tempMaxDuration = 24 * 60
        it.isSetBasalProfileCapable = true
        it.basalStep = 0.01
        it.basalMinimumRate = 0.01
        it.isRefillingCapable = true
        //it.storesCarbInfo = false
        it.is30minBasalRatesCapable = true
    }

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventPreferenceChange -> if (event.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_virtualpump_type))) refreshConfiguration() }, fabricPrivacy::logException)
        refreshConfiguration()
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        val uploadStatus = preferenceFragment.findPreference(rh.gs(info.nightscout.core.utils.R.string.key_virtual_pump_upload_status)) as SwitchPreference?
            ?: return
        uploadStatus.isVisible = !config.NSCLIENT
    }

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = config.NSCLIENT && fakeDataDetected
    override var fakeDataDetected = false

    override fun loadTDDs(): PumpEnactResult { //no result, could read DB in the future?
        return PumpEnactResult(injector)
    }

    override fun isInitialized(): Boolean = true
    override fun isSuspended(): Boolean = false
    override fun isBusy(): Boolean = false
    override fun isConnected(): Boolean = true
    override fun isConnecting(): Boolean = false
    override fun isHandshakeInProgress(): Boolean = false

    override fun connect(reason: String) {
        lastDataTime = System.currentTimeMillis()
    }

    override fun waitForDisconnectionInSeconds(): Int = 0
    override fun disconnect(reason: String) {}
    override fun stopConnecting() {}
    override fun getPumpStatus(reason: String) {
        lastDataTime = System.currentTimeMillis()
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        lastDataTime = System.currentTimeMillis()
        rxBus.send(EventNewNotification(Notification(Notification.PROFILE_SET_OK, rh.gs(info.nightscout.core.ui.R.string.profile_set_ok), Notification.INFO, 60)))
        // Do nothing here. we are using database profile
        return PumpEnactResult(injector).success(true).enacted(true)
    }

    override fun isThisProfileSet(profile: Profile): Boolean = pumpSync.expectedPumpState().profile?.isEqual(profile) ?: false

    override fun lastDataTime(): Long = lastDataTime

    override val baseBasalRate: Double
        get() = profileFunction.getProfile()?.getBasal() ?: 0.0

    override val reservoirLevel: Double
        get() =
            if (config.NSCLIENT) processedDeviceStatusData.pumpData?.reservoir ?: -1.0
            else reservoirInUnits.toDouble()

    override val batteryLevel: Int
        get() = batteryPercent

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        val result = PumpEnactResult(injector)
            .success(true)
            .bolusDelivered(detailedBolusInfo.insulin)
            .enacted(detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0)
            .comment(rh.gs(info.nightscout.core.ui.R.string.virtualpump_resultok))
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.t = EventOverviewBolusProgress.Treatment(0.0, 0, detailedBolusInfo.bolusType == DetailedBolusInfo.BolusType.SMB, detailedBolusInfo.id)
        var delivering = 0.0
        while (delivering < detailedBolusInfo.insulin) {
            SystemClock.sleep(200)
            bolusingEvent.status = rh.gs(info.nightscout.core.ui.R.string.bolus_delivering, delivering)
            bolusingEvent.percent = min((delivering / detailedBolusInfo.insulin * 100).toInt(), 100)
            rxBus.send(bolusingEvent)
            delivering += 0.1
            if (BolusProgressData.stopPressed)
                return PumpEnactResult(injector)
                    .success(false)
                    .enacted(false)
                    .comment(rh.gs(info.nightscout.core.ui.R.string.stop))
        }
        SystemClock.sleep(200)
        bolusingEvent.status = rh.gs(info.nightscout.core.ui.R.string.bolus_delivered_successfully, detailedBolusInfo.insulin)
        bolusingEvent.percent = 100
        rxBus.send(bolusingEvent)
        SystemClock.sleep(1000)
        aapsLogger.debug(LTag.PUMP, "Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result)
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        if (detailedBolusInfo.insulin > 0)
            pumpSync.syncBolusWithPumpId(
                timestamp = detailedBolusInfo.timestamp,
                amount = detailedBolusInfo.insulin,
                type = detailedBolusInfo.bolusType,
                pumpId = dateUtil.now(),
                pumpType = pumpType ?: PumpType.GENERIC_AAPS,
                pumpSerial = serialNumber()
            )
        if (detailedBolusInfo.carbs > 0)
            pumpSync.syncCarbsWithTimestamp(
                timestamp = detailedBolusInfo.carbsTimestamp ?: detailedBolusInfo.timestamp,
                amount = detailedBolusInfo.carbs,
                pumpId = null,
                pumpType = pumpType ?: PumpType.GENERIC_AAPS,
                pumpSerial = serialNumber()
            )
        return result
    }

    override fun stopBolusDelivering() {}
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = true
        result.enacted = true
        result.isTempCancel = false
        result.absolute = absoluteRate
        result.duration = durationInMinutes
        result.comment = rh.gs(info.nightscout.core.ui.R.string.virtualpump_resultok)
        pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = dateUtil.now(),
            rate = absoluteRate,
            duration = T.mins(durationInMinutes.toLong()).msecs(),
            isAbsolute = true,
            type = tbrType,
            pumpId = dateUtil.now(),
            pumpType = pumpType ?: PumpType.GENERIC_AAPS,
            pumpSerial = serialNumber()
        )
        aapsLogger.debug(LTag.PUMP, "Setting temp basal absolute: ${result.toText(rh)}")
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = true
        result.enacted = true
        result.percent = percent
        result.isPercent = true
        result.isTempCancel = false
        result.duration = durationInMinutes
        result.comment = rh.gs(info.nightscout.core.ui.R.string.virtualpump_resultok)
        pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = dateUtil.now(),
            rate = percent.toDouble(),
            duration = T.mins(durationInMinutes.toLong()).msecs(),
            isAbsolute = false,
            type = tbrType,
            pumpId = dateUtil.now(),
            pumpType = pumpType ?: PumpType.GENERIC_AAPS,
            pumpSerial = serialNumber()
        )
        aapsLogger.debug(LTag.PUMP, "Settings temp basal percent: ${result.toText(rh)}")
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        val result = cancelExtendedBolus()
        if (!result.success) return result
        result.success = true
        result.enacted = true
        result.bolusDelivered = insulin
        result.isTempCancel = false
        result.duration = durationInMinutes
        result.comment = rh.gs(info.nightscout.core.ui.R.string.virtualpump_resultok)
        pumpSync.syncExtendedBolusWithPumpId(
            timestamp = dateUtil.now(),
            amount = insulin,
            duration = T.mins(durationInMinutes.toLong()).msecs(),
            isEmulatingTB = false,
            pumpId = dateUtil.now(),
            pumpType = pumpType ?: PumpType.GENERIC_AAPS,
            pumpSerial = serialNumber()
        )
        aapsLogger.debug(LTag.PUMP, "Setting extended bolus: ${result.toText(rh)}")
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = true
        result.isTempCancel = true
        result.comment = rh.gs(info.nightscout.core.ui.R.string.virtualpump_resultok)
        if (pumpSync.expectedPumpState().temporaryBasal != null) {
            result.enacted = true
            pumpSync.syncStopTemporaryBasalWithPumpId(
                timestamp = dateUtil.now(),
                endPumpId = dateUtil.now(),
                pumpType = pumpType ?: PumpType.GENERIC_AAPS,
                pumpSerial = serialNumber()
            )
            aapsLogger.debug(LTag.PUMP, "Canceling temp basal: ${result.toText(rh)}")
            rxBus.send(EventVirtualPumpUpdateGui())
        }
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        val result = PumpEnactResult(injector)
        if (pumpSync.expectedPumpState().extendedBolus != null) {
            pumpSync.syncStopExtendedBolusWithPumpId(
                timestamp = dateUtil.now(),
                endPumpId = dateUtil.now(),
                pumpType = pumpType ?: PumpType.GENERIC_AAPS,
                pumpSerial = serialNumber()
            )
        }
        result.success = true
        result.enacted = true
        result.isTempCancel = true
        result.comment = rh.gs(info.nightscout.core.ui.R.string.virtualpump_resultok)
        aapsLogger.debug(LTag.PUMP, "Canceling extended bolus: ${result.toText(rh)}")
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        val now = System.currentTimeMillis()
        if (!sp.getBoolean(info.nightscout.core.utils.R.string.key_virtual_pump_upload_status, false)) {
            return JSONObject()
        }
        val pump = JSONObject()
        val battery = JSONObject()
        val status = JSONObject()
        val extended = JSONObject()
        try {
            battery.put("percent", batteryPercent)
            status.put("status", "normal")
            extended.put("Version", version)
            try {
                extended.put("ActiveProfile", profileName)
            } catch (ignored: Exception) {
            }
            val tb = iobCobCalculator.getTempBasal(now)
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.convertedToAbsolute(now, profile))
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.timestamp))
                extended.put("TempBasalRemaining", tb.plannedRemainingMinutes)
            }
            val eb = iobCobCalculator.getExtendedBolus(now)
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.rate)
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.timestamp))
                extended.put("ExtendedBolusRemaining", eb.plannedRemainingMinutes)
            }
            status.put("timestamp", dateUtil.toISOString(now))
            pump.put("battery", battery)
            pump.put("status", status)
            pump.put("extended", extended)
            pump.put("reservoir", reservoirInUnits)
            pump.put("clock", dateUtil.toISOString(now))
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return pump
    }

    override fun manufacturer(): ManufacturerType = pumpDescription.pumpType.manufacturer ?: ManufacturerType.AAPS

    override fun model(): PumpType = pumpDescription.pumpType

    override fun serialNumber(): String = InstanceId.instanceId

    override fun shortStatus(veryShort: Boolean): String = "Virtual Pump"

    override fun canHandleDST(): Boolean = true

    fun refreshConfiguration() {
        val pumpType = sp.getString(info.nightscout.core.utils.R.string.key_virtualpump_type, PumpType.GENERIC_AAPS.description)
        val pumpTypeNew = PumpType.getByDescription(pumpType)
        aapsLogger.debug(LTag.PUMP, "Pump in configuration: $pumpType, PumpType object: $pumpTypeNew")
        if (this.pumpType == pumpTypeNew) return
        aapsLogger.debug(LTag.PUMP, "New pump configuration found ($pumpTypeNew), changing from previous (${this.pumpType})")
        pumpDescription.fillFor(pumpTypeNew)
        this.pumpType = pumpTypeNew
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {}
}
