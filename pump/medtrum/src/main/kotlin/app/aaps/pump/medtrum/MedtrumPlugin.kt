package app.aaps.pump.medtrum

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Medtrum
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.pump.mapState
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.withEntriesProvider
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.icons.IcPluginMedtrum
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.compose.MedtrumComposeContent
import app.aaps.pump.medtrum.keys.MedtrumBooleanKey
import app.aaps.pump.medtrum.keys.MedtrumDoubleNonKey
import app.aaps.pump.medtrum.keys.MedtrumIntKey
import app.aaps.pump.medtrum.keys.MedtrumIntNonKey
import app.aaps.pump.medtrum.keys.MedtrumLongNonKey
import app.aaps.pump.medtrum.keys.MedtrumStringKey
import app.aaps.pump.medtrum.keys.MedtrumStringNonKey
import app.aaps.pump.medtrum.services.MedtrumService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

@Singleton
class MedtrumPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val medtrumPump: MedtrumPump,
    private val uiInteraction: UiInteraction,
    private val notificationManager: NotificationManager,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val bolusProgressData: BolusProgressData,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .icon(IcPluginMedtrum)
        .pluginName(R.string.medtrum)
        .shortName(R.string.medtrum_pump_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.medtrum_pump_description)
        .composeContent { _ ->
            MedtrumComposeContent(
                pluginName = rh.gs(R.string.medtrum),
                protectionCheck = protectionCheck,
                blePreCheck = blePreCheck
            )
        },
    ownPreferences = listOf(
        MedtrumStringKey::class.java, MedtrumIntKey::class.java, MedtrumBooleanKey::class.java,
        MedtrumIntNonKey::class.java, MedtrumLongNonKey::class.java, MedtrumStringNonKey::class.java, MedtrumDoubleNonKey::class.java
    ),
    aapsLogger, rh, preferences, commandQueue
), Pump, Medtrum {

    private val disposable = CompositeDisposable()
    private var scope: CoroutineScope? = null
    private var medtrumService: MedtrumService? = null

    override fun onStart() {
        super.onStart()
        aapsLogger.debug(LTag.PUMP, "MedtrumPlugin onStart()")
        medtrumPump.loadVarsFromSP()
        val intent = Intent(context, MedtrumService::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ context.unbindService(mConnection) }, fabricPrivacy::logException)
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        preferences.observe(MedtrumStringNonKey.SnInput).drop(1).onEach {
            updateMaxInsulinLimitsForPumpType()
        }.launchIn(newScope)

        // Force enable pump unreachable alert due to some failure modes of Medtrum pump
        preferences.put(BooleanKey.AlertPumpUnreachable, true)
    }

    override fun onStop() {
        aapsLogger.debug(LTag.PUMP, "MedtrumPlugin onStop()")
        scope?.cancel()
        scope = null
        context.unbindService(mConnection)
        disposable.clear()
        super.onStop()
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected")
            medtrumService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            val mLocalBinder = service as MedtrumService.LocalBinder
            medtrumService = mLocalBinder.serviceInstance
        }
    }

    fun getService(): MedtrumService? {
        return medtrumService
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)

        preprocessConnectionAlertSettings(preferenceFragment)
    }

    private fun preprocessConnectionAlertSettings(preferenceFragment: PreferenceFragmentCompat) {
        val unreachableAlertSetting = preferenceFragment.findPreference<SwitchPreference>(BooleanKey.AlertPumpUnreachable.key)
        val unreachableThresholdSetting = preferenceFragment.findPreference<AdaptiveIntPreference>(IntKey.AlertsPumpUnreachableThreshold.key)

        unreachableAlertSetting?.apply {
            isSelectable = false
            summary = rh.gs(R.string.enable_pump_unreachable_alert_summary)
        }

        unreachableThresholdSetting?.apply {
            val currentValue = text
            summary = "${rh.gs(R.string.pump_unreachable_threshold_minutes_summary)}\n${currentValue}"
        }
    }

    override fun isConfigured(): Boolean =
        medtrumPump.pumpSNFromSP != 0L && medtrumPump.pumpState > MedtrumPumpState.EJECTED

    override fun isInitialized(): Boolean {
        return isConfigured() && medtrumPump.pumpState < MedtrumPumpState.STOPPED
    }

    override fun isSuspended(): Boolean {
        return medtrumPump.pumpState < MedtrumPumpState.ACTIVE || medtrumPump.pumpState > MedtrumPumpState.ACTIVE_ALT
    }

    override fun isBusy(): Boolean {
        return false
    }

    override fun isConnected(): Boolean {
        // This is a workaround to prevent AAPS to trigger connects when we have no patch activated
        return if (!isInitialized()) {
            true
        } else {
            medtrumService?.isConnected == true
        }
    }

    override fun isConnecting(): Boolean = medtrumService?.isConnecting == true
    override fun isHandshakeInProgress(): Boolean = false

    override fun finishHandshaking() {
        // Unused
    }

    override fun connect(reason: String) {
        if (isInitialized()) {
            aapsLogger.debug(LTag.PUMP, "Medtrum connect - reason:$reason")
            if (medtrumService != null) {
                aapsLogger.debug(LTag.PUMP, "Medtrum connect - Attempt connection!")
                val success = medtrumService?.connect(reason) == true
                if (!success) ToastUtils.errorToast(context, app.aaps.core.ui.R.string.ble_not_supported_or_not_paired)
            }
        }
    }

    override fun disconnect(reason: String) {
        if (isInitialized()) {
            aapsLogger.debug(LTag.PUMP, "Medtrum disconnect from: $reason")
            medtrumService?.disconnect(reason)
        }
    }

    override fun stopConnecting() {
        if (isInitialized()) {
            aapsLogger.debug(LTag.PUMP, "Medtrum stopConnecting")
            medtrumService?.stopConnecting()
        }
    }

    override fun getPumpStatus(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Medtrum getPumpStatus - reason:$reason")
        if (isInitialized()) {
            val connectionOK = medtrumService?.readPumpStatus() ?: false
            if (connectionOK == false) {
                aapsLogger.error(LTag.PUMP, "Medtrum getPumpStatus failed")
            }
        }
    }

    override fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        // New profile will be set when patch is activated
        if (!isInitialized()) return pumpEnactResultProvider.get().success(true).enacted(true)

        return if (medtrumService?.updateBasalsInPump(profile) == true) {
            notificationManager.dismiss(NotificationId.FAILED_UPDATE_PROFILE)
            notificationManager.post(NotificationId.PROFILE_SET_OK, app.aaps.core.ui.R.string.profile_set_ok, validMinutes = 60)
            pumpEnactResultProvider.get().success(true).enacted(true)
        } else {
            notificationManager.post(NotificationId.FAILED_UPDATE_PROFILE, app.aaps.core.ui.R.string.failed_update_basal_profile, level = NotificationLevel.URGENT)
            pumpEnactResultProvider.get()
        }
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        if (!isInitialized()) return true
        var result = false
        val profileBytes = medtrumPump.buildMedtrumProfileArray(profile)
        if (profileBytes?.size == medtrumPump.actualBasalProfile.size) {
            result = true
            for (i in profileBytes.indices) {
                if (profileBytes[i] != medtrumPump.actualBasalProfile[i]) {
                    result = false
                    break
                }
            }
        }
        return result
    }

    override val lastDataTime: StateFlow<Long> = medtrumPump.lastConnectionFlow
    override val lastBolusTime: StateFlow<Long?> = medtrumPump.lastBolusTimeFlow
    override val lastBolusAmount: StateFlow<PumpInsulin?> = medtrumPump.lastBolusAmountFlow.mapState { it?.let(::PumpInsulin) }
    override val baseBasalRate: PumpRate get() = PumpRate(medtrumPump.baseBasalRate)
    override val reservoirLevel: StateFlow<PumpInsulin> = medtrumPump.reservoirFlow.mapState(::PumpInsulin)
    override val batteryLevel: StateFlow<Int?> = medtrumPump.batteryFlow

    @Synchronized
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        aapsLogger.debug(LTag.PUMP, "deliverTreatment: " + detailedBolusInfo.insulin + "U")
        if (!isInitialized()) return pumpEnactResultProvider.get().success(false).enacted(false)
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger)).value()
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: Delivering bolus: " + detailedBolusInfo.insulin + "U")
        val connectionOK = medtrumService?.setBolus(detailedBolusInfo) == true
        val result = pumpEnactResultProvider.get()
        val delivered = bolusProgressData.state.value?.delivered ?: 0.0
        result.success = (connectionOK && abs(detailedBolusInfo.insulin - delivered) < pumpDescription.bolusStep) || medtrumPump.bolusStopped
        result.bolusDelivered = delivered
        if (result.success && result.bolusDelivered > 0.0) {
            medtrumPump.lastBolusAmount = result.bolusDelivered
            medtrumPump.lastBolusTime = detailedBolusInfo.timestamp
        }
        if (!result.success) {
            result.comment(medtrumPump.bolusErrorReason ?: rh.gs(R.string.bolus_error_reason_pump_error))
        } else {
            result.comment(app.aaps.core.ui.R.string.ok)
        }
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Success: ${result.success} Asked: ${detailedBolusInfo.insulin} Delivered: ${result.bolusDelivered}")
        return result
    }

    override fun stopBolusDelivering() {
        if (!isInitialized()) return

        aapsLogger.info(LTag.PUMP, "stopBolusDelivering")
        medtrumService?.stopBolus()
    }

    @Synchronized
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        if (!isInitialized()) return pumpEnactResultProvider.get().success(false).enacted(false)

        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - absoluteRate: $absoluteRate, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        // round rate to pump rate
        val pumpRate = absoluteRate
        temporaryBasalStorage.add(PumpSync.PumpState.TemporaryBasal(dateUtil.now(), T.mins(durationInMinutes.toLong()).msecs(), pumpRate, true, tbrType, 0L, 0L))
        val connectionOK = medtrumService?.setTempBasal(pumpRate, durationInMinutes) == true
        return if (connectionOK
            && medtrumPump.tempBasalInProgress
            && abs(medtrumPump.tempBasalAbsoluteRate - pumpRate) <= 0.05
        ) {

            pumpEnactResultProvider.get().success(true).enacted(true).duration(durationInMinutes).absolute(medtrumPump.tempBasalAbsoluteRate)
                .isPercent(false)
                .isTempCancel(false)
        } else {
            aapsLogger.error(
                LTag.PUMP,
                "setTempBasalAbsolute failed, connectionOK: $connectionOK, tempBasalInProgress: ${medtrumPump.tempBasalInProgress}, tempBasalAbsoluteRate: ${medtrumPump.tempBasalAbsoluteRate}"
            )
            pumpEnactResultProvider.get().success(false).enacted(false).comment("Medtrum setTempBasalAbsolute failed")
        }
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setTempBasalPercent - percent: $percent, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        return pumpEnactResultProvider.get().success(false).enacted(false).comment("Medtrum driver does not support percentage temp basals")
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setExtendedBolus - insulin: $insulin, durationInMinutes: $durationInMinutes")
        return pumpEnactResultProvider.get().success(false).enacted(false).comment("Medtrum driver does not support extended boluses")
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (!isInitialized()) return pumpEnactResultProvider.get().success(false).enacted(false)

        aapsLogger.info(LTag.PUMP, "cancelTempBasal - enforceNew: $enforceNew")
        val connectionOK = medtrumService?.cancelTempBasal() == true
        return if (connectionOK && !medtrumPump.tempBasalInProgress) {
            pumpEnactResultProvider.get().success(true).enacted(true).isTempCancel(true)
        } else {
            aapsLogger.error(LTag.PUMP, "cancelTempBasal failed, connectionOK: $connectionOK, tempBasalInProgress: ${medtrumPump.tempBasalInProgress}")
            pumpEnactResultProvider.get().success(false).enacted(false).comment("Medtrum cancelTempBasal failed")
        }
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        return pumpEnactResultProvider.get()
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Medtrum
    override fun model(): PumpType = medtrumPump.pumpType()
    override fun serialNumber(): String = medtrumPump.pumpSNFromSP.toString(radix = 16).uppercase()
    override val pumpDescription: PumpDescription get() = PumpDescription().fillFor(medtrumPump.pumpType())
    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun loadTDDs(): PumpEnactResult = pumpEnactResultProvider.get() // Note: Can implement this if we implement history fully (no priority)
    override fun canHandleDST(): Boolean = true

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        medtrumPump.needCheckTimeUpdate = true
        if (isInitialized()) {
            commandQueue.updateTime(object : Callback() {
                override fun run() {
                    if (!this.result.success) {
                        aapsLogger.error(LTag.PUMP, "Medtrum time update failed")
                        // Only notify here on failure (connection may be failed), service will handle success
                        medtrumService?.timeUpdateNotification(false)
                    }
                }
            })
        }
    }

    // Medtrum interface
    override fun loadEvents(): PumpEnactResult {
        if (!isInitialized()) return pumpEnactResultProvider.get().success(false).enacted(false)
        val connectionOK = medtrumService?.loadEvents() == true
        return pumpEnactResultProvider.get().success(connectionOK)
    }

    override fun setUserOptions(): PumpEnactResult {
        if (!isInitialized()) return pumpEnactResultProvider.get().success(false).enacted(false)
        val connectionOK = medtrumService?.setUserSettings() == true
        return pumpEnactResultProvider.get().success(connectionOK)
    }

    override fun clearAlarms(): PumpEnactResult {
        if (!isInitialized()) return pumpEnactResultProvider.get().success(false).enacted(false)
        val connectionOK = medtrumService?.clearAlarms() == true
        return pumpEnactResultProvider.get().success(connectionOK)
    }

    override fun deactivate(): PumpEnactResult {
        val connectionOK = medtrumService?.deactivatePatch() == true
        return pumpEnactResultProvider.get().success(connectionOK)
    }

    override fun updateTime(): PumpEnactResult {
        if (!isInitialized()) return pumpEnactResultProvider.get().success(false).enacted(false)
        val connectionOK = medtrumService?.updateTimeIfNeeded() == true
        return pumpEnactResultProvider.get().success(connectionOK)
    }

    override fun getPreferenceScreenContent(): PreferenceSubScreenDef {
        // Update max values based on pump type (same as legacy addPreferenceScreen)
        updateMaxInsulinLimitsForPumpType()

        return PreferenceSubScreenDef(
            key = "medtrum_settings",
            titleResId = R.string.medtrum_pump_setting,
            items = listOf(
                MedtrumStringKey.MedtrumAlarmSettings.withEntriesProvider(
                    provider = { context -> getAlarmEntriesForPumpType(context) }
                ),
                MedtrumBooleanKey.MedtrumWarningNotification,
                MedtrumBooleanKey.MedtrumPatchExpiration,
                MedtrumIntKey.MedtrumPumpExpiryWarningHours,
                MedtrumIntKey.MedtrumHourlyMaxInsulin,
                MedtrumIntKey.MedtrumDailyMaxInsulin,
                PreferenceSubScreenDef(
                    key = "medtrum_advanced",
                    titleResId = app.aaps.core.ui.R.string.advanced_settings_title,
                    items = listOf(
                        MedtrumBooleanKey.MedtrumScanOnConnectionErrors
                    )
                )
            )
        )
    }

    private fun updateMaxInsulinLimitsForPumpType() {
        when (medtrumPump.pumpType()) {
            PumpType.MEDTRUM_NANO -> {
                MedtrumIntKey.MedtrumHourlyMaxInsulin.max = 40
                MedtrumIntKey.MedtrumDailyMaxInsulin.max = 180
            }

            PumpType.MEDTRUM_300U -> {
                MedtrumIntKey.MedtrumHourlyMaxInsulin.max = 60
                MedtrumIntKey.MedtrumDailyMaxInsulin.max = 270
            }

            else                  -> {
                // Default values (same as NANO)
                MedtrumIntKey.MedtrumHourlyMaxInsulin.max = 40
                MedtrumIntKey.MedtrumDailyMaxInsulin.max = 180
            }
        }
        // Clamp current values to new max limits
        preferences.put(MedtrumIntKey.MedtrumHourlyMaxInsulin, min(preferences.get(MedtrumIntKey.MedtrumHourlyMaxInsulin), MedtrumIntKey.MedtrumHourlyMaxInsulin.max))
        preferences.put(MedtrumIntKey.MedtrumDailyMaxInsulin, min(preferences.get(MedtrumIntKey.MedtrumDailyMaxInsulin), MedtrumIntKey.MedtrumDailyMaxInsulin.max))
    }

    private fun getAlarmEntriesForPumpType(context: Context): Map<String, String> {
        // For NANO and 300U pumps, only Beep and Silent options are available
        return when (medtrumPump.pumpType()) {
            PumpType.MEDTRUM_NANO, PumpType.MEDTRUM_300U -> mapOf(
                "6" to context.getString(R.string.alarm_setting_beep),
                "7" to context.getString(R.string.alarm_setting_silent)
            )

            else                                         -> mapOf(
                "0" to context.getString(R.string.alarm_setting_light_vibrate_beep),
                "1" to context.getString(R.string.alarm_setting_light_vibrate),
                "2" to context.getString(R.string.alarm_setting_light_beep),
                "3" to context.getString(R.string.alarm_setting_light),
                "4" to context.getString(R.string.alarm_setting_vibrate_beep),
                "5" to context.getString(R.string.alarm_setting_vibrate),
                "6" to context.getString(R.string.alarm_setting_beep),
                "7" to context.getString(R.string.alarm_setting_silent)
            )
        }
    }

    // TODO: Remove after full migration to Compose preferences (getPreferenceScreenContent)
    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "medtrum_advanced") return

        var alarmEntries = arrayOf<CharSequence>("Light, vibrate and beep", "Light and vibrate", "Light and beep", "Light", "Vibrate and beep", "Vibrate", "Beep", "Silent")
        var alarmValues = arrayOf<CharSequence>("0", "1", "2", "3", "4", "5", "6", "7")

        when (medtrumPump.pumpType()) {
            PumpType.MEDTRUM_NANO, PumpType.MEDTRUM_300U -> {
                alarmEntries = arrayOf(alarmEntries[6], alarmEntries[7]) // "Beep", "Silent"
                alarmValues = arrayOf(alarmValues[6], alarmValues[7]) // "6", "7"
            }

            else                                         -> { /* keep default */
            }
        }

        when (medtrumPump.pumpType()) {
            PumpType.MEDTRUM_NANO -> {
                MedtrumIntKey.MedtrumHourlyMaxInsulin.max = 40
                MedtrumIntKey.MedtrumDailyMaxInsulin.max = 180
            } // maxHourlyMax, maxDailyMax
            PumpType.MEDTRUM_300U -> {
                MedtrumIntKey.MedtrumHourlyMaxInsulin.max = 60
                MedtrumIntKey.MedtrumDailyMaxInsulin.max = 270
            }

            else                  -> { /* keep default 40 & 180 */
            }
        }
        preferences.put(MedtrumIntKey.MedtrumHourlyMaxInsulin, min(preferences.get(MedtrumIntKey.MedtrumHourlyMaxInsulin), MedtrumIntKey.MedtrumHourlyMaxInsulin.max))
        preferences.put(MedtrumIntKey.MedtrumDailyMaxInsulin, min(preferences.get(MedtrumIntKey.MedtrumDailyMaxInsulin), MedtrumIntKey.MedtrumDailyMaxInsulin.max))

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "medtrum_settings"
            title = rh.gs(R.string.medtrum_pump_setting)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveListPreference(ctx = context, stringKey = MedtrumStringKey.MedtrumAlarmSettings, title = R.string.alarm_setting_title, dialogTitle = R.string.alarm_setting_summary, entries = alarmEntries, entryValues = alarmValues))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = MedtrumBooleanKey.MedtrumWarningNotification, title = R.string.pump_warning_notification_title, summary = R.string.pump_warning_notification_summary))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = MedtrumBooleanKey.MedtrumPatchExpiration, title = R.string.patch_expiration_title, summary = R.string.patch_expiration_summary))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = MedtrumIntKey.MedtrumPumpExpiryWarningHours, title = R.string.pump_warning_expiry_hour_title, dialogMessage = R.string.pump_warning_expiry_hour_summary))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = MedtrumIntKey.MedtrumHourlyMaxInsulin, title = R.string.hourly_max_insulin_title, dialogMessage = R.string.hourly_max_insulin_summary))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = MedtrumIntKey.MedtrumDailyMaxInsulin, title = R.string.daily_max_insulin_title, dialogMessage = R.string.daily_max_insulin_summary))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "medtrum_advanced"
                title = rh.gs(app.aaps.core.ui.R.string.advanced_settings_title)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = MedtrumBooleanKey.MedtrumScanOnConnectionErrors, title = R.string.scan_on_connection_error_title, summary = R.string.scan_on_connection_error_summary))
            })
        }
    }
}
