package app.aaps.pump.eopatch

import android.content.Context
import android.os.SystemClock
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.eopatch.alarm.IAlarmManager
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.code.BolusExDuration
import app.aaps.pump.eopatch.keys.EopatchBooleanKey
import app.aaps.pump.eopatch.keys.EopatchIntKey
import app.aaps.pump.eopatch.keys.EopatchStringNonKey
import app.aaps.pump.eopatch.ui.EopatchOverviewFragment
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.TempBasal
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.BehaviorSubject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class EopatchPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val patchManager: IPatchManager,
    private val patchManagerExecutor: PatchManagerExecutor,
    private val alarmManager: IAlarmManager,
    private val preferenceManager: PreferenceManager,
    private val uiInteraction: UiInteraction,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val patchConfig: PatchConfig,
    private val normalBasalManager: NormalBasalManager
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(EopatchOverviewFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_eopatch2_128)
        .pluginName(R.string.eopatch)
        .shortName(R.string.eopatch_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.eopatch_pump_description),
    ownPreferences = listOf(
        EopatchIntKey::class.java, EopatchBooleanKey::class.java, EopatchStringNonKey::class.java
    ),
    aapsLogger, rh, preferences, commandQueue
), Pump {

    private val mDisposables = CompositeDisposable()

    private var mPumpType: PumpType = PumpType.EOFLOW_EOPATCH2
    private var mLastDataTime: Long = 0
    private val mPumpDescription = PumpDescription().fillFor(mPumpType)

    override fun onStart() {
        super.onStart()
        mDisposables += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           if (event.isChanged(EopatchIntKey.LowReservoirReminder.key) || event.isChanged(EopatchIntKey.ExpirationReminder.key)) {
                               patchManager.changeReminderSetting()
                           } else if (event.isChanged(EopatchBooleanKey.BuzzerReminder.key)) {
                               patchManager.changeBuzzerSetting()
                           }
                       }, fabricPrivacy::logException)

        mDisposables += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           preferenceManager.init()
                           patchManager.init()
                           alarmManager.init()
                       }, fabricPrivacy::logException)

        // following was moved from specialEnableCondition()
        // specialEnableCondition() is called too often to add there executive code

        // This is a required code for maintaining the patch activation phase and maintaining the alarm. It should not be deleted.
        //BG -> FG, restart patch activation and trigger unhandled alarm
        if (preferenceManager.isInitDone()) {
            patchManager.checkActivationProcess()
            alarmManager.restartAll()
        }
    }

    override fun onStop() {
        super.onStop()
        aapsLogger.debug(LTag.PUMP, "EOPatchPumpPlugin onStop()")
    }

    override fun isInitialized(): Boolean {
        return isConnected() && patchConfig.isActivated
    }

    override fun isSuspended(): Boolean {
        return preferenceManager.patchState.isNormalBasalPaused
    }

    override fun isBusy(): Boolean {
        return false
    }

    override fun isConnected(): Boolean {
        return if (patchConfig.isDeactivated) true else patchManagerExecutor.patchConnectionState.isConnected
    }

    override fun isConnecting(): Boolean {
        return patchManagerExecutor.patchConnectionState.isConnecting
    }

    override fun isHandshakeInProgress(): Boolean {
        return false
    }

    override fun finishHandshaking() {
    }

    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "EOPatch connect - reason:$reason")
        mLastDataTime = System.currentTimeMillis()
    }

    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "EOPatch disconnect - reason:$reason")
    }

    override fun stopConnecting() {
    }

    override fun getPumpStatus(reason: String) {
        if (patchConfig.isActivated) {
            if ("SMS" == reason) {
                aapsLogger.debug("Acknowledged AAPS getPumpStatus request it was requested through an SMS")
            } else {
                aapsLogger.debug("Acknowledged AAPS getPumpStatus request")
            }
            mDisposables.add(
                patchManagerExecutor.updateConnection()
                    .subscribe(Consumer {
                        mLastDataTime = System.currentTimeMillis()
                    })
            )
        }
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        mLastDataTime = System.currentTimeMillis()
        if (patchConfig.isActivated) {
            if (preferenceManager.patchState.isTempBasalActive) {
                val cancelResult = cancelTempBasal(true)
                if (!cancelResult.success) return pumpEnactResultProvider.get().isTempCancel(true).comment(app.aaps.core.ui.R.string.canceling_tbr_failed)
            }

            if (preferenceManager.patchState.isExtBolusActive) {
                val cancelResult = cancelExtendedBolus()
                if (!cancelResult.success) return pumpEnactResultProvider.get().comment(app.aaps.core.ui.R.string.canceling_eb_failed)
            }
            var isSuccess: Boolean? = null
            val result: BehaviorSubject<Boolean> = BehaviorSubject.create()
            val disposable = result.hide()
                .subscribe {
                    isSuccess = it
                }

            val nb = normalBasalManager.convertProfileToNormalBasal(profile)
            mDisposables.add(
                patchManagerExecutor.startBasal(nb)
                    .observeOn(aapsSchedulers.main)
                    .subscribe({ response ->
                                   result.onNext(response.isSuccess)
                               }, {
                                   result.onNext(false)
                               })
            )

            do {
                SystemClock.sleep(100)
            } while (isSuccess == null)

            disposable.dispose()
            aapsLogger.info(LTag.PUMP, "Basal Profile was set: $isSuccess")
            return if (isSuccess) {
                uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
                pumpEnactResultProvider.get().success(true).enacted(true)
            } else {
                pumpEnactResultProvider.get()
            }
        } else {
            normalBasalManager.setNormalBasal(profile)
            preferenceManager.flushNormalBasalManager()
            uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
            return pumpEnactResultProvider.get().success(true).enacted(true)
        }
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        // if (!patchManager.isActivated) {
        //     return true
        // }

        val ret = normalBasalManager.isEqual(profile)
        aapsLogger.info(LTag.PUMP, "Is this profile set? $ret")
        return ret
    }

    override val lastDataTime: Long get() = mLastDataTime
    override val lastBolusTime: Long? get() = null
    override val lastBolusAmount: Double? get() = null
    override val baseBasalRate: Double
        get() =
            if (!patchConfig.isActivated || preferenceManager.patchState.isNormalBasalPaused) 0.0
            else normalBasalManager.normalBasal.getCurrentSegment()?.doseUnitPerHour?.toDouble() ?: 0.05
    override val reservoirLevel: Double get() = if (!patchConfig.isActivated) 0.0 else preferenceManager.patchState.remainedInsulin.toDouble()
    override val batteryLevel: Int? get() = if (patchConfig.isActivated) preferenceManager.patchState.batteryLevel() else null

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        val askedInsulin = detailedBolusInfo.insulin
        var isSuccess = true
        val result = BehaviorSubject.createDefault(true)
        val disposable = result.hide()
            .subscribe {
                isSuccess = it
            }

        mDisposables.add(
            patchManagerExecutor.startCalculatorBolus(detailedBolusInfo)
                .doOnSuccess { mLastDataTime = System.currentTimeMillis() }
                .subscribe({ result.onNext(it.isSuccess) }, { result.onNext(false) })
        )

        do {
            SystemClock.sleep(100)
            if (patchManagerExecutor.patchConnectionState.isConnected) {
                val delivering = preferenceManager.bolusCurrent.nowBolus.injected.toDouble()
                rxBus.send(EventOverviewBolusProgress(rh, delivered = delivering, id = detailedBolusInfo.id))
            }
        } while (!preferenceManager.bolusCurrent.nowBolus.endTimeSynced && isSuccess)

        rxBus.send(EventOverviewBolusProgress(rh, percent = 100, id = detailedBolusInfo.id))

        detailedBolusInfo.insulin = preferenceManager.bolusCurrent.nowBolus.injected.toDouble()
        patchManager.addBolusToHistory(detailedBolusInfo)

        disposable.dispose()

        return if (isSuccess && abs(askedInsulin - detailedBolusInfo.insulin) < pumpDescription.bolusStep)
            pumpEnactResultProvider.get().success(true).enacted(true).bolusDelivered(askedInsulin)
        else
            pumpEnactResultProvider.get().success(false)/*.enacted(false)*/.bolusDelivered(Round.roundTo(detailedBolusInfo.insulin, 0.01))
    }

    override fun stopBolusDelivering() {
        mDisposables.add(
            patchManagerExecutor.stopNowBolus()
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe {
                    rxBus.send(EventOverviewBolusProgress(status = rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_successfully, (it.injectedBolusAmount * 0.05f)), id = BolusProgressData.id))
                }
        )
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - absoluteRate: ${absoluteRate.toFloat()}, durationInMinutes: ${durationInMinutes.toLong()}, enforceNew: $enforceNew")
        if (preferenceManager.patchState.isNormalBasalAct) {
            mLastDataTime = System.currentTimeMillis()
            val tb = TempBasal.createAbsolute(durationInMinutes.toLong(), absoluteRate.toFloat())
            return patchManagerExecutor.startTempBasal(tb)
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .doOnSuccess {
                    pumpSync.syncTemporaryBasalWithPumpId(
                        timestamp = dateUtil.now(),
                        rate = absoluteRate,
                        duration = T.mins(durationInMinutes.toLong()).msecs(),
                        isAbsolute = true,
                        type = tbrType,
                        pumpId = dateUtil.now(),
                        pumpType = PumpType.EOFLOW_EOPATCH2,
                        pumpSerial = serialNumber()
                    )
                    aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - tbrCurrent:${readTBR()}")
                }
                .map { pumpEnactResultProvider.get().success(true).enacted(true).duration(durationInMinutes).absolute(absoluteRate).isPercent(false).isTempCancel(false) }
                .onErrorReturnItem(
                    pumpEnactResultProvider.get().success(false).enacted(false)
                        .comment("Internal error")
                )
                .blockingGet()
        } else {
            aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - normal basal is not active")
            return pumpEnactResultProvider.get().success(false).enacted(false)
        }
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setTempBasalPercent - percent: $percent, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        if (preferenceManager.patchState.isNormalBasalAct && percent != 0) {
            mLastDataTime = System.currentTimeMillis()
            val tb = TempBasal.createPercent(durationInMinutes.toLong(), percent)
            return patchManagerExecutor.startTempBasal(tb)
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .doOnSuccess {
                    pumpSync.syncTemporaryBasalWithPumpId(
                        timestamp = dateUtil.now(),
                        rate = percent.toDouble(),
                        duration = T.mins(durationInMinutes.toLong()).msecs(),
                        isAbsolute = false,
                        type = tbrType,
                        pumpId = dateUtil.now(),
                        pumpType = PumpType.EOFLOW_EOPATCH2,
                        pumpSerial = serialNumber()
                    )
                    aapsLogger.info(LTag.PUMP, "setTempBasalPercent - tbrCurrent:${readTBR()}")
                }
                .map { pumpEnactResultProvider.get().success(true).enacted(true).duration(durationInMinutes).percent(percent).isPercent(true).isTempCancel(false) }
                .onErrorReturnItem(
                    pumpEnactResultProvider.get().success(false).enacted(false)
                        .comment("Internal error")
                )
                .blockingGet()
        } else {
            aapsLogger.info(LTag.PUMP, "setTempBasalPercent - normal basal is not active")
            return pumpEnactResultProvider.get().success(false).enacted(false)
        }
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setExtendedBolus - insulin: $insulin, durationInMinutes: $durationInMinutes")

        return patchManagerExecutor.startQuickBolus(0f, insulin.toFloat(), BolusExDuration.ofRaw(durationInMinutes))
            .doOnSuccess {
                mLastDataTime = System.currentTimeMillis()
                pumpSync.syncExtendedBolusWithPumpId(
                    timestamp = dateUtil.now(),
                    amount = insulin,
                    duration = T.mins(durationInMinutes.toLong()).msecs(),
                    isEmulatingTB = false,
                    pumpId = dateUtil.now(),
                    pumpType = PumpType.EOFLOW_EOPATCH2,
                    pumpSerial = serialNumber()
                )
            }
            .map { pumpEnactResultProvider.get().success(true).enacted(true) }
            .onErrorReturnItem(
                pumpEnactResultProvider.get().success(false).enacted(false).bolusDelivered(0.0)
                    .comment(rh.gs(app.aaps.core.ui.R.string.error))
            )
            .blockingGet()
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val tbrCurrent = readTBR()

        if (tbrCurrent == null) {
            aapsLogger.debug(LTag.PUMP, "cancelTempBasal - TBR already false.")
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }

        if (!preferenceManager.patchState.isTempBasalActive) {
            return if (pumpSync.expectedPumpState().temporaryBasal != null) {
                pumpEnactResultProvider.get().success(true).enacted(true).isTempCancel(true)
            } else
                pumpEnactResultProvider.get().success(true).isTempCancel(true)
        }

        return patchManagerExecutor.stopTempBasal()
            .doOnSuccess {
                mLastDataTime = System.currentTimeMillis()
                aapsLogger.debug(LTag.PUMP, "cancelTempBasal - $it")
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    timestamp = dateUtil.now(),
                    endPumpId = dateUtil.now(),
                    pumpType = PumpType.EOFLOW_EOPATCH2,
                    pumpSerial = serialNumber()
                )
            }
            .doOnError {
                aapsLogger.error(LTag.PUMP, "cancelTempBasal() - $it")
            }
            .map { pumpEnactResultProvider.get().success(true).enacted(true).isTempCancel(true) }
            .onErrorReturnItem(
                pumpEnactResultProvider.get().success(false).enacted(false)
                    .comment(rh.gs(app.aaps.core.ui.R.string.error))
            )
            .blockingGet()
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        if (preferenceManager.patchState.isExtBolusActive) {
            return patchManagerExecutor.stopExtBolus()
                .doOnSuccess {
                    aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus - success")
                    mLastDataTime = System.currentTimeMillis()
                    pumpSync.syncStopExtendedBolusWithPumpId(
                        timestamp = dateUtil.now(),
                        endPumpId = dateUtil.now(),
                        pumpType = PumpType.EOFLOW_EOPATCH2,
                        pumpSerial = serialNumber()
                    )
                }
                .map { pumpEnactResultProvider.get().success(true).enacted(true).isTempCancel(true) }
                .onErrorReturnItem(
                    pumpEnactResultProvider.get().success(false).enacted(false)
                        .comment(rh.gs(app.aaps.core.ui.R.string.canceling_eb_failed))
                )
                .blockingGet()
        } else {
            aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus - nothing stops")
            return if (pumpSync.expectedPumpState().extendedBolus != null) {
                pumpSync.syncStopExtendedBolusWithPumpId(
                    timestamp = dateUtil.now(),
                    endPumpId = dateUtil.now(),
                    pumpType = PumpType.EOFLOW_EOPATCH2,
                    pumpSerial = serialNumber()
                )
                pumpEnactResultProvider.get().success(true).enacted(true).isTempCancel(true)
            } else
                pumpEnactResultProvider.get()
        }
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Eoflow
    override fun model(): PumpType = PumpType.EOFLOW_EOPATCH2
    override fun serialNumber(): String = patchConfig.patchSerialNumber
    override val pumpDescription: PumpDescription get() = mPumpDescription
    override val isFakingTempsByExtendedBoluses: Boolean = false

    override fun loadTDDs(): PumpEnactResult {
        return pumpEnactResultProvider.get()
    }

    override fun canHandleDST(): Boolean {
        return false
    }

    override fun getCustomActions(): List<CustomAction>? {
        return null
    }

    override fun executeCustomAction(customActionType: CustomActionType) {

    }

    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? {
        return null
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {

    }

    private fun readTBR(): PumpSync.PumpState.TemporaryBasal? {
        return pumpSync.expectedPumpState().temporaryBasal
    }

    override fun addPreferenceScreen(preferenceManager: androidx.preference.PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        val lowReservoirEntries = arrayOf<CharSequence>("10 U", "15 U", "20 U", "25 U", "30 U", "35 U", "40 U", "45 U", "50 U")
        val lowReservoirValues = arrayOf<CharSequence>("10", "15", "20", "25", "30", "35", "40", "45", "50")
        val expirationRemindersEntries =
            arrayOf<CharSequence>("1 hr", "2 hr", "3 hr", "4 hr", "5 hr", "6 hr", "7 hr", "8 hr", "9 hr", "10 hr", "11 hr", "12 hr", "13 hr", "14 hr", "15 hr", "16 hr", "17 hr", "18 hr", "19 hr", "20 hr", "21 hr", "22 hr", "23 hr", "24 hr")
        val expirationRemindersValues = arrayOf<CharSequence>("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24")

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "eopatch_settings"
            title = rh.gs(R.string.eopatch)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = EopatchIntKey.LowReservoirReminder, title = R.string.low_reservoir, entries = lowReservoirEntries, entryValues = lowReservoirValues))
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = EopatchIntKey.ExpirationReminder, title = R.string.patch_expiration_reminders, entries = expirationRemindersEntries, entryValues = expirationRemindersValues))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = EopatchBooleanKey.BuzzerReminder, title = R.string.patch_buzzer_reminders))
        }
    }
}