package info.nightscout.androidaps.plugins.pump.eopatch

import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.pump.defs.ManufacturerType
import app.aaps.core.interfaces.pump.defs.PumpDescription
import app.aaps.core.interfaces.pump.defs.PumpType
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
import app.aaps.core.interfaces.utils.T
import app.aaps.core.interfaces.utils.TimeChangeType
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPatchManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager
import info.nightscout.androidaps.plugins.pump.eopatch.code.BolusExDuration
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys
import info.nightscout.androidaps.plugins.pump.eopatch.ui.EopatchOverviewFragment
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class EopatchPumpPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    commandQueue: CommandQueue,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val patchManager: IPatchManager,
    private val alarmManager: IAlarmManager,
    private val preferenceManager: IPreferenceManager,
    private val uiInteraction: UiInteraction,
    private val profileFunction: ProfileFunction
) : PumpPluginBase(
    PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(EopatchOverviewFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_eopatch2_128)
        .pluginName(R.string.eopatch)
        .shortName(R.string.eopatch_shortname)
        .preferencesId(R.xml.pref_eopatch)
        .description(R.string.eopatch_pump_description), injector, aapsLogger, rh, commandQueue
), Pump {

    private val mDisposables = CompositeDisposable()

    private var mPumpType: PumpType = PumpType.EOFLOW_EOPATCH2
    private var mLastDataTime: Long = 0
    private val mPumpDescription = PumpDescription(mPumpType)

    override fun onStart() {
        super.onStart()
        mDisposables += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventPreferenceChange ->
                           if (event.isChanged(rh.gs(SettingKeys.LOW_RESERVOIR_REMINDERS)) || event.isChanged(rh.gs(SettingKeys.EXPIRATION_REMINDERS))) {
                               patchManager.changeReminderSetting()
                           } else if (event.isChanged(rh.gs(SettingKeys.BUZZER_REMINDERS))) {
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
        return isConnected() && patchManager.isActivated
    }

    override fun isSuspended(): Boolean {
        return patchManager.patchState.isNormalBasalPaused
    }

    override fun isBusy(): Boolean {
        return false
    }

    override fun isConnected(): Boolean {
        return if (patchManager.isDeactivated) true else patchManager.patchConnectionState.isConnected
    }

    override fun isConnecting(): Boolean {
        return patchManager.patchConnectionState.isConnecting
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
        if (patchManager.isActivated) {
            if ("SMS" == reason) {
                aapsLogger.debug("Acknowledged AAPS getPumpStatus request it was requested through an SMS")
            } else {
                aapsLogger.debug("Acknowledged AAPS getPumpStatus request")
            }
            mDisposables.add(
                patchManager.updateConnection()
                    .subscribe(Consumer {
                        mLastDataTime = System.currentTimeMillis()
                    })
            )
        }
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        mLastDataTime = System.currentTimeMillis()
        if (patchManager.isActivated) {
            if (patchManager.patchState.isTempBasalActive) {
                val cancelResult = cancelTempBasal(true)
                if (!cancelResult.success) return PumpEnactResult(injector).isTempCancel(true).comment(app.aaps.core.ui.R.string.canceling_tbr_failed)
            }

            if (patchManager.patchState.isExtBolusActive) {
                val cancelResult = cancelExtendedBolus()
                if (!cancelResult.success) return PumpEnactResult(injector).comment(app.aaps.core.ui.R.string.canceling_eb_failed)
            }
            var isSuccess: Boolean? = null
            val result: BehaviorSubject<Boolean> = BehaviorSubject.create()
            val disposable = result.hide()
                .subscribe {
                    isSuccess = it
                }

            val nb = preferenceManager.getNormalBasalManager().convertProfileToNormalBasal(profile)
            mDisposables.add(
                patchManager.startBasal(nb)
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
            aapsLogger.info(LTag.PUMP, "Basal Profile was set: ${isSuccess ?: false}")
            return if (isSuccess == true) {
                uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
                PumpEnactResult(injector).success(true).enacted(true)
            } else {
                PumpEnactResult(injector)
            }
        } else {
            preferenceManager.getNormalBasalManager().setNormalBasal(profile)
            preferenceManager.flushNormalBasalManager()
            uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
            return PumpEnactResult(injector).success(true).enacted(true)
        }
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        // if (!patchManager.isActivated) {
        //     return true
        // }

        val ret = preferenceManager.getNormalBasalManager().isEqual(profile)
        aapsLogger.info(LTag.PUMP, "Is this profile set? $ret")
        return ret
    }

    override fun lastDataTime(): Long {
        return mLastDataTime
    }

    override val baseBasalRate: Double
        get() {
            if (!patchManager.isActivated || patchManager.patchState.isNormalBasalPaused) {
                return 0.0
            }

            return preferenceManager.getNormalBasalManager().normalBasal.getCurrentSegment()?.doseUnitPerHour?.toDouble() ?: 0.05
        }

    override val reservoirLevel: Double
        get() {
            if (!patchManager.isActivated) {
                return 0.0
            }

            return patchManager.patchState.remainedInsulin.toDouble()
        }

    override val batteryLevel: Int
        get() {
            return if (patchManager.isActivated) {
                patchManager.patchState.batteryLevel()
            } else {
                0
            }
        }

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

        mDisposables.add(patchManager.startCalculatorBolus(detailedBolusInfo)
                             .doOnSuccess {
                                 mLastDataTime = System.currentTimeMillis()
                             }.subscribe({
                                             result.onNext(it.isSuccess)
                                         }, {
                                             result.onNext(false)
                                         })
        )

        val tr = detailedBolusInfo.let {
            EventOverviewBolusProgress.Treatment(it.insulin, it.carbs.toInt(), it.bolusType === DetailedBolusInfo.BolusType.SMB, it.id)
        }

        do {
            SystemClock.sleep(100)
            if (patchManager.patchConnectionState.isConnected) {
                val delivering = patchManager.bolusCurrent.nowBolus.injected
                rxBus.send(EventOverviewBolusProgress.apply {
                    status = rh.gs(app.aaps.core.ui.R.string.bolus_delivering, delivering)
                    percent = min((delivering / detailedBolusInfo.insulin * 100).toInt(), 100)
                    t = tr
                })
            }
        } while (!patchManager.bolusCurrent.nowBolus.endTimeSynced && isSuccess)

        rxBus.send(EventOverviewBolusProgress.apply {
            status = rh.gs(app.aaps.core.ui.R.string.bolus_delivered_successfully, detailedBolusInfo.insulin)
            percent = 100
        })

        detailedBolusInfo.insulin = patchManager.bolusCurrent.nowBolus.injected.toDouble()
        patchManager.addBolusToHistory(detailedBolusInfo)

        disposable.dispose()

        return if (isSuccess && abs(askedInsulin - detailedBolusInfo.insulin) < pumpDescription.bolusStep)
            PumpEnactResult(injector).success(true).enacted(true).bolusDelivered(askedInsulin)
        else
            PumpEnactResult(injector).success(false)/*.enacted(false)*/.bolusDelivered(Round.roundTo(detailedBolusInfo.insulin, 0.01))
    }

    override fun stopBolusDelivering() {
        mDisposables.add(patchManager.stopNowBolus()
                             .subscribeOn(aapsSchedulers.io)
                             .observeOn(aapsSchedulers.main)
                             .subscribe { it ->
                                 rxBus.send(EventOverviewBolusProgress.apply {
                                     status = rh.gs(app.aaps.core.ui.R.string.bolus_delivered_successfully, (it.injectedBolusAmount * 0.05f))
                                 })
                             }
        )
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - absoluteRate: ${absoluteRate.toFloat()}, durationInMinutes: ${durationInMinutes.toLong()}, enforceNew: $enforceNew")
        if (patchManager.patchState.isNormalBasalAct) {
            mLastDataTime = System.currentTimeMillis()
            val tb = TempBasal.createAbsolute(durationInMinutes.toLong(), absoluteRate.toFloat())
            return patchManager.startTempBasal(tb)
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
                .map { PumpEnactResult(injector).success(true).enacted(true).duration(durationInMinutes).absolute(absoluteRate).isPercent(false).isTempCancel(false) }
                .onErrorReturnItem(
                    PumpEnactResult(injector).success(false).enacted(false)
                        .comment("Internal error")
                )
                .blockingGet()
        } else {
            aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - normal basal is not active")
            return PumpEnactResult(injector).success(false).enacted(false)
        }
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setTempBasalPercent - percent: $percent, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        if (patchManager.patchState.isNormalBasalAct && percent != 0) {
            mLastDataTime = System.currentTimeMillis()
            val tb = TempBasal.createPercent(durationInMinutes.toLong(), percent)
            return patchManager.startTempBasal(tb)
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
                .map { PumpEnactResult(injector).success(true).enacted(true).duration(durationInMinutes).percent(percent).isPercent(true).isTempCancel(false) }
                .onErrorReturnItem(
                    PumpEnactResult(injector).success(false).enacted(false)
                        .comment("Internal error")
                )
                .blockingGet()
        } else {
            aapsLogger.info(LTag.PUMP, "setTempBasalPercent - normal basal is not active")
            return PumpEnactResult(injector).success(false).enacted(false)
        }
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setExtendedBolus - insulin: $insulin, durationInMinutes: $durationInMinutes")

        return patchManager.startQuickBolus(0f, insulin.toFloat(), BolusExDuration.ofRaw(durationInMinutes))
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
            .map { PumpEnactResult(injector).success(true).enacted(true) }
            .onErrorReturnItem(
                PumpEnactResult(injector).success(false).enacted(false).bolusDelivered(0.0)
                    .comment(rh.gs(app.aaps.core.ui.R.string.error))
            )
            .blockingGet()
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val tbrCurrent = readTBR()

        if (tbrCurrent == null) {
            aapsLogger.debug(LTag.PUMP, "cancelTempBasal - TBR already false.")
            return PumpEnactResult(injector).success(true).enacted(false)
        }

        if (!patchManager.patchState.isTempBasalActive) {
            return if (pumpSync.expectedPumpState().temporaryBasal != null) {
                PumpEnactResult(injector).success(true).enacted(true).isTempCancel(true)
            } else
                PumpEnactResult(injector).success(true).isTempCancel(true)
        }

        return patchManager.stopTempBasal()
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
            .map { PumpEnactResult(injector).success(true).enacted(true).isTempCancel(true) }
            .onErrorReturnItem(
                PumpEnactResult(injector).success(false).enacted(false)
                    .comment(rh.gs(app.aaps.core.ui.R.string.error))
            )
            .blockingGet()
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        if (patchManager.patchState.isExtBolusActive) {
            return patchManager.stopExtBolus()
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
                .map { PumpEnactResult(injector).success(true).enacted(true).isTempCancel(true) }
                .onErrorReturnItem(
                    PumpEnactResult(injector).success(false).enacted(false)
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
                PumpEnactResult(injector).success(true).enacted(true).isTempCancel(true)
            } else
                PumpEnactResult(injector)
        }
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        val now = System.currentTimeMillis()
        val pumpJson = JSONObject()
        val battery = JSONObject()
        val status = JSONObject()
        val extended = JSONObject()
        try {
            battery.put("percent", 100)
            status.put("status", if (patchManager.patchState.isNormalBasalPaused) "suspended" else "normal")
            status.put("timestamp", dateUtil.toISOString(lastDataTime()))
            extended.put("Version", version)
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
            extended.put("BaseBasalRate", baseBasalRate)
            try {
                extended.put("ActiveProfile", profileFunction.getProfileName())
            } catch (e: Exception) {
                aapsLogger.error("Unhandled exception", e)
            }
            pumpJson.put("battery", battery)
            pumpJson.put("status", status)
            pumpJson.put("extended", extended)
            pumpJson.put("reservoir", patchManager.patchState.remainedInsulin.toInt())
            pumpJson.put("clock", dateUtil.toISOString(now))
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return pumpJson
    }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.Eoflow
    }

    override fun model(): PumpType {
        return PumpType.EOFLOW_EOPATCH2
    }

    override fun serialNumber(): String {
        return patchManager.patchConfig.patchSerialNumber
    }

    override val pumpDescription: PumpDescription
        get() = mPumpDescription

    override fun shortStatus(veryShort: Boolean): String {
        if (patchManager.isActivated) {
            var ret = ""
            val activeTemp = pumpSync.expectedPumpState().temporaryBasal
            if (activeTemp != null)
                ret += "Temp: ${activeTemp.rate} U/hr"

            val activeExtendedBolus = pumpSync.expectedPumpState().extendedBolus
            if (activeExtendedBolus != null)
                ret += "Extended: ${activeExtendedBolus.amount} U\n"

            val reservoirStr = patchManager.patchState.remainedInsulin.let {
                when {
                    it > 50f -> "50+ U"
                    it < 1f  -> "0 U"
                    else     -> "${it.roundToInt()} U"
                }
            }

            ret += "Reservoir: $reservoirStr"
            ret += "Battery: ${patchManager.patchState.batteryLevel()}"
            return ret
        } else {
            return "EOPatch is not enabled."
        }
    }

    override val isFakingTempsByExtendedBoluses: Boolean = false

    override fun loadTDDs(): PumpEnactResult {
        return PumpEnactResult(injector)
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
}