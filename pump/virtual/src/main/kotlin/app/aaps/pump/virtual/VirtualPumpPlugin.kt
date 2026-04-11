package app.aaps.pump.virtual

import android.os.SystemClock
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
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.withEntries
import app.aaps.core.ui.compose.icons.IcPluginVirtualPump
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.utils.fabric.InstanceId
import app.aaps.pump.virtual.extensions.toText
import app.aaps.pump.virtual.keys.VirtualBooleanNonPreferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.min

@Singleton
open class VirtualPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val notificationManager: NotificationManager,
    private val ch: ConcentrationHelper,
    private val insulin: Insulin,
    private val bolusProgressData: BolusProgressData,
    @ApplicationScope private val appScope: CoroutineScope
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { plugin ->
            VirtualPumpComposeContent(
                virtualPumpPlugin = plugin as VirtualPumpPlugin,
                rh = rh,
                pumpSync = pumpSync,
                dateUtil = dateUtil,
                persistenceLayer = persistenceLayer,
                preferences = preferences,
                ch = ch,
                rxBus = rxBus,
                commandQueue = commandQueue
            )
        }
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_virtual_pump)
        .icon(IcPluginVirtualPump)
        .pluginName(app.aaps.core.ui.R.string.virtual_pump)
        .shortName(R.string.virtual_pump_shortname)
        .description(R.string.description_pump_virtual)
        .setDefault()
        .neverVisible(config.AAPSCLIENT),
    ownPreferences = listOf(VirtualBooleanNonPreferenceKey::class.java),
    aapsLogger, rh, preferences, commandQueue
), Pump, VirtualPump {

    private var scope: CoroutineScope? = null

    val batteryPercentFlow: StateFlow<Int>
        field = MutableStateFlow(50)

    val reservoirInUnitsFlow: StateFlow<Int>
        field = MutableStateFlow(50)

    val pumpTypeFlow: StateFlow<PumpType?>
        field = MutableStateFlow(null)

    private val _lastDataTime = MutableStateFlow(0L)
    override val lastDataTime: StateFlow<Long> = _lastDataTime

    private val _lastBolusTime = MutableStateFlow<Long?>(null)
    override val lastBolusTime: StateFlow<Long?> = _lastBolusTime

    private val _lastBolusAmount = MutableStateFlow<PumpInsulin?>(null)
    override val lastBolusAmount: StateFlow<PumpInsulin?> = _lastBolusAmount

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

    override fun requiredPermissions(): List<PermissionGroup> = emptyList()

    override fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        preferences.observe(StringKey.VirtualPumpType).drop(1).onEach { refreshConfiguration() }.launchIn(newScope)
        batteryPercentFlow.onEach { _batteryLevel.value = it }.launchIn(newScope)
        reservoirInUnitsFlow.onEach { _reservoirLevel.value = PumpInsulin(it.toDouble()) }.launchIn(newScope)
        refreshConfiguration()
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
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
        _lastDataTime.value = System.currentTimeMillis()
    }

    override fun waitForDisconnectionInSeconds(): Int = 0
    override fun disconnect(reason: String) {}
    override fun stopConnecting() {}
    override fun getPumpStatus(reason: String) {
        _lastDataTime.value = System.currentTimeMillis()
    }

    override fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        _lastDataTime.value = System.currentTimeMillis()
        notificationManager.post(NotificationId.PROFILE_SET_OK, app.aaps.core.ui.R.string.profile_set_ok, validMinutes = 60)
        // Do nothing here. we are using database profile
        return pumpEnactResultProvider.get().success(true).enacted(true)
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean = runBlocking { pumpSync.expectedPumpState() }.profile?.isEqual(profile) == true

    private val _reservoirLevel = MutableStateFlow(PumpInsulin(0.0))
    override val reservoirLevel: StateFlow<PumpInsulin> = _reservoirLevel

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    override val batteryLevel: StateFlow<Int?> = _batteryLevel

    override val baseBasalRate: PumpRate
        get() = PumpRate(runBlocking { pumpSync.expectedPumpState() }.profile?.getBasal() ?: 0.0)

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        val result = pumpEnactResultProvider.get()
            .success(true)
            .bolusDelivered(detailedBolusInfo.insulin)
            .enacted(detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0)
            .comment(rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok))
        val isPriming = bolusProgressData.state.value?.isPriming ?: false
        val totalInsulin = bolusProgressData.state.value?.insulin ?: detailedBolusInfo.insulin
        var delivering = 0.0
        while (delivering < detailedBolusInfo.insulin) {
            SystemClock.sleep(200)
            val pumpInsulin = PumpInsulin(delivering)
            val percent = min((ch.fromPump(pumpInsulin, isPriming) / totalInsulin * 100).toInt(), 100)
            bolusProgressData.updateProgress(percent, ch.bolusProgressString(pumpInsulin, isPriming), delivering)
            delivering += 0.1
            if (bolusProgressData.isStopPressed)
                return pumpEnactResultProvider.get()
                    .success(false)
                    .enacted(false)
                    .comment(rh.gs(app.aaps.core.ui.R.string.stop))
        }
        SystemClock.sleep(200)
        bolusProgressData.updateProgress(100, rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_successfully, totalInsulin), detailedBolusInfo.insulin)
        SystemClock.sleep(1000)
        aapsLogger.debug(LTag.PUMP, "Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result)
        _lastDataTime.value = System.currentTimeMillis()
        if (detailedBolusInfo.insulin > 0) {
            if (config.AAPSCLIENT) // do not store pump serial (record will not be marked PH)
                appScope.launch {
                    persistenceLayer.insertOrUpdateBolus(
                        bolus = detailedBolusInfo.createBolus(insulin.iCfg),
                        action = Action.BOLUS,
                        source = Sources.Pump
                    )
                }
            else
                runBlocking {
                    pumpSync.syncBolusWithPumpId(
                        timestamp = detailedBolusInfo.timestamp,
                        amount = PumpInsulin(detailedBolusInfo.insulin),
                        type = detailedBolusInfo.bolusType,
                        pumpId = dateUtil.now(),
                        pumpType = pumpTypeFlow.value ?: PumpType.GENERIC_AAPS,
                        pumpSerial = serialNumber()
                    )
                }
        }
        if (detailedBolusInfo.insulin > 0) {
            _lastBolusTime.value = detailedBolusInfo.timestamp
            _lastBolusAmount.value = PumpInsulin(detailedBolusInfo.insulin)
        }
        return result
    }

    override fun stopBolusDelivering() {}
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        result.success = true
        result.enacted = true
        result.isTempCancel = false
        result.absolute = absoluteRate
        result.duration = durationInMinutes
        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
        runBlocking {
            pumpSync.syncTemporaryBasalWithPumpId(
                timestamp = dateUtil.now(),
                rate = PumpRate(absoluteRate),
                duration = T.mins(durationInMinutes.toLong()).msecs(),
                isAbsolute = true,
                type = tbrType,
                pumpId = dateUtil.now(),
                pumpType = pumpTypeFlow.value ?: PumpType.GENERIC_AAPS,
                pumpSerial = serialNumber()
            )
        }
        aapsLogger.debug(LTag.PUMP, "Setting temp basal absolute: ${result.toText(rh)}")
        _lastDataTime.value = System.currentTimeMillis()
        return result
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        result.success = true
        result.enacted = true
        result.percent = percent
        result.isPercent = true
        result.isTempCancel = false
        result.duration = durationInMinutes
        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
        runBlocking {
            pumpSync.syncTemporaryBasalWithPumpId(
                timestamp = dateUtil.now(),
                rate = PumpRate(percent.toDouble()),
                duration = T.mins(durationInMinutes.toLong()).msecs(),
                isAbsolute = false,
                type = tbrType,
                pumpId = dateUtil.now(),
                pumpType = pumpTypeFlow.value ?: PumpType.GENERIC_AAPS,
                pumpSerial = serialNumber()
            )
        }
        aapsLogger.debug(LTag.PUMP, "Settings temp basal percent: ${result.toText(rh)}")
        _lastDataTime.value = System.currentTimeMillis()
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
        runBlocking {
            pumpSync.syncExtendedBolusWithPumpId(
                timestamp = dateUtil.now(),
                rate = PumpRate(insulin),
                duration = T.mins(durationInMinutes.toLong()).msecs(),
                isEmulatingTB = false,
                pumpId = dateUtil.now(),
                pumpType = pumpTypeFlow.value ?: PumpType.GENERIC_AAPS,
                pumpSerial = serialNumber()
            )
        }
        aapsLogger.debug(LTag.PUMP, "Setting extended bolus: ${result.toText(rh)}")
        _lastDataTime.value = System.currentTimeMillis()
        return result
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        result.success = true
        result.isTempCancel = true
        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
        if (runBlocking { pumpSync.expectedPumpState() }.temporaryBasal != null) {
            result.enacted = true
            runBlocking {
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    timestamp = dateUtil.now(),
                    endPumpId = dateUtil.now(),
                    pumpType = pumpTypeFlow.value ?: PumpType.GENERIC_AAPS,
                    pumpSerial = serialNumber()
                )
            }
            aapsLogger.debug(LTag.PUMP, "Canceling temp basal: ${result.toText(rh)}")
        }
        _lastDataTime.value = System.currentTimeMillis()
        return result
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (runBlocking { pumpSync.expectedPumpState() }.extendedBolus != null) {
            runBlocking {
                pumpSync.syncStopExtendedBolusWithPumpId(
                    timestamp = dateUtil.now(),
                    endPumpId = dateUtil.now(),
                    pumpType = pumpTypeFlow.value ?: PumpType.GENERIC_AAPS,
                    pumpSerial = serialNumber()
                )
            }
        }
        result.success = true
        result.enacted = true
        result.isTempCancel = true
        result.comment = rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)
        aapsLogger.debug(LTag.PUMP, "Canceling extended bolus: ${result.toText(rh)}")
        _lastDataTime.value = System.currentTimeMillis()
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
        if (pumpTypeFlow.value == pumpTypeNew) return
        aapsLogger.debug(LTag.PUMP, "New pump configuration found ($pumpTypeNew), changing from previous (${pumpTypeFlow.value})")
        pumpDescription.fillFor(pumpTypeNew)
        pumpTypeFlow.value = pumpTypeNew
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {}

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "virtual_pump_settings",
        titleResId = R.string.virtualpump_settings,
        items = listOf(
            StringKey.VirtualPumpType.withEntries(
                PumpType.entries
                    .filter { it.description != "USER" }
                    .sortedBy { it.description }
                    .associate { it.description to it.description }
            ),
            BooleanKey.VirtualPumpStatusUpload
        ),
        icon = pluginDescription.icon
    )

}
