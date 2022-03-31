package info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPatchManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager
import info.nightscout.androidaps.plugins.pump.eopatch.code.EventType
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.BleConnectionState
import info.nightscout.androidaps.plugins.pump.eopatch.ui.EoBaseNavigator
import info.nightscout.androidaps.plugins.pump.eopatch.ui.event.SingleLiveEvent
import info.nightscout.androidaps.plugins.pump.eopatch.ui.event.UIEvent
import info.nightscout.androidaps.plugins.pump.eopatch.vo.Alarms
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchConfig
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

class EopatchOverviewViewModel @Inject constructor(
    private val rh: ResourceHelper,
    val patchManager: IPatchManager,
    private val preferenceManager: IPreferenceManager,
    private val profileFunction: ProfileFunction,
    private val aapsSchedulers: AapsSchedulers
) : EoBaseViewModel<EoBaseNavigator>() {
    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler : LiveData<UIEvent<EventType>>
        get() = _eventHandler

    private val _patchConfig = SingleLiveEvent<PatchConfig>()
    val patchConfig : LiveData<PatchConfig>
        get() = _patchConfig

    private val _patchState = SingleLiveEvent<PatchState>()
    val patchState : LiveData<PatchState>
        get() = _patchState

    private val _normalBasal = SingleLiveEvent<String>()
    val normalBasal : LiveData<String>
        get() = _normalBasal

    private val _tempBasal = SingleLiveEvent<String>()
    val tempBasal : LiveData<String>
        get() = _tempBasal

    private val _bleStatus = SingleLiveEvent<String>()
    val bleStatus : LiveData<String>
        get() = _bleStatus

    private val _status = SingleLiveEvent<String>()
    val status : LiveData<String>
        get() = _status

    private val _pauseBtnStr = SingleLiveEvent<String>()
    val pauseBtnStr : LiveData<String>
        get() = _pauseBtnStr

    private val _alarms = SingleLiveEvent<Alarms>()
    val alarms : LiveData<Alarms>
        get() = _alarms

    private val _patchRemainingInsulin = MutableLiveData(0f)

    private var mPauseTimeDisposable: Disposable? = null
    private var mBasalRateDisposable: Disposable? = null

    val patchRemainingInsulin: LiveData<String>
        get() = Transformations.map(_patchRemainingInsulin) { insulin ->
            when {
                insulin > 50f -> "50+ U"
                insulin < 1f -> "0 U"
                else -> "${insulin.roundToInt()} U"
            }
        }

    val isPatchConnected: Boolean
        get() = patchManager.patchConnectionState.isConnected

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

        patchManager.observePatchConnectionState()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                _bleStatus.value = when(it){
                    BleConnectionState.CONNECTED -> "{fa-bluetooth}"
                    BleConnectionState.DISCONNECTED -> "{fa-bluetooth-b}"
                    else -> "{fa-bluetooth-b spin}  ${rh.gs(R.string.string_connecting)}"
                }
            }
            .addTo()

        patchManager.observePatchLifeCycle()
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

        if(preferenceManager.getPatchState().isNormalBasalPaused){
            startPauseTimeUpdate()
        }else {
            updateBasalInfo()
        }
    }

    private fun updatePatchStatus(){
        if(patchManager.isActivated){
            val finishTimeMillis = patchConfig.value?.basalPauseFinishTimestamp?:System.currentTimeMillis()
            val remainTimeMillis = max(finishTimeMillis - System.currentTimeMillis(), 0L)
            val h =  TimeUnit.MILLISECONDS.toHours(remainTimeMillis)
            val m =  TimeUnit.MILLISECONDS.toMinutes(remainTimeMillis - TimeUnit.HOURS.toMillis(h))
            _status.value = if(patchManager.patchState.isNormalBasalPaused)
                    "${rh.gs(R.string.string_suspended)}\n${rh.gs(R.string.string_temp_basal_remained_hhmm, h.toString(), m.toString())}"
                else
                    rh.gs(R.string.string_running)
        }else{
            _status.value = ""
        }
        _pauseBtnStr.value = if(patchManager.patchState.isNormalBasalPaused) rh.gs(R.string.string_resume) else rh.gs(R.string.string_suspend)
    }

    private fun updateBasalInfo(){
        if(patchManager.isActivated){
            _normalBasal.value = if(patchManager.patchState.isNormalBasalRunning)
                "${preferenceManager.getNormalBasalManager().normalBasal.currentSegmentDoseUnitPerHour} U/hr"
            else
                ""
            _tempBasal.value = if(patchManager.patchState.isTempBasalActive)
                "${preferenceManager.getTempBasalManager().startedBasal?.doseUnitPerHour} U/hr"
            else
                ""

        }else{
            _normalBasal.value = ""
            _tempBasal.value = ""
        }
    }

    fun onClickActivation(){
        val profile = profileFunction.getProfile()

        if(profile != null && profile.getBasal() >= 0.05) {
            patchManager.preferenceManager.getNormalBasalManager().setNormalBasal(profile)
            patchManager.preferenceManager.flushNormalBasalManager()

            _eventHandler.postValue(UIEvent(EventType.ACTIVATION_CLICKED))
        }else if(profile != null && profile.getBasal() < 0.05){
            _eventHandler.postValue(UIEvent(EventType.INVALID_BASAL_RATE))
        }else{
            _eventHandler.postValue(UIEvent(EventType.PROFILE_NOT_SET))
        }
    }

    fun onClickDeactivation(){
        _eventHandler.postValue(UIEvent(EventType.DEACTIVATION_CLICKED))
    }

    fun onClickSuspendOrResume(){
        if(patchManager.patchState.isNormalBasalPaused) {
            _eventHandler.postValue(UIEvent(EventType.RESUME_CLICKED))
        }else{
            _eventHandler.postValue(UIEvent(EventType.SUSPEND_CLICKED))
        }
    }

    fun pauseBasal(pauseDurationHour: Float){
        patchManager.pauseBasal(pauseDurationHour)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ response ->
                if (response.isSuccess) {
                    navigator?.toast(R.string.string_suspended_insulin_delivery_message)
                    startPauseTimeUpdate()
                } else {
                    UIEvent(EventType.PAUSE_BASAL_FAILED).apply { value = pauseDurationHour }.let { _eventHandler.postValue(it) }
                }
            }, {
                UIEvent(EventType.PAUSE_BASAL_FAILED).apply { value = pauseDurationHour }.let { _eventHandler.postValue(it) }
            }).addTo()
    }

    fun resumeBasal() {
        patchManager.resumeBasal()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                if (it.isSuccess) {
                    navigator?.toast(R.string.string_resumed_insulin_delivery_message)
                    stopPauseTimeUpdate()
                } else {
                    _eventHandler.postValue(UIEvent(EventType.RESUME_BASAL_FAILED))
                }
            },{
                _eventHandler.postValue(UIEvent(EventType.RESUME_BASAL_FAILED))
            }).addTo()
    }

    private fun startPauseTimeUpdate(){
        if(mPauseTimeDisposable == null) {
            mPauseTimeDisposable = Observable.interval(30, TimeUnit.SECONDS)
                .observeOn(aapsSchedulers.main)
                .subscribe { updatePatchStatus() }
        }
    }

    private fun stopPauseTimeUpdate(){
        mPauseTimeDisposable?.dispose()
        mPauseTimeDisposable = null
    }

    fun startBasalRateUpdate(){
        val initialDelaySecs = Calendar.getInstance().let { c ->
            (60 - c.get(Calendar.MINUTE) - 1) * 60 + (60 - c.get(Calendar.SECOND))
        }
        if(mBasalRateDisposable == null) {
            mBasalRateDisposable = Observable.interval(initialDelaySecs.toLong(), 3600L, TimeUnit.SECONDS)
                .observeOn(aapsSchedulers.main)
                .subscribe { updateBasalInfo() }
        }
        updateBasalInfo()
    }

    fun stopBasalRateUpdate(){
        mBasalRateDisposable?.dispose()
        mBasalRateDisposable = null
    }
}