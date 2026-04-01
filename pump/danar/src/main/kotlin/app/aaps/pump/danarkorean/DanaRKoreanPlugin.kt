package app.aaps.pump.danarkorean

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaBooleanKey
import app.aaps.pump.danar.AbstractDanaRPlugin
import app.aaps.pump.danarkorean.services.DanaRKoreanExecutionService
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.util.Vector
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
class DanaRKoreanPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    aapsSchedulers: AapsSchedulers,
    rxBus: RxBus,
    private val context: Context,
    rh: ResourceHelper,
    constraintChecker: ConstraintsChecker,
    activePlugin: ActivePlugin,
    commandQueue: CommandQueue,
    danaPump: DanaPump,
    dateUtil: DateUtil,
    private val fabricPrivacy: FabricPrivacy,
    pumpSync: PumpSync,
    preferences: Preferences,
    config: Config,
    notificationManager: NotificationManager,
    danaHistoryDatabase: DanaHistoryDatabase,
    decimalFormatter: DecimalFormatter,
    pumpEnactResultProvider: Provider<PumpEnactResult>
) : AbstractDanaRPlugin(
    danaPump,
    aapsLogger,
    rh,
    preferences,
    config,
    commandQueue,
    constraintChecker,
    aapsSchedulers,
    rxBus,
    activePlugin,
    dateUtil,
    pumpSync,
    notificationManager,
    danaHistoryDatabase,
    decimalFormatter,
    pumpEnactResultProvider
) {

    private var scope: CoroutineScope? = null

    init {
        pluginDescription.description(app.aaps.pump.dana.R.string.description_pump_dana_r_korean)
        pumpDescription.fillFor(PumpType.DANA_R_KOREAN)
    }

    override fun onStart() {
        context.bindService(Intent(context, DanaRKoreanExecutionService::class.java), mConnection, Context.BIND_AUTO_CREATE)
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        preferences.observe(DanaBooleanKey.UseExtended).drop(1).onEach {
            if (pumpSync.expectedPumpState().extendedBolus != null) {
                executionService?.extendedBolusStop()
            }
        }.launchIn(newScope)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ context.unbindService(mConnection) }, fabricPrivacy::logException)
        super.onStart()
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
        context.unbindService(mConnection)
        disposable.clear()
        super.onStop()
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected")
            executionService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            val mLocalBinder = service as DanaRKoreanExecutionService.LocalBinder
            executionService = mLocalBinder.serviceInstance
        }
    }

    // Plugin base interface
    override val name: String
        get() = rh.gs(app.aaps.pump.dana.R.string.danarkoreanpump)

    // Pump interface
    override val isFakingTempsByExtendedBoluses: Boolean
        get() = preferences.get(DanaBooleanKey.UseExtended)

    override fun isInitialized(): Boolean =
        isConfigured() && danaPump.lastConnection > 0 && danaPump.maxBasal > 0 && !danaPump.isConfigUD && !danaPump.isEasyModeEnabled && danaPump.isExtendedBolusEnabled && danaPump.isPasswordOK

    override fun isHandshakeInProgress(): Boolean =
        executionService?.isHandshakeInProgress == true

    override fun finishHandshaking() {
        executionService?.finishHandshaking()
    }

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger)).value()
        var connectionOK = false
        if (detailedBolusInfo.insulin > 0)
            connectionOK = executionService?.bolus(detailedBolusInfo) == true
        val result = pumpEnactResultProvider.get()
        result.success(connectionOK && abs(detailedBolusInfo.insulin - BolusProgressData.delivered) < pumpDescription.bolusStep)
            .bolusDelivered(BolusProgressData.delivered)
        if (!result.success) result.comment(
            rh.gs(
                app.aaps.pump.dana.R.string.boluserrorcode,
                detailedBolusInfo.insulin,
                BolusProgressData.delivered,
                danaPump.bolusStartErrorCode
            )
        ) else result.comment(app.aaps.core.ui.R.string.ok)
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered)
        if (detailedBolusInfo.insulin > 0) runBlocking {
            pumpSync.syncBolusWithPumpId(
                dateUtil.now(),
                PumpInsulin(BolusProgressData.delivered),
                detailedBolusInfo.bolusType,
                dateUtil.now(),
                PumpType.DANA_R_KOREAN,
                serialNumber()
            )
        }
        return result
    }

    // This is called from APS
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        // Recheck pump status if older than 30 min
        //This should not be needed while using queue because connection should be done before calling this
        var doTempOff = baseBasalRate.cU - absoluteRate == 0.0 && absoluteRate >= 0.10
        val doLowTemp = absoluteRate < baseBasalRate.cU || absoluteRate < 0.10
        val doHighTemp = absoluteRate > baseBasalRate.cU && !preferences.get(DanaBooleanKey.UseExtended)
        val doExtendedTemp = absoluteRate > baseBasalRate.cU && preferences.get(DanaBooleanKey.UseExtended)

        var percentRate: Int = java.lang.Double.valueOf(absoluteRate / baseBasalRate.cU * 100).toInt()
        // Any basal less than 0.10u/h will be dumped once per hour, not every 4 minutes. So if it's less than .10u/h, set a zero temp.
        if (absoluteRate < 0.10) percentRate = 0
        percentRate = if (percentRate < 100) Round.ceilTo(percentRate.toDouble(), 10.0).toInt() else Round.floorTo(percentRate.toDouble(), 10.0).toInt()
        if (percentRate > pumpDescription.maxTempPercent) {
            percentRate = pumpDescription.maxTempPercent
        }
        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Calculated percent rate: $percentRate")

        if (percentRate == 100) doTempOff = true

        if (doTempOff) {
            // If extended in progress
            if (danaPump.isExtendedInProgress && preferences.get(DanaBooleanKey.UseExtended)) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doTempOff)")
                return cancelExtendedBolus()
            }
            // If temp in progress
            if (danaPump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)")
                return cancelRealTempBasal()
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK")
            return pumpEnactResultProvider.get().success(true).enacted(false).percent(100).isPercent(true).isTempCancel(true)
        }
        if (doLowTemp || doHighTemp) {
            // If extended in progress
            if (danaPump.isExtendedInProgress && preferences.get(DanaBooleanKey.UseExtended)) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)")
                val result = cancelExtendedBolus()
                if (!result.success) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous extended bolus (doLowTemp || doHighTemp)")
                    return result
                }
            }
            // Check if some temp is already in progress
            if (danaPump.isTempBasalInProgress) {
                // Correct basal already set ?
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: currently running: " + danaPump.temporaryBasalToString())
                if (danaPump.tempBasalPercent == percentRate && danaPump.tempBasalRemainingMin > 4) {
                    if (enforceNew) {
                        cancelTempBasal(true)
                    } else {
                        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)")
                        return pumpEnactResultProvider.get().success(true).percent(percentRate).enacted(false).duration(danaPump.tempBasalRemainingMin).isPercent(true).isTempCancel(false)
                    }
                }
            }
            // Convert duration from minutes to hours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal $percentRate% for $durationInMinutes minutes (doLowTemp || doHighTemp)")
            return setTempBasalPercent(percentRate, durationInMinutes, false, tbrType)
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (danaPump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doExtendedTemp)")
                val result = cancelRealTempBasal()
                // Check for proper result
                if (!result.success) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous temp basal (doExtendedTemp)")
                    return result
                }
            }

            // Calculate # of halfHours from minutes
            val durationInHalfHours = max(durationInMinutes / 30, 1)
            // We keep current basal running so need to sub current basal
            var extendedRateToSet: Double = absoluteRate - baseBasalRate.cU
            // needs to be rounded to 0.1
            extendedRateToSet = Round.roundTo(extendedRateToSet, pumpDescription.extendedBolusStep * 2) // *2 because of half hours

            // What is current rate of extended bolusing in u/h?
            aapsLogger.debug(
                LTag.PUMP,
                "setTempBasalAbsolute: Extended bolus in progress: " + danaPump.isExtendedInProgress + " rate: " + danaPump.extendedBolusAbsoluteRate + "U/h duration remaining: " + danaPump.extendedBolusRemainingMinutes + "min"
            )
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h")

            // Compare with extended rate in progress
            if (danaPump.isExtendedInProgress && abs(danaPump.extendedBolusAbsoluteRate - extendedRateToSet) < pumpDescription.extendedBolusStep) {
                // correct extended already set
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct extended already set")
                return pumpEnactResultProvider.get().success(true).absolute(danaPump.extendedBolusAbsoluteRate).enacted(false).duration(danaPump.extendedBolusRemainingMinutes).isPercent(false)
                    .isTempCancel(false)
            }

            // Now set new extended, no need to stop previous (if running) because it's replaced
            val extendedAmount = extendedRateToSet / 2 * durationInHalfHours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting extended: " + extendedAmount + "U  half hours: " + durationInHalfHours)
            val result = setExtendedBolus(extendedAmount, durationInMinutes)
            if (!result.success) {
                aapsLogger.error("setTempBasalAbsolute: Failed to set extended bolus")
                return result
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Extended bolus set ok")
            result.absolute(result.absolute + baseBasalRate.cU)
            return result
        }
        // We should never end here
        aapsLogger.error("setTempBasalAbsolute: Internal error")
        return pumpEnactResultProvider.get().success(false).comment("Internal error")
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (danaPump.isTempBasalInProgress) return cancelRealTempBasal()
        if (danaPump.isExtendedInProgress && preferences.get(DanaBooleanKey.UseExtended)) {
            return cancelExtendedBolus()
        }
        val result = pumpEnactResultProvider.get()
        result.success(true).enacted(false).comment(app.aaps.core.ui.R.string.ok).isTempCancel(true)
        return result
    }

    override fun model(): PumpType = PumpType.DANA_R_KOREAN

    private fun cancelRealTempBasal(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (danaPump.isTempBasalInProgress) {
            executionService?.tempBasalStop()
            if (!danaPump.isTempBasalInProgress) {
                runBlocking {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        dateUtil.now(),
                        dateUtil.now(),
                        pumpDescription.pumpType,
                        serialNumber()
                    )
                }
                result.success(true).enacted(true).isTempCancel(true)
            } else result.success(false).enacted(false).isTempCancel(true)
        } else {
            result.success(true).isTempCancel(true).comment(app.aaps.core.ui.R.string.ok)
            aapsLogger.debug(LTag.PUMP, "cancelRealTempBasal: OK")
        }
        return result
    }

    override fun loadEvents(): PumpEnactResult = pumpEnactResultProvider.get() // no history, not needed
    override fun setUserOptions(): PumpEnactResult = pumpEnactResultProvider.get()

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "danar_korean_settings",
        titleResId = app.aaps.pump.dana.R.string.danar_pump_settings,
        items = listOf(
            DanaBooleanKey.UseExtended
        ),
        icon = pluginDescription.icon
    )

    private fun getBondedBluetoothDevices(context: Context): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter?.bondedDevices
                ?.mapNotNull { it.name }
                ?.sorted()
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    // TODO: Remove after full migration to Compose preferences (getPreferenceScreenContent)
    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        var entries = emptyArray<CharSequence>()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val devices = Vector<CharSequence>()
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.let { bta ->
                for (dev in bta.bondedDevices)
                    dev.name?.let { name -> devices.add(name) }
            }
            entries = devices.toTypedArray()
        } else ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "danar_korean_settings"
            title = rh.gs(app.aaps.pump.dana.R.string.danar_pump_settings)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = DanaBooleanKey.UseExtended, title = app.aaps.pump.dana.R.string.danar_useextended_title))
        }
    }
}