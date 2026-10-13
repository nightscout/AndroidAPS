package app.aaps.pump.virtual

import android.content.Context
import android.os.SystemClock
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.fabric.InstanceId
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.virtual.events.EventVirtualPumpUpdateGui
import app.aaps.pump.virtual.extensions.toText
import app.aaps.pump.virtual.keys.VirtualBooleanNonPreferenceKey
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
open class VirtualPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private var fabricPrivacy: FabricPrivacy,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    preferences: Preferences,
    private val profileFunction: ProfileFunction,
    commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val persistenceLayer: PersistenceLayer,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(VirtualPumpFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_virtual_pump)
        .pluginName(app.aaps.core.ui.R.string.virtual_pump)
        .shortName(R.string.virtual_pump_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_pump_virtual)
        .setDefault()
        .neverVisible(config.AAPSCLIENT),
    ownPreferences = listOf(VirtualBooleanNonPreferenceKey::class.java),
    aapsLogger, rh, preferences, commandQueue
), Pump, VirtualPump {

    private val disposable = CompositeDisposable()
    var batteryPercent = 50
    var reservoirInUnits = 50

    var pumpType: PumpType? = null
        private set
    override var lastDataTime: Long = 0
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
            .subscribe({ event: EventPreferenceChange -> if (event.isChanged(StringKey.VirtualPumpType.key)) refreshConfiguration() }, fabricPrivacy::logException)
        refreshConfiguration()
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = config.AAPSCLIENT && fakeDataDetected
    override var fakeDataDetected = false

    override fun loadTDDs(): PumpEnactResult { //no result, could read DB in the future?
        return pumpEnactResultProvider.get()
    }

    override fun isInitialized(): Boolean = true
    override fun isSuspended(): Boolean = preferences.get(VirtualBooleanNonPreferenceKey.IsSuspended)
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
        rxBus.send(EventNewNotification(Notification(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)))
        // Do nothing here. we are using database profile
        return pumpEnactResultProvider.get().success(true).enacted(true)
    }

    override fun isThisProfileSet(profile: Profile): Boolean = pumpSync.expectedPumpState().profile?.isEqual(profile) == true

    override val lastBolusTime: Long? get() = pumpSync.expectedPumpState().bolus?.timestamp
    override val lastBolusAmount: Double? get() = pumpSync.expectedPumpState().bolus?.amount
    override val baseBasalRate: Double get() = profileFunction.getProfile()?.getBasal() ?: 0.0

    override val reservoirLevel: Double
        get() =
            if (config.AAPSCLIENT) processedDeviceStatusData.pumpData?.reservoir ?: -1.0
            else reservoirInUnits.toDouble()

    override val batteryLevel: Int? get() = batteryPercent

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        val result = pumpEnactResultProvider.get()
            .success(true)
            .bolusDelivered(detailedBolusInfo.insulin)
            .enacted(detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0)
            .comment(rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok))
        var delivering = 0.0
        while (delivering < detailedBolusInfo.insulin) {
            SystemClock.sleep(200)
            rxBus.send(EventOverviewBolusProgress(rh, delivering, id = detailedBolusInfo.id))
            delivering += 0.1
            if (BolusProgressData.stopPressed)
                return pumpEnactResultProvider.get()
                    .success(false)
                    .enacted(false)
                    .comment(rh.gs(app.aaps.core.ui.R.string.stop))
        }
        SystemClock.sleep(200)
        rxBus.send(EventOverviewBolusProgress(rh, percent = 100, id = detailedBolusInfo.id))
        SystemClock.sleep(1000)
        aapsLogger.debug(LTag.PUMP, "Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result)
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        if (detailedBolusInfo.insulin > 0) {
            if (config.AAPSCLIENT) // do not store pump serial (record will not be marked PH)
                disposable += persistenceLayer.insertOrUpdateBolus(
                    bolus = detailedBolusInfo.createBolus(),
                    action = Action.BOLUS,
                    source = Sources.Pump
                ).subscribe()
            else
                pumpSync.syncBolusWithPumpId(
                    timestamp = detailedBolusInfo.timestamp,
                    amount = detailedBolusInfo.insulin,
                    type = detailedBolusInfo.bolusType,
                    pumpId = dateUtil.now(),
                    pumpType = pumpType ?: PumpType.GENERIC_AAPS,
                    pumpSerial = serialNumber()
                )
        }
        return result
    }

    override fun stopBolusDelivering() {}
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        result.success = true
        result.enacted = true
        result.isTempCancel = false
        result.absolute = absoluteRate
        result.duration = durationInMinutes
        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
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
        val result = pumpEnactResultProvider.get()
        result.success = true
        result.enacted = true
        result.percent = percent
        result.isPercent = true
        result.isTempCancel = false
        result.duration = durationInMinutes
        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
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
        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
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
        val result = pumpEnactResultProvider.get()
        result.success = true
        result.isTempCancel = true
        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
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
        val result = pumpEnactResultProvider.get()
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
        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
        aapsLogger.debug(LTag.PUMP, "Canceling extended bolus: ${result.toText(rh)}")
        rxBus.send(EventVirtualPumpUpdateGui())
        lastDataTime = System.currentTimeMillis()
        return result
    }

    override fun manufacturer(): ManufacturerType = pumpDescription.pumpType.manufacturer()
    override fun model(): PumpType = pumpDescription.pumpType
    override fun serialNumber(): String = InstanceId.instanceId
    override fun canHandleDST(): Boolean = true

    fun refreshConfiguration() {
        val pumpType = preferences.get(StringKey.VirtualPumpType)
        val pumpTypeNew = PumpType.getByDescription(pumpType)
        aapsLogger.debug(LTag.PUMP, "Pump in configuration: $pumpType, PumpType object: $pumpTypeNew")
        if (this.pumpType == pumpTypeNew) return
        aapsLogger.debug(LTag.PUMP, "New pump configuration found ($pumpTypeNew), changing from previous (${this.pumpType})")
        pumpDescription.fillFor(pumpTypeNew)
        this.pumpType = pumpTypeNew
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {}

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return
        val entries = mutableListOf<CharSequence>()
            .also { entries ->
                PumpType.entries.forEach {
                    if (it.description != "USER") entries.add(it.description)
                }
            }
            .sortedWith(compareBy { it.toString() })
            .toTypedArray()
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "virtual_pump_settings"
            title = rh.gs(R.string.virtualpump_settings)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveListPreference(ctx = context, stringKey = StringKey.VirtualPumpType, title = R.string.virtual_pump_type, entries = entries, entryValues = entries))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.VirtualPumpStatusUpload, title = app.aaps.core.ui.R.string.virtualpump_uploadstatus_title))
        }
    }
}
