package app.aaps.pump.eopatch.ble

import android.content.Context
import android.content.Intent
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.RxAction
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.core.scan.IPatchScanner
import app.aaps.pump.eopatch.core.scan.PatchScanner
import app.aaps.pump.eopatch.core.scan.ScanList
import app.aaps.pump.eopatch.event.EventPatchActivationNotComplete
import app.aaps.pump.eopatch.keys.EopatchBooleanKey
import app.aaps.pump.eopatch.keys.EopatchIntKey
import app.aaps.pump.eopatch.ui.DialogHelperActivity
import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchState
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatchManager @Inject constructor(
    private val aapsPatchManager: PatchManagerExecutor,
    private val pm: PreferenceManager,
    private val alarms: Alarms,
    private val patchConfig: PatchConfig,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val context: Context,
    private val preferences: Preferences,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val rxAction: RxAction,
    private val aapsSchedulers: AapsSchedulers,
    private val alarmRegistry: IAlarmRegistry
) : IPatchManager {

    private val compositeDisposable = CompositeDisposable()

    private var patchScanner: IPatchScanner = PatchScanner(context)
    private var mConnectingDisposable: Disposable? = null

    @Inject
    fun onInit() {
        compositeDisposable.add(
            aapsPatchManager.observePatchConnectionState()
                .subscribe(Consumer { bleConnectionState: BleConnectionState ->
                    when (bleConnectionState) {
                        BleConnectionState.DISCONNECTED -> {
                            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                            rxBus.send(EventRefreshOverview("Eopatch connection state: " + bleConnectionState.name, true))
                            rxBus.send(EventCustomActionsChanged())
                            stopObservingConnection()
                        }

                        BleConnectionState.CONNECTED    -> {
                            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                            rxBus.send(EventRefreshOverview("Eopatch connection state: " + bleConnectionState.name, true))
                            rxBus.send(EventCustomActionsChanged())
                            stopObservingConnection()
                        }

                        BleConnectionState.CONNECTING   -> mConnectingDisposable = Observable.interval(0, 1, TimeUnit.SECONDS)
                            .observeOn(aapsSchedulers.main)
                            .takeUntil(Predicate { n: Long -> aapsPatchManager.patchConnectionState.isConnected || n > 10 * 60 })
                            .subscribe(Consumer { n: Long -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, n.toInt())) })

                        else                            -> stopObservingConnection()
                    }
                })
        )
        compositeDisposable.add(
            rxBus
                .toObservable<EventPatchActivationNotComplete>(EventPatchActivationNotComplete::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribeOn(aapsSchedulers.main)
                .subscribe(Consumer {
                    val i = Intent(context, DialogHelperActivity::class.java)
                    i.putExtra("title", rh.gs(R.string.patch_activate_reminder_title))
                    i.putExtra("message", rh.gs(R.string.patch_activate_reminder_desc))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                })
        )
    }

    override fun init() {
        setConnection()
    }

    private fun stopObservingConnection() {
        if (mConnectingDisposable != null) {
            mConnectingDisposable!!.dispose()
            mConnectingDisposable = null
        }
    }

    override fun updatePatchState(state: PatchState) {
        pm.patchState.update(state)
        pm.flushPatchState()
    }

    override fun setConnection() {
        if (patchConfig.hasMacAddress()) {
            aapsPatchManager.updateMacAddress(patchConfig.macAddress!!, false)
        }
    }

    override fun patchActivation(timeout: Long): Single<Boolean> {
        return aapsPatchManager.patchActivation(timeout)
            .doOnSuccess(Consumer { success: Boolean ->
                if (success) {
                    pumpSync.connectNewPump(true)
                    Thread.sleep(1000)
                    pumpSync.insertTherapyEventIfNewWithTimestamp(
                        System.currentTimeMillis(),
                        TE.Type.CANNULA_CHANGE,
                        null,
                        null,
                        PumpType.EOFLOW_EOPATCH2,
                        patchConfig.patchSerialNumber
                    )
                    pumpSync.insertTherapyEventIfNewWithTimestamp(
                        System.currentTimeMillis(),
                        TE.Type.INSULIN_CHANGE,
                        null,
                        null,
                        PumpType.EOFLOW_EOPATCH2,
                        patchConfig.patchSerialNumber
                    )
                }
            })
    }

    override fun scan(timeout: Long): Single<ScanList> {
        aapsPatchManager.updateMacAddress("", false)
        patchConfig.macAddress = ""
        return patchScanner.scan(timeout)
    }

    override fun addBolusToHistory(originalDetailedBolusInfo: DetailedBolusInfo) {
        val detailedBolusInfo = originalDetailedBolusInfo.copy()

        if (detailedBolusInfo.insulin > 0) {
            pumpSync.syncBolusWithPumpId(
                dateUtil.now(),  // Use real timestamp to have it different from carbs (otherwise NS sync fail)
                PumpInsulin(detailedBolusInfo.insulin),
                detailedBolusInfo.bolusType,
                dateUtil.now(),
                PumpType.EOFLOW_EOPATCH2,
                patchConfig.patchSerialNumber
            )
        }
    }

    override fun changeBuzzerSetting() {
        val buzzer = preferences.get(EopatchBooleanKey.BuzzerReminder)
        if (patchConfig.infoReminder != buzzer) {
            if (patchConfig.isActivated) {
                compositeDisposable.add(
                    aapsPatchManager.infoReminderSet(buzzer)
                        .observeOn(aapsSchedulers.main)
                        .subscribe(Consumer {
                            patchConfig.infoReminder = buzzer
                            pm.flushPatchConfig()
                        })
                )
            } else {
                patchConfig.infoReminder = buzzer
                pm.flushPatchConfig()
            }
        }
    }

    override fun changeReminderSetting() {
        val doseUnit = preferences.get(EopatchIntKey.LowReservoirReminder)
        val hours = preferences.get(EopatchIntKey.ExpirationReminder)
        val pc: PatchConfig = patchConfig
        if (pc.lowReservoirAlertAmount != doseUnit || pc.patchExpireAlertTime != hours) {
            if (patchConfig.isActivated) {
                compositeDisposable.add(
                    aapsPatchManager.setLowReservoir(doseUnit, hours)
                        .observeOn(aapsSchedulers.main)
                        .doOnSubscribe(Consumer {
                            if (pc.patchExpireAlertTime != hours) {
                                Maybe.just<AlarmCode>(AlarmCode.B000)
                                    .flatMap<AlarmCode>(Function { alarmCode: AlarmCode -> alarmRegistry.remove(alarmCode) })
                                    .flatMap<AlarmCode>(Function { alarmCode: AlarmCode -> alarmRegistry.add(alarmCode, (pc.expireTimestamp - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours.toLong())), false) })
                                    .subscribe()
                            }
                        })
                        .subscribe(Consumer {
                            pc.lowReservoirAlertAmount = doseUnit
                            pc.patchExpireAlertTime = hours
                            pm.flushPatchConfig()
                        })
                )
            } else {
                pc.lowReservoirAlertAmount = doseUnit
                pc.patchExpireAlertTime = hours
                pm.flushPatchConfig()
            }
        }
    }

    override fun checkActivationProcess() {
        if (patchConfig.lifecycleEvent.isSubStepRunning
            && !alarms.isOccurring(AlarmCode.A005)
            && !alarms.isOccurring(AlarmCode.A020)
        ) {
            rxAction.runOnMainThread(Runnable { rxBus.send(EventPatchActivationNotComplete()) })
        }
    }
}
