package app.aaps.pump.common

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.SystemClock
import android.text.format.DateFormat
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.data.PumpStatus
import app.aaps.pump.common.defs.PumpDriverAction
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.driver.PumpDriverConfiguration
import app.aaps.pump.common.driver.PumpDriverConfigurationCapable
import app.aaps.pump.common.driver.refresh.PumpDataRefreshAction
import app.aaps.pump.common.driver.refresh.PumpDataRefreshType
import app.aaps.pump.common.sync.PumpDbEntryCarbs
import app.aaps.pump.common.sync.PumpSyncEntriesCreator
import app.aaps.pump.common.sync.PumpSyncStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Provider
import app.aaps.core.ui.R as Rc

/**
 * Created by andy on 23.04.18.
 */
// When using this class, make sure that your first step is to create mConnection (see MedtronicPumpPlugin)
abstract class PumpPluginAbstract protected constructor(
    pluginDescription: PluginDescription,
    ownPreferences: List<Class<out NonPreferenceKey>> = emptyList(),
    pumpType: PumpType,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    commandQueue: CommandQueue,
    var rxBus: RxBus,
    var context: Context,
    var fabricPrivacy: FabricPrivacy,
    var aapsSchedulers: AapsSchedulers,
    var pumpSync: PumpSync,
    var pumpSyncStorage: PumpSyncStorage,
    val pumpDriverConfigurationInternal: PumpDriverConfiguration,
    var decimalFormatter: DecimalFormatter,
    var dateUtil: DateUtil,
    protected val pumpEnactResultProvider: Provider<PumpEnactResult>
) : PumpPluginBase(pluginDescription = pluginDescription,
                   ownPreferences = ownPreferences,
                   aapsLogger = aapsLogger,
                   rh = rh,
                   preferences = preferences,
                   commandQueue = commandQueue),
    Pump, PluginConstraints,
    PumpDriverConfigurationCapable, /*Constraints,*/ PumpSyncEntriesCreator {

    protected val disposable = CompositeDisposable()

    // Pump capabilities
    final override var pumpDescription = PumpDescription()

    protected open var serviceConnection: ServiceConnection? = null
    protected var serviceRunning = false
    protected var pumpState = PumpDriverState.NotInitialized
    protected var displayConnectionMessages = false
    protected var timeChangeType: TimeChangeType? = null
    protected var hasTimeDateOrTimeZoneChanged = false
    protected var isDriverInitialized = false
    protected var firstRun = true
    protected var isRefresh = false
    protected val statusRefreshMap: MutableMap<PumpDataRefreshType?, Long?> = mutableMapOf()

    var pumpType: PumpType = PumpType.GENERIC_AAPS
        get() = field
        set(value) {
            field = value
            pumpDescription.fillFor(value)
        }

    protected var gson: Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    abstract fun initPumpStatusData()

    open fun hasService(): Boolean {
        return pumpDriverConfigurationInternal.hasService
    }

    override fun onStart() {
        super.onStart()
        initPumpStatusData()
        if (hasService()) {
            val intent = Intent(context, serviceClass)
            context.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
            disposable.add(
                    rxBus
                        .toObservable(EventAppExit::class.java)
                        .observeOn(aapsSchedulers.io)
                        .subscribe({ context.unbindService(serviceConnection!!) }, fabricPrivacy::logException)
                )
        }
        serviceRunning = true
        onStartScheduledPumpActions()
    }

    override fun onStop() {
        aapsLogger.debug(LTag.PUMP, model().model + " onStop()")
        if (hasService()) {
            serviceConnection?.let { serviceConnection ->
                context.unbindService(serviceConnection)
            }
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

    override val lastDataTime: Long get() = pumpStatusData.lastConnection

    override val lastBolusAmount: Double?
        get() = pumpStatusData.lastBolusAmount

    override val lastBolusTime: Long?
        get() = if (pumpStatusData.lastBolusTime==null) null else pumpStatusData.lastBolusTime!!.time

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


    @Synchronized
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        return try {
            deliverBolus(detailedBolusInfo)
        } finally {
            triggerUIChange()
        }
    }

    protected fun incrementStatistics(statsKey: LongNonPreferenceKey) {
        preferences.inc(statsKey)
    }

    // TODO check deliverTreatmentMy
    @Synchronized
    fun deliverTreatmentMy(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        return try {
            if (detailedBolusInfo.insulin == 0.0 && detailedBolusInfo.carbs == 0.0) {
                // neither carbs nor bolus requested
                aapsLogger.error("deliverTreatment: Invalid input")
                pumpEnactResultProvider.get().success(false).enacted(false)
                    .bolusDelivered(0.0)
                    .comment(app.aaps.core.ui.R.string.invalid_input)
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                deliverBolus(detailedBolusInfo)
            } else {
                detailedBolusInfo.timestamp = System.currentTimeMillis()

                // no bolus required, carb only treatment
                pumpSyncStorage.addCarbs(PumpDbEntryCarbs(detailedBolusInfo, this))

                val bolusingEvent = EventOverviewBolusProgress(
                                    rh = rh, id = detailedBolusInfo.id, percent = 100 )
                // bolusingEvent.t = EventOverviewBolusProgress.Treatment(0.0, detailedBolusInfo.carbs.toInt(), detailedBolusInfo.bolusType === BS.Type.SMB, detailedBolusInfo.id)
                // bolusingEvent.percent = 100
                rxBus.send(bolusingEvent)
                aapsLogger.debug(LTag.PUMP, "deliverTreatment: Carb only treatment.")
                pumpEnactResultProvider.get().success(true).enacted(true)
                    .bolusDelivered(0.0)
                    .comment(Rc.string.ok)
            }
        } finally {
            triggerUIChange()
        }
    }

    protected fun refreshCustomActionsList() {
        rxBus.send(EventCustomActionsChanged())
    }

    override fun manufacturer(): ManufacturerType = pumpType.manufacturer()
    override fun model(): PumpType = pumpType
    override fun canHandleDST(): Boolean = pumpDriverConfigurationInternal.canHandleDST

    protected abstract fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult

    protected abstract fun triggerUIChange()

    private fun getOperationNotSupportedWithCustomText(resourceId: Int): PumpEnactResult =
        pumpEnactResultProvider.get().success(false).enacted(false).comment(resourceId)

    init {
        pumpDescription.fillFor(pumpType)
        this.pumpType = pumpType
    }

    protected fun databaseOperationAfterPumpAction(pumpDriverAction: PumpDriverAction) {

    }

    var logPrefix : String = pumpDriverConfigurationInternal.logPrefix

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.warn(LTag.PUMP, logPrefix + "Time or TimeZone changed (type=$timeChangeType). ")
        this.timeChangeType = timeChangeType
        this.hasTimeDateOrTimeZoneChanged = true
    }

    override fun getPumpDriverConfiguration(): PumpDriverConfiguration {
        return this.pumpDriverConfigurationInternal
    }

    protected fun getTimeInFutureFromMinutes(minutes: Int): Long {
        return System.currentTimeMillis() + getTimeInMs(minutes)
    }

    protected fun getTimeInMs(minutes: Int): Long {
        return minutes * 60 * 1000L
    }


    // PumpDataRefreshCapable

    protected fun startRefreshOfPumpCommands() {

        // check status every minute (if any status needs refresh we send readStatus command)
        Thread {
            do {
                SystemClock.sleep(60000)
                if (this.isDriverInitialized && !isInPreventConnectMode()) {
                    val statusRefresh = workWithStatusRefresh(
                        PumpDataRefreshAction.GetData, null, null
                    )
                    if (doWeHaveAnyStatusNeededRefereshing(statusRefresh)) {
                        if (!commandQueue.statusInQueue()) {
                            commandQueue.readStatus("Scheduled Status Refresh", null)
                        }
                    }
                    doCustomScheduledActions()
                }
            } while (serviceRunning)
        }.start()

    }


    @Synchronized
    protected fun workWithStatusRefresh(
        action: PumpDataRefreshAction,
        statusRefreshType: PumpDataRefreshType?,
        time: Long?,
        customParameter: Any? = null
    ): HashMap<PumpDataRefreshType?, Long?>? {
        return when (action) {
            PumpDataRefreshAction.Add     -> {
                statusRefreshMap[statusRefreshType] = time
                null
            }

            PumpDataRefreshAction.AddSameAsOther     -> {
                val statusRefreshType2 = customParameter as PumpDataRefreshType
                val timeFromType = statusRefreshMap[statusRefreshType2]
                statusRefreshMap[statusRefreshType] = timeFromType
                null
            }

            PumpDataRefreshAction.GetData -> {
                HashMap(statusRefreshMap)
            }

            PumpDataRefreshAction.Delete -> {
                statusRefreshMap.remove(statusRefreshType)
                null
            }
        }
    }


    protected open fun doCustomScheduledActions() {

    }


    protected fun doWeHaveAnyStatusNeededRefereshing(statusRefresh: Map<PumpDataRefreshType?, Long?>?): Boolean {
        aapsLogger.debug(LTag.PUMP, "Do we have status needed to refresh: $statusRefresh, currentTime=${System.currentTimeMillis()}")
        for ((key, value) in statusRefresh!!) {
            if (value==null) {
                aapsLogger.error(LTag.PUMP, "We got key ($key}) with value null.")
                continue
            }
            if (value!! > 0 && System.currentTimeMillis() > value) {
                return true
            }
        }
        return hasTimeDateOrTimeZoneChanged
    }

    // protected fun scheduleNextRefresh(refreshType: PumpDataRefreshType) {
    //     scheduleNextRefresh(refreshType, 0)
    // }

    protected fun scheduleNextRefresh(refreshType: PumpDataRefreshType, additionalTimeInMinutes: Int = 0) {
        val refreshTime = getRefreshTime(refreshType)

        if (refreshTime>0) {
            workWithStatusRefresh(
                PumpDataRefreshAction.Add, refreshType,
                getTimeInFutureFromMinutes(refreshTime + additionalTimeInMinutes)
            )
        } else if (refreshTime==-1) {
            val statusRefresh = workWithStatusRefresh(
                PumpDataRefreshAction.GetData, null,
                null)!!

            if (statusRefresh.containsKey(refreshType)) {
                workWithStatusRefresh(
                    PumpDataRefreshAction.Delete, refreshType,
                    -1
                )
            }
        }
    }

    protected fun scheduleNextRefreshWithSpecifiedTime(refreshType: PumpDataRefreshType, whenToRefresh: Long) {
        workWithStatusRefresh(
            PumpDataRefreshAction.Add, refreshType,
            whenToRefresh
        )
    }

    protected fun scheduleNextRefreshAtSameTimeAsOtherType(refreshType: PumpDataRefreshType, refreshTypeToCopy: PumpDataRefreshType) {
        workWithStatusRefresh(
            action = PumpDataRefreshAction.AddSameAsOther,
            statusRefreshType = refreshType,
            time = null,
            customParameter = refreshTypeToCopy
        )
    }


    // this method needs to be overwriten in your driver and implement PumpDataRefreshCapable
    open fun getRefreshTime(pumpDataRefreshType: PumpDataRefreshType): Int {
        return -1
    }


    open fun isInPreventConnectMode(): Boolean {
        return false
    }


}
