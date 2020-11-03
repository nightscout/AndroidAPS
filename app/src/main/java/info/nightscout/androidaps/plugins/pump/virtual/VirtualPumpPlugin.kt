package info.nightscout.androidaps.plugins.pump.virtual

import android.os.SystemClock
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.ExtendedBolus
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.virtual.events.EventVirtualPumpUpdateGui
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.InstanceId.instanceId
import info.nightscout.androidaps.utils.TimeChangeType
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class VirtualPumpPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private var fabricPrivacy: FabricPrivacy,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    private val treatmentsPlugin: TreatmentsPlugin,
    commandQueue: CommandQueueProvider,
    private val config: Config,
    private val dateUtil: DateUtil
) : PumpPluginBase(PluginDescription()
    .mainType(PluginType.PUMP)
    .fragmentClass(VirtualPumpFragment::class.java.name)
    .pluginName(R.string.virtualpump)
    .shortName(R.string.virtualpump_shortname)
    .preferencesId(R.xml.pref_virtualpump)
    .description(R.string.description_pump_virtual)
    .setDefault(),
    injector, aapsLogger, resourceHelper, commandQueue
), PumpInterface {


    private val disposable = CompositeDisposable()
    var batteryPercent = 50
    var reservoirInUnits = 50

    var pumpType: PumpType? = null
        private set
    private var lastDataTime: Long = 0
    private val pumpDescription = PumpDescription()

    init {
        pumpDescription.isBolusCapable = true
        pumpDescription.bolusStep = 0.1
        pumpDescription.isExtendedBolusCapable = true
        pumpDescription.extendedBolusStep = 0.05
        pumpDescription.extendedBolusDurationStep = 30.0
        pumpDescription.extendedBolusMaxDuration = 8 * 60.toDouble()
        pumpDescription.isTempBasalCapable = true
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT or PumpDescription.ABSOLUTE
        pumpDescription.maxTempPercent = 500
        pumpDescription.tempPercentStep = 10
        pumpDescription.tempDurationStep = 30
        pumpDescription.tempDurationStep15mAllowed = true
        pumpDescription.tempDurationStep30mAllowed = true
        pumpDescription.tempMaxDuration = 24 * 60
        pumpDescription.isSetBasalProfileCapable = true
        pumpDescription.basalStep = 0.01
        pumpDescription.basalMinimumRate = 0.01
        pumpDescription.isRefillingCapable = true
        pumpDescription.storesCarbInfo = false
        pumpDescription.is30minBasalRatesCapable = true
    }

    fun getFakingStatus(): Boolean {
        return sp.getBoolean(R.string.key_fromNSAreCommingFakedExtendedBoluses, false)
    }

    fun setFakingStatus(newStatus: Boolean) {
        sp.putBoolean(R.string.key_fromNSAreCommingFakedExtendedBoluses, newStatus)
    }

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventPreferenceChange -> if (event.isChanged(resourceHelper, R.string.key_virtualpump_type)) refreshConfiguration() }) { fabricPrivacy.logException(it) }
        refreshConfiguration()
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        val uploadStatus = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_virtualpump_uploadstatus)) as SwitchPreference?
            ?: return
        uploadStatus.isVisible = !config.NSCLIENT
    }

    override fun isFakingTempsByExtendedBoluses(): Boolean {
        return config.NSCLIENT && getFakingStatus()
    }

    override fun loadTDDs(): PumpEnactResult { //no result, could read DB in the future?
        return PumpEnactResult(injector)
    }

    override fun getCustomActions(): List<CustomAction>? {
        return null
    }

    override fun executeCustomAction(customActionType: CustomActionType) {}

    override fun executeCustomCommand(customCommand: CustomCommand?): PumpEnactResult? {
        return null
    }

    override fun isInitialized(): Boolean {
        return true
    }

    override fun isSuspended(): Boolean {
        return false
    }

    override fun isBusy(): Boolean {
        return false
    }

    override fun isConnected(): Boolean {
        return true
    }

    override fun isConnecting(): Boolean {
        return false
    }

    override fun isHandshakeInProgress(): Boolean {
        return false
    }

    override fun finishHandshaking() {}
    override fun connect(reason: String) {
        //if (!Config.NSCLIENT) NSUpload.uploadDeviceStatus()
        lastDataTime = System.currentTimeMillis()
    }

    override fun disconnect(reason: String) {}
    override fun stopConnecting() {}
    override fun getPumpStatus() {
        lastDataTime = System.currentTimeMillis()
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        lastDataTime = System.currentTimeMillis()
        // Do nothing here. we are using ConfigBuilderPlugin.getPlugin().getActiveProfile().getProfile();
        val result = PumpEnactResult(injector)
        result.success = true
        val notification = Notification(Notification.PROFILE_SET_OK, resourceHelper.gs(R.string.profile_set_ok), Notification.INFO, 60)
        rxBus.send(EventNewNotification(notification))
        return result
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        return true
    }

    override fun lastDataTime(): Long {
        return lastDataTime
    }

    override fun getBaseBasalRate(): Double {
        return profileFunction.getProfile()?.basal ?: 0.0
    }

    override fun getReservoirLevel(): Double {
        return reservoirInUnits.toDouble()
    }

    override fun getBatteryLevel(): Int {
        return batteryPercent
    }

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = true
        result.bolusDelivered = detailedBolusInfo.insulin
        result.carbsDelivered = detailedBolusInfo.carbs
        result.enacted = result.bolusDelivered > 0 || result.carbsDelivered > 0
        result.comment = resourceHelper.gs(R.string.virtualpump_resultok)
        var delivering = 0.0
        while (delivering < detailedBolusInfo.insulin) {
            SystemClock.sleep(200)
            val bolusingEvent = EventOverviewBolusProgress
            bolusingEvent.status = resourceHelper.gs(R.string.bolusdelivering, delivering)
            bolusingEvent.percent = min((delivering / detailedBolusInfo.insulin * 100).toInt(), 100)
            rxBus.send(bolusingEvent)
            delivering += 0.1
        }
        SystemClock.sleep(200)
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.status = resourceHelper.gs(R.string.bolusdelivered, detailedBolusInfo.insulin)
        bolusingEvent.percent = 100
        rxBus.send(bolusingEvent)
        SystemClock.sleep(1000)
        aapsLogger.debug(LTag.PUMP, "Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result)
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        treatmentsPlugin.addToHistoryTreatment(detailedBolusInfo, false)
        return result
    }

    override fun stopBolusDelivering() {}
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean): PumpEnactResult {
        val tempBasal = TemporaryBasal(injector)
            .date(System.currentTimeMillis())
            .absolute(absoluteRate)
            .duration(durationInMinutes)
            .source(Source.USER)
        val result = PumpEnactResult(injector)
        result.success = true
        result.enacted = true
        result.isTempCancel = false
        result.absolute = absoluteRate
        result.duration = durationInMinutes
        result.comment = resourceHelper.gs(R.string.virtualpump_resultok)
        treatmentsPlugin.addToHistoryTempBasal(tempBasal)
        aapsLogger.debug(LTag.PUMP, "Setting temp basal absolute: $result")
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean): PumpEnactResult {
        val tempBasal = TemporaryBasal(injector)
            .date(System.currentTimeMillis())
            .percent(percent)
            .duration(durationInMinutes)
            .source(Source.USER)
        val result = PumpEnactResult(injector)
        result.success = true
        result.enacted = true
        result.percent = percent
        result.isPercent = true
        result.isTempCancel = false
        result.duration = durationInMinutes
        result.comment = resourceHelper.gs(R.string.virtualpump_resultok)
        treatmentsPlugin.addToHistoryTempBasal(tempBasal)
        aapsLogger.debug(LTag.PUMP, "Settings temp basal percent: $result")
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        val result = cancelExtendedBolus()
        if (!result.success) return result
        val extendedBolus = ExtendedBolus(injector)
            .date(System.currentTimeMillis())
            .insulin(insulin)
            .durationInMinutes(durationInMinutes)
            .source(Source.USER)
        result.success = true
        result.enacted = true
        result.bolusDelivered = insulin
        result.isTempCancel = false
        result.duration = durationInMinutes
        result.comment = resourceHelper.gs(R.string.virtualpump_resultok)
        treatmentsPlugin.addToHistoryExtendedBolus(extendedBolus)
        aapsLogger.debug(LTag.PUMP, "Setting extended bolus: $result")
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun cancelTempBasal(force: Boolean): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = true
        result.isTempCancel = true
        result.comment = resourceHelper.gs(R.string.virtualpump_resultok)
        if (treatmentsPlugin.isTempBasalInProgress) {
            result.enacted = true
            val tempStop = TemporaryBasal(injector).date(System.currentTimeMillis()).source(Source.USER)
            treatmentsPlugin.addToHistoryTempBasal(tempStop)
            //tempBasal = null;
            aapsLogger.debug(LTag.PUMP, "Canceling temp basal: $result")
            rxBus.send(EventVirtualPumpUpdateGui())
        }
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        val result = PumpEnactResult(injector)
        if (treatmentsPlugin.isInHistoryExtendedBoluslInProgress) {
            val exStop = ExtendedBolus(injector, System.currentTimeMillis())
            exStop.source = Source.USER
            treatmentsPlugin.addToHistoryExtendedBolus(exStop)
        }
        result.success = true
        result.enacted = true
        result.isTempCancel = true
        result.comment = resourceHelper.gs(R.string.virtualpump_resultok)
        aapsLogger.debug(LTag.PUMP, "Canceling extended bolus: $result")
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        val now = System.currentTimeMillis()
        if (!sp.getBoolean(R.string.key_virtualpump_uploadstatus, false)) {
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
            val tb = treatmentsPlugin.getTempBasalFromHistory(now)
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(now, profile))
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.date))
                extended.put("TempBasalRemaining", tb.plannedRemainingMinutes)
            }
            val eb = treatmentsPlugin.getExtendedBolusFromHistory(now)
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate())
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.date))
                extended.put("ExtendedBolusRemaining", eb.plannedRemainingMinutes)
            }
            status.put("timestamp", DateUtil.toISOString(now))
            pump.put("battery", battery)
            pump.put("status", status)
            pump.put("extended", extended)
            pump.put("reservoir", reservoirInUnits)
            pump.put("clock", DateUtil.toISOString(now))
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return pump
    }

    override fun manufacturer(): ManufacturerType {
        return pumpDescription.pumpType.manufacturer
    }

    override fun model(): PumpType {
        return pumpDescription.pumpType
    }

    override fun serialNumber(): String {
        return instanceId()
    }

    override fun getPumpDescription(): PumpDescription {
        return pumpDescription
    }

    override fun shortStatus(veryShort: Boolean): String {
        return "Virtual Pump"
    }

    override fun canHandleDST(): Boolean {
        return true
    }

    fun refreshConfiguration() {
        val pumptype = sp.getString(R.string.key_virtualpump_type, "Generic AAPS")
        val pumpTypeNew = PumpType.getByDescription(pumptype)
        aapsLogger.debug(LTag.PUMP, "Pump in configuration: $pumptype, PumpType object: $pumpTypeNew")
        if (pumpType == pumpTypeNew) return
        aapsLogger.debug(LTag.PUMP, "New pump configuration found ($pumpTypeNew), changing from previous ($pumpType)")
        pumpDescription.setPumpDescription(pumpTypeNew)
        pumpType = pumpTypeNew
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType?) {}

}
