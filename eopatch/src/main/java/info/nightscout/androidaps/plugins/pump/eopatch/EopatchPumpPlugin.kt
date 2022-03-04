package info.nightscout.androidaps.plugins.pump.eopatch

import android.os.SystemClock
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventAppInitialized
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPatchManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager
import info.nightscout.androidaps.plugins.pump.eopatch.code.BolusExDuration
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys
import info.nightscout.androidaps.plugins.pump.eopatch.extension.takeOne
import info.nightscout.androidaps.plugins.pump.eopatch.ui.EopatchOverviewFragment
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.TimeChangeType
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
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
    private val preferenceManager: IPreferenceManager
):PumpPluginBase(PluginDescription()
    .mainType(PluginType.PUMP)
    .fragmentClass(EopatchOverviewFragment::class.java.name)
    .pluginIcon(R.drawable.ic_eopatch2_128)
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
        mDisposables.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventPreferenceChange ->
                if (event.isChanged(rh, SettingKeys.LOW_RESERVOIR_REMINDERS) || event.isChanged(rh, SettingKeys.EXPIRATION_REMINDERS)) {
                    patchManager.changeReminderSetting()
                } else if (event.isChanged(rh, SettingKeys.BUZZER_REMINDERS)) {
                    patchManager.changeBuzzerSetting()
                }
            }) { throwable: Throwable -> fabricPrivacy.logException(throwable) }
        )

        mDisposables.add(rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                aapsLogger.debug(LTag.PUMP,"EventAppInitialized")
                preferenceManager.init()
                patchManager.init()
                alarmManager.init()
            }) { throwable: Throwable -> fabricPrivacy.logException(throwable) }
        )
    }

    override fun specialEnableCondition(): Boolean {
        //BG -> FG 시 패치 활성화 재진행 및 미처리 알람 발생
        if(preferenceManager.isInitDone()) {
            patchManager.checkActivationProcess()
            alarmManager.restartAll()
        }
        return super.specialEnableCondition()
    }

    override fun specialShowInListCondition(): Boolean {
        return super.specialShowInListCondition()
    }

    override fun onStop() {
        super.onStop()
        aapsLogger.debug(LTag.PUMP, "EOPatchPumpPlugin onStop()")
    }

    override fun onStateChange(type: PluginType?, oldState: State?, newState: State?) {
        super.onStateChange(type, oldState, newState)
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
    }

    override fun updatePreferenceSummary(pref: Preference) {
        super.updatePreferenceSummary(pref)
    }

    override fun isUnreachableAlertTimeoutExceeded(alertTimeoutMilliseconds: Long): Boolean {
        return super.isUnreachableAlertTimeoutExceeded(alertTimeoutMilliseconds)
    }

    override fun setNeutralTempAtFullHour(): Boolean {
        return super.setNeutralTempAtFullHour()
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
        return patchManager.patchConnectionState.isConnected
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
        aapsLogger.debug(LTag.PUMP,"EOPatch connect - reason:$reason")
        mLastDataTime = System.currentTimeMillis()
    }

    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP,"EOPatch disconnect - reason:$reason")
    }

    override fun stopConnecting() {
    }

    override fun getPumpStatus(reason: String) {
        if (patchManager.isActivated) {
            if ("SMS" == reason) {
                aapsLogger.debug("Acknowledged AAPS getPumpStatus request it was requested through an SMS")
            }else{
                aapsLogger.debug("Acknowledged AAPS getPumpStatus request")
            }
            mDisposables.add(patchManager.updateConnection()
                .subscribe(Consumer {
                    mLastDataTime = System.currentTimeMillis()
                })
            )
        }
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        mLastDataTime = System.currentTimeMillis()
        if(patchManager.isActivated){
            if(patchManager.patchState.isTempBasalActive || patchManager.patchState.isBolusActive){
                return PumpEnactResult(injector)
            }else{
                var isSuccess: Boolean? = null
                val result: BehaviorSubject<Boolean> = BehaviorSubject.create()
                val disposable = result.hide()
                    .subscribe {
                        isSuccess = it
                    }

                val nb = preferenceManager.getNormalBasalManager().convertProfileToNormalBasal(profile)
                mDisposables.add(patchManager.startBasal(nb)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ response ->
                        result.onNext(response.isSuccess)
                    }, {
                        result.onNext(false)
                    })
                )

                do{
                    SystemClock.sleep(100)
                }while(isSuccess == null)

                disposable.dispose()
                aapsLogger.info(LTag.PUMP, "Basal Profile was set: ${isSuccess?:false}")
                return PumpEnactResult(injector).apply{ success = isSuccess?:false }
            }
        }else{
            preferenceManager.getNormalBasalManager().setNormalBasal(profile)
            preferenceManager.flushNormalBasalManager()
            return PumpEnactResult(injector)
        }
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        if (!patchManager.isActivated) {
            return true
        }

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

        return preferenceManager.getNormalBasalManager().normalBasal.getCurrentSegment()?.doseUnitPerHour?.toDouble()?:0.05
    }

    override val reservoirLevel: Double
    get() {
        if (!patchManager.isActivated) {
            return 0.0
        }
        val reservoirLevel = patchManager.patchState.remainedInsulin.toDouble()

        return (reservoirLevel > 50.0).takeOne(50.0, reservoirLevel)
    }

    override val batteryLevel: Int
    get() {
        return if(patchManager.isActivated) {
            patchManager.patchState.batteryLevel()
        }else{
            0
        }
    }

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {

        if (detailedBolusInfo.insulin == 0.0 && detailedBolusInfo.carbs == 0.0) {
            // neither carbs nor bolus requested
            aapsLogger.error("deliverTreatment: Invalid input: neither carbs nor insulin are set in treatment")
            return PumpEnactResult(injector).success(false).enacted(false).bolusDelivered(0.0).carbsDelivered(0.0)
                    .comment(rh.gs(R.string.invalidinput))
        } else if (detailedBolusInfo.insulin > 0.0) {
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

            do{
                SystemClock.sleep(100)
                if(patchManager.patchConnectionState.isConnected) {
                    val delivering = patchManager.bolusCurrent.nowBolus.injected
                    rxBus.send(EventOverviewBolusProgress.apply {
                        status = rh.gs(R.string.bolusdelivering, delivering)
                        percent = min((delivering / detailedBolusInfo.insulin * 100).toInt(), 100)
                    })
                }
            }while(!patchManager.bolusCurrent.nowBolus.endTimeSynced && isSuccess)

            rxBus.send(EventOverviewBolusProgress.apply {
                status = rh.gs(R.string.bolusdelivered, detailedBolusInfo.insulin)
                percent = 100
            })

            detailedBolusInfo.insulin = patchManager.bolusCurrent.nowBolus.injected.toDouble()
            patchManager.addBolusToHistory(detailedBolusInfo)

            disposable.dispose()

            return if(isSuccess)
                PumpEnactResult(injector).success(true)/*.enacted(true)*/.carbsDelivered(detailedBolusInfo.carbs).bolusDelivered(detailedBolusInfo.insulin)
            else
                PumpEnactResult(injector).success(false)/*.enacted(false)*/.carbsDelivered(0.0).bolusDelivered(detailedBolusInfo.insulin)

        } else {
            // no bolus required, carb only treatment
            patchManager.addBolusToHistory(detailedBolusInfo)

            return PumpEnactResult(injector).success(true).enacted(true).bolusDelivered(0.0)
                    .carbsDelivered(detailedBolusInfo.carbs).comment(rh.gs(info.nightscout.androidaps.core.R.string.ok))
        }
    }

    override fun stopBolusDelivering() {
        mDisposables.add(patchManager.stopNowBolus()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { it ->
                rxBus.send(EventOverviewBolusProgress.apply {
                    status = rh.gs(R.string.bolusdelivered, (it.injectedBolusAmount * 0.05f))
                })
            }
        )
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - absoluteRate: ${absoluteRate.toFloat()}, durationInMinutes: ${durationInMinutes.toLong()}, enforceNew: $enforceNew")
        if(patchManager.patchState.isNormalBasalAct){
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
                    aapsLogger.info(LTag.PUMP,"setTempBasalAbsolute - tbrCurrent:${readTBR()}")
                }
                .map { PumpEnactResult(injector).success(true).enacted(true).duration(durationInMinutes).absolute(absoluteRate).isPercent(false).isTempCancel(false) }
                .onErrorReturnItem(PumpEnactResult(injector).success(false).enacted(false)
                    .comment("Internal error"))
                .blockingGet()
        }else{
            aapsLogger.info(LTag.PUMP,"setTempBasalAbsolute - normal basal is not active")
            return PumpEnactResult(injector).success(false).enacted(false)
        }
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP,"setTempBasalPercent - percent: $percent, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        if(patchManager.patchState.isNormalBasalAct && percent != 0){
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
                    aapsLogger.info(LTag.PUMP,"setTempBasalPercent - tbrCurrent:${readTBR()}")
                }
                .map { PumpEnactResult(injector).success(true).enacted(true).duration(durationInMinutes).percent(percent).isPercent(true).isTempCancel(false) }
                .onErrorReturnItem(PumpEnactResult(injector).success(false).enacted(false)
                    .comment("Internal error"))
                .blockingGet()
        }else{
            aapsLogger.info(LTag.PUMP,"setTempBasalPercent - normal basal is not active")
            return PumpEnactResult(injector).success(false).enacted(false)
        }
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        aapsLogger.info(LTag.PUMP,"setExtendedBolus - insulin: $insulin, durationInMinutes: $durationInMinutes")

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
            .map { PumpEnactResult(injector).success(true).enacted(true)}
            .onErrorReturnItem(PumpEnactResult(injector).success(false).enacted(false).bolusDelivered(0.0)
                .comment(rh.gs(info.nightscout.androidaps.core.R.string.error)))
            .blockingGet()
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val tbrCurrent = readTBR()

       if (tbrCurrent == null ) {
           aapsLogger.debug(LTag.PUMP,"cancelTempBasal - TBR already false.")
           return PumpEnactResult(injector).success(true).enacted(false)
       }

       if (!patchManager.patchState.isTempBasalActive) {
           return if (pumpSync.expectedPumpState().temporaryBasal != null) {
               PumpEnactResult(injector).success(true).enacted(true).isTempCancel(true)
           }else
               PumpEnactResult(injector).success(true).isTempCancel(true)
        }

        return patchManager.stopTempBasal()
            .doOnSuccess {
                mLastDataTime = System.currentTimeMillis()
                aapsLogger.debug(LTag.PUMP,"cancelTempBasal - $it")
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    timestamp = dateUtil.now(),
                    endPumpId = dateUtil.now(),
                    pumpType = PumpType.EOFLOW_EOPATCH2,
                    pumpSerial = serialNumber()
                )
            }
            .doOnError{
                aapsLogger.error(LTag.PUMP,"cancelTempBasal() - $it")
            }
            .map { PumpEnactResult(injector).success(true).enacted(true).isTempCancel(true)}
            .onErrorReturnItem(PumpEnactResult(injector).success(false).enacted(false)
                .comment(rh.gs(info.nightscout.androidaps.core.R.string.error)))
            .blockingGet()
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        if(patchManager.patchState.isExtBolusActive){
            return patchManager.stopExtBolus()
                .doOnSuccess {
                    aapsLogger.debug(LTag.PUMP,"cancelExtendedBolus - success")
                    mLastDataTime = System.currentTimeMillis()
                    pumpSync.syncStopExtendedBolusWithPumpId(
                        timestamp = dateUtil.now(),
                        endPumpId = dateUtil.now(),
                        pumpType = PumpType.EOFLOW_EOPATCH2,
                        pumpSerial = serialNumber()
                    )
                }
                .map { PumpEnactResult(injector).success(true).enacted(true).isTempCancel(true)}
                .onErrorReturnItem(PumpEnactResult(injector).success(false).enacted(false)
                    .comment(rh.gs(info.nightscout.androidaps.core.R.string.error)))
                .blockingGet()
        }else{
            aapsLogger.debug(LTag.PUMP,"cancelExtendedBolus - nothing stops")
            return if (pumpSync.expectedPumpState().extendedBolus != null) {
                pumpSync.syncStopExtendedBolusWithPumpId(
                    timestamp = dateUtil.now(),
                    endPumpId = dateUtil.now(),
                    pumpType = PumpType.EOFLOW_EOPATCH2,
                    pumpSerial = serialNumber()
                )
                PumpEnactResult(injector).success(true).enacted(true).isTempCancel(true)
            }else
                PumpEnactResult(injector)
        }
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        return JSONObject()
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
        if(patchManager.isActivated) {
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
        }else{
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