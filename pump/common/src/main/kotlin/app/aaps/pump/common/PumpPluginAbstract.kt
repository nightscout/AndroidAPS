package app.aaps.pump.common

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
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
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.data.PumpStatus
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.sync.PumpSyncEntriesCreator
import app.aaps.pump.common.sync.PumpSyncStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Provider

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
    protected val pumpEnactResultProvider: Provider<PumpEnactResult>
) : PumpPluginBase(pluginDescription, ownPreferences, aapsLogger, rh, preferences, commandQueue), Pump, PluginConstraints, PumpSyncEntriesCreator {

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

    protected var gson: Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    abstract fun initPumpStatusData()

    override fun onStart() {
        super.onStart()
        initPumpStatusData()
        serviceConnection?.let { serviceConnection ->
            val intent = Intent(context, serviceClass)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            disposable.add(
                rxBus
                    .toObservable(EventAppExit::class.java)
                    .observeOn(aapsSchedulers.io)
                    .subscribe({ context.unbindService(serviceConnection) }, fabricPrivacy::logException)
            )
        }
        serviceRunning = true
        onStartScheduledPumpActions()
    }

    override fun onStop() {
        aapsLogger.debug(LTag.PUMP, model().model() + " onStop()")
        serviceConnection?.let { serviceConnection ->
            context.unbindService(serviceConnection)
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

    protected fun refreshCustomActionsList() {
        rxBus.send(EventCustomActionsChanged())
    }

    override fun manufacturer(): ManufacturerType = pumpType.manufacturer()
    override fun model(): PumpType = pumpType
    override fun canHandleDST(): Boolean = false

    protected abstract fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult

    protected abstract fun triggerUIChange()

    private fun getOperationNotSupportedWithCustomText(resourceId: Int): PumpEnactResult =
        pumpEnactResultProvider.get().success(false).enacted(false).comment(resourceId)

    init {
        pumpDescription.fillFor(pumpType)
        this.pumpType = pumpType
    }
}
