package app.aaps.pump.eopatch.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.code.EventType
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.ui.EoBaseNavigator
import app.aaps.pump.eopatch.ui.event.SingleLiveEvent
import app.aaps.pump.eopatch.ui.event.UIEvent
import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchState
import app.aaps.pump.eopatch.vo.TempBasalManager
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

class EopatchOverviewViewModel @Inject constructor(
    private val rh: ResourceHelper,
    val patchManager: IPatchManager,
    private val patchManagerExecutor: PatchManagerExecutor,
    private val patchConfigData: PatchConfig,
    private val tempBasalManager: TempBasalManager,
    private val normalBasalManager: NormalBasalManager,
    val preferenceManager: PreferenceManager,
    private val profileFunction: ProfileFunction,
    private val aapsSchedulers: AapsSchedulers,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync
) : EoBaseViewModel<EoBaseNavigator>() {

    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler: LiveData<UIEvent<EventType>>
        get() = _eventHandler

    private val _patchConfig = SingleLiveEvent<PatchConfig>()
    val patchConfig: LiveData<PatchConfig>
        get() = _patchConfig

    private val _patchState = SingleLiveEvent<PatchState>()
    val patchState: LiveData<PatchState>
        get() = _patchState

    private val _normalBasal = SingleLiveEvent<String>()
    val normalBasal: LiveData<String>
        get() = _normalBasal

    private val _tempBasal = SingleLiveEvent<String>()
    val tempBasal: LiveData<String>
        get() = _tempBasal

    private val _bleStatus = SingleLiveEvent<String>()
    val bleStatus: LiveData<String>
        get() = _bleStatus

    private val _status = SingleLiveEvent<String>()
    val status: LiveData<String>
        get() = _status

    private val _pauseBtnStr = SingleLiveEvent<String>()
    val pauseBtnStr: LiveData<String>
        get() = _pauseBtnStr

    private val _alarms = SingleLiveEvent<Alarms>()
    val alarms: LiveData<Alarms>
        get() = _alarms

    private val _patchRemainingInsulin = MutableLiveData(0f)

    private var mPauseTimeDisposable: Disposable? = null
    private var mBasalRateDisposable: Disposable? = null

    val patchRemainingInsulin: LiveData<String>
        get() = _patchRemainingInsulin.map { insulin ->
            when {
                insulin > 50f -> "50+ U"
                insulin < 1f  -> "0 U"
                else          -> "${insulin.roundToInt()} U"
            }
        }

    val isPatchConnected: Boolean
        get() = patchManagerExecutor.patchConnectionState.isConnected

    init {
        preferenceManager.observePatchConfig()
            .observeOn(aapsSchedulers.main)
            .subscribe { _patchConfig.value = it }
            .addTo()

        preferenceManager.observePatchState()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                _patchState.value = it
                _patchRemainingInsulin.value = it.remainedInsulin
                updateBasalInfo()
                updatePatchStatus()
            }
            .addTo()

        patchManagerExecutor.observePatchConnectionState()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                _bleStatus.value = when (it) {
                    BleConnectionState.CONNECTED    -> "{fa-bluetooth}"
                    BleConnectionState.DISCONNECTED -> "{fa-bluetooth-b}"
                    else                            -> "{fa-bluetooth-b spin}  ${rh.gs(R.string.string_connecting)}"
                }
            }
            .addTo()

        preferenceManager.observePatchLifeCycle()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                updatePatchStatus()
            }
            .addTo()

        preferenceManager.observeAlarm()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                _alarms.value = it
            }
            .addTo()

        if (preferenceManager.patchState.isNormalBasalPaused) {
            startPauseTimeUpdate()
        } else {
            updateBasalInfo()
        }
    }

    private fun updatePatchStatus() {
        if (patchConfigData.isActivated) {
            val finishTimeMillis = patchConfig.value?.basalPauseFinishTimestamp ?: System.currentTimeMillis()
            val remainTimeMillis = max(finishTimeMillis - System.currentTimeMillis(), 0L)
            val h = TimeUnit.MILLISECONDS.toHours(remainTimeMillis)
            val m = TimeUnit.MILLISECONDS.toMinutes(remainTimeMillis - TimeUnit.HOURS.toMillis(h))
            _status.value = if (preferenceManager.patchState.isNormalBasalPaused)
                "${rh.gs(R.string.string_suspended)}\n${rh.gs(R.string.string_temp_basal_remained_hhmm, h.toString(), m.toString())}"
            else
                rh.gs(R.string.string_running)
        } else {
            _status.value = ""
        }
        _pauseBtnStr.value = if (preferenceManager.patchState.isNormalBasalPaused) rh.gs(R.string.string_resume) else rh.gs(R.string.string_suspend)
    }

    private fun updateBasalInfo() {
        if (patchConfigData.isActivated) {
            _normalBasal.value = if (preferenceManager.patchState.isNormalBasalRunning)
                "${normalBasalManager.normalBasal.currentSegmentDoseUnitPerHour} U/hr"
            else
                ""
            _tempBasal.value = if (preferenceManager.patchState.isTempBasalActive)
                "${tempBasalManager.startedBasal?.doseUnitPerHour} U/hr"
            else
                ""

        } else {
            _normalBasal.value = ""
            _tempBasal.value = ""
        }
    }

    fun onClickActivation() {
        val profile = profileFunction.getProfile()
        if (profile == null) {
            _eventHandler.postValue(UIEvent(EventType.PROFILE_NOT_SET))
        } else {
            val basalValues = profile.getBasalValues()
            var isValid = true
            for (basalRate in basalValues) {
                if (basalRate.value < 0.049999) {
                    _eventHandler.postValue(UIEvent(EventType.INVALID_BASAL_RATE))
                    isValid = false
                    break
                }
            }

            if (isValid) {
                normalBasalManager.setNormalBasal(profile)
                preferenceManager.flushNormalBasalManager()

                _eventHandler.postValue(UIEvent(EventType.ACTIVATION_CLICKED))
            }
        }
    }

    fun onClickDeactivation() {
        _eventHandler.postValue(UIEvent(EventType.DEACTIVATION_CLICKED))
    }

    fun onClickSuspendOrResume() {
        if (preferenceManager.patchState.isNormalBasalPaused) {
            _eventHandler.postValue(UIEvent(EventType.RESUME_CLICKED))
        } else {
            _eventHandler.postValue(UIEvent(EventType.SUSPEND_CLICKED))
        }
    }

    fun pauseBasal(pauseDurationHour: Float) {
        patchManagerExecutor.pauseBasal(pauseDurationHour)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ response ->
                           if (response.isSuccess) {
                               val result = pumpSync.syncTemporaryBasalWithPumpId(
                                   timestamp = dateUtil.now(),
                                   rate = 0.0,
                                   duration = T.mins((pauseDurationHour * 60).toLong()).msecs(),
                                   isAbsolute = true,
                                   type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                                   pumpId = dateUtil.now(),
                                   pumpType = PumpType.EOFLOW_EOPATCH2,
                                   pumpSerial = patchConfigData.patchSerialNumber
                               )
                               aapsLogger.debug(LTag.PUMP, "syncTemporaryBasalWithPumpId: Result: $result")

                               UIEvent(EventType.PAUSE_BASAL_SUCCESS).let { _eventHandler.postValue(it) }
                               startPauseTimeUpdate()
                           } else {
                               UIEvent(EventType.PAUSE_BASAL_FAILED).apply { value = pauseDurationHour }.let { _eventHandler.postValue(it) }
                           }
                       }, {
                           UIEvent(EventType.PAUSE_BASAL_FAILED).apply { value = pauseDurationHour }.let { _eventHandler.postValue(it) }
                       }).addTo()
    }

    fun resumeBasal() {
        patchManagerExecutor.resumeBasal()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.isSuccess) {
                               pumpSync.syncStopTemporaryBasalWithPumpId(
                                   timestamp = dateUtil.now(),
                                   endPumpId = dateUtil.now(),
                                   pumpType = PumpType.EOFLOW_EOPATCH2,
                                   pumpSerial = patchConfigData.patchSerialNumber
                               )
                               UIEvent(EventType.RESUME_BASAL_SUCCESS).let { event -> _eventHandler.postValue(event) }
                               stopPauseTimeUpdate()
                           } else {
                               _eventHandler.postValue(UIEvent(EventType.RESUME_BASAL_FAILED))
                           }
                       }, {
                           _eventHandler.postValue(UIEvent(EventType.RESUME_BASAL_FAILED))
                       }).addTo()
    }

    private fun startPauseTimeUpdate() {
        if (mPauseTimeDisposable == null) {
            mPauseTimeDisposable = Observable.interval(30, TimeUnit.SECONDS)
                .observeOn(aapsSchedulers.main)
                .subscribe { updatePatchStatus() }
        }
    }

    private fun stopPauseTimeUpdate() {
        mPauseTimeDisposable?.dispose()
        mPauseTimeDisposable = null
    }

    fun startBasalRateUpdate() {
        val initialDelaySecs = Calendar.getInstance().let { c ->
            (60 - c.get(Calendar.MINUTE) - 1) * 60 + (60 - c.get(Calendar.SECOND))
        }
        if (mBasalRateDisposable == null) {
            mBasalRateDisposable = Observable.interval(initialDelaySecs.toLong(), 3600L, TimeUnit.SECONDS)
                .observeOn(aapsSchedulers.main)
                .subscribe { updateBasalInfo() }
        }
        updateBasalInfo()
    }

    fun stopBasalRateUpdate() {
        mBasalRateDisposable?.dispose()
        mBasalRateDisposable = null
    }
}