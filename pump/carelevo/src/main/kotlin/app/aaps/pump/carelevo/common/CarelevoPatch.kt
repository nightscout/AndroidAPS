package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.ble.CarelevoBleSource
import app.aaps.pump.carelevo.ble.core.CarelevoBleController
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.CommandResult
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.isAvailable
import app.aaps.pump.carelevo.ble.data.isPeripheralConnected
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.bt.AlertReportResultModel
import app.aaps.pump.carelevo.domain.model.bt.BasalInfusionResumeResultModel
import app.aaps.pump.carelevo.domain.model.bt.FinishPulseReportResultModel
import app.aaps.pump.carelevo.domain.model.bt.InfusionInfoReportResultModel
import app.aaps.pump.carelevo.domain.model.bt.InfusionModeResult.Companion.commandToCode
import app.aaps.pump.carelevo.domain.model.bt.NoticeReportResultModel
import app.aaps.pump.carelevo.domain.model.bt.PatchResultModel
import app.aaps.pump.carelevo.domain.model.bt.PumpStateResult.Companion.commandToCode
import app.aaps.pump.carelevo.domain.model.bt.RetrieveOperationInfoResultModel
import app.aaps.pump.carelevo.domain.model.bt.WarningReportResultModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoInfusionInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchRptInfusionInfoProcessUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoRequestPatchInfusionInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoDefaultRequestModel
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoRequestModel
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoCreateUserSettingInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUserSettingInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import app.aaps.pump.carelevo.event.EventForceStopConnecting
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import kotlin.math.min

class CarelevoPatch @Inject constructor(
    private val bleController: CarelevoBleController,
    private val patchObserver: CarelevoPatchObserver,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val sp: SP,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    private val infusionInfoMonitorUseCase: CarelevoInfusionInfoMonitorUseCase,
    private val patchInfoMonitorUseCase: CarelevoPatchInfoMonitorUseCase,
    private val userSettingInfoMonitorUseCase: CarelevoUserSettingInfoMonitorUseCase,
    private val patchRptInfusionInfoProcessUseCase: CarelevoPatchRptInfusionInfoProcessUseCase,
    private val updateMaxBolusDoseUseCase: CarelevoUpdateMaxBolusDoseUseCase,
    private val updateLowInsulinNoticeAmountUseCase: CarelevoUpdateLowInsulinNoticeAmountUseCase,
    private val createUserSettingInfoUseCase: CarelevoCreateUserSettingInfoUseCase,
    private val carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase,
    private val requestPatchInfusionInfoUseCase: CarelevoRequestPatchInfusionInfoUseCase
) {

    private val bleDisposable = CompositeDisposable()
    private var connectingDisposable: Disposable? = null

    private val infoDisposable = CompositeDisposable()

    private var _isWorking = false
    val isWorking get() = _isWorking

    private val _btState: BehaviorSubject<Optional<BleState>> = BehaviorSubject.create()
    val btState get() = _btState

    private val _patchState: BehaviorSubject<Optional<PatchState>> = BehaviorSubject.create()
    val patchState get() = _patchState

    private val _isConnected: BehaviorSubject<Boolean> = BehaviorSubject.createDefault(false)
    val isConnected get() = _isConnected

    private val _connectedAddress: BehaviorSubject<Optional<String>> = BehaviorSubject.create()
    val connectedAddress get() = _connectedAddress

    private val _patchInfo: BehaviorSubject<Optional<CarelevoPatchInfoDomainModel>> = BehaviorSubject.create()
    val patchInfo get() = _patchInfo

    private val _infusionInfo: BehaviorSubject<Optional<CarelevoInfusionInfoDomainModel>> = BehaviorSubject.create()
    val infusionInfo get() = _infusionInfo

    private val _userSettingInfo: BehaviorSubject<Optional<CarelevoUserSettingInfoDomainModel>> = BehaviorSubject.create()
    val userSettingInfo get() = _userSettingInfo

    private val _profile: BehaviorSubject<Optional<Profile>> = BehaviorSubject.create()
    val profile get() = _profile

    private var lastBtState: BleState? = null

    fun initPatch() {
        if (!isWorking) {
            observePatchInfo()
            observeBleState()
            observeChangeState()
            observePatch()
            observeInfusionInfo()
            observeUserSettingInfo()
            observeSyncPatch()
            _isWorking = true
        }
    }

    fun initPatchAndAwait(): Completable =
        Completable.defer {
            initPatch()
            patchState
                .filter { state ->
                    state == PatchState.NotConnectedNotBooting || state == PatchState.ConnectedBooted
                }
                .firstOrError()
                .ignoreElement()
        }

    /** One-shot wrapper that shares the same init progress across duplicate calls. */
    @Volatile private var inFlightInit: Completable? = null
    fun initPatchOnce(): Completable = synchronized(this) {
        inFlightInit?.let { return it }
        val c = initPatchAndAwait()
            .timeout(20, TimeUnit.SECONDS)
            .cache()
            .doFinally { synchronized(this) { inFlightInit = null } }
        inFlightInit = c
        c
    }

    fun isCarelevoConnected(): Boolean {
        val address = connectedAddress.value?.getOrNull()
        val isConnected = isConnected.value ?: false
        val validAddress = patchInfo.value?.getOrNull()?.address

        return address != null && validAddress != null && isConnected && address.equals(validAddress, ignoreCase = true)
    }

    fun isBleConnectedNow(address: String): Boolean {
        return bleController.isConnectedNow(address)
    }

    fun getPatchInfoAddress(): String? {
        return patchInfo.value?.getOrNull()?.address
    }

    fun resolvePatchState(): PatchState {
        val isPatchValid = patchInfo.value?.getOrNull()?.let { true } ?: false
        val isPeripheralConnected = btState.value?.getOrNull()?.isPeripheralConnected() ?: false

        return when {
            isPeripheralConnected && isPatchValid  -> PatchState.ConnectedBooted
            isPeripheralConnected && !isPatchValid -> PatchState.NotConnectedNotBooting
            !isPeripheralConnected && isPatchValid -> PatchState.NotConnectedBooted
            else                                   -> PatchState.NotConnectedNotBooting
        }
    }

    private fun observeChangeState() {
        aapsLogger.debug(LTag.PUMPCOMM, "observeChangeState called")
        bleDisposable += rxBus.toObservable(EventForceStopConnecting::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe {
                aapsLogger.warn(LTag.PUMPCOMM, "Force stop connectingDisposable")
                connectingDisposable?.dispose()
                connectingDisposable = null
            }

        bleDisposable += BehaviorSubject.combineLatest(
            btState,
            patchInfo
        ) { btState, _ ->
            val btAvailable = btState.getOrNull()?.isAvailable()
            val btPeripheralConnected = btState.getOrNull()?.isPeripheralConnected()

            aapsLogger.debug(LTag.PUMPCOMM, "btAvailable : $btAvailable")
            aapsLogger.debug(LTag.PUMPCOMM, "btPeripheralConnected : $btPeripheralConnected")

            var result = resolvePatchState()
            if (result == PatchState.ConnectedBooted) {
                if (btAvailable == false) {
                    result = PatchState.NotConnectedBooted
                }
            }

            aapsLogger.debug(LTag.PUMPCOMM, "result : $result")

            _isConnected.onNext(btPeripheralConnected ?: false)
            _connectedAddress.onNext(Optional.ofNullable(bleController.getConnectedAddress()))
            _patchState.onNext(Optional.ofNullable(result))

            when (result) {
                is PatchState.NotConnectedNotBooting -> {
                    aapsLogger.debug(LTag.PUMPCOMM, "patch state is no connection")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                    rxBus.send(EventRefreshOverview("Carelevo connection state", true))
                    rxBus.send(EventCustomActionsChanged())
                    stopObservingConnection()
                }

                is PatchState.ConnectedBooted        -> {
                    aapsLogger.debug(LTag.PUMPCOMM, "patch state is ConnectedBooted")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                    rxBus.send(EventRefreshOverview("Carelevo connection state", true))
                    rxBus.send(EventCustomActionsChanged())
                    stopObservingConnection()
                }

                is PatchState.NotConnectedBooted     -> {
                    aapsLogger.debug(LTag.PUMPCOMM, "patch state is NotConnectedBooted")

                    /*connectingDisposable?.dispose()
                    connectingDisposable = Observable.interval(0, 1, TimeUnit.SECONDS)
                        .observeOn(aapsSchedulers.main)
                        .takeUntil {
                            btState.getOrNull()?.isPeripheralConnected() == true || it > 60
                        }
                        .subscribe { n ->
                            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, n.toInt()))
                        }*/
                }

                else                                 -> {
                    aapsLogger.debug(LTag.PUMPCOMM, "patch state is disconnected")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                }
            }

            result
        }
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnComplete {
                aapsLogger.debug(LTag.PUMPCOMM, "doOnComplete called")
            }
            .doOnError {
                it.printStackTrace()
                aapsLogger.debug(LTag.PUMPCOMM, "doOnError called : $it")
            }
            .subscribe {
                aapsLogger.debug(LTag.PUMPCOMM, "result : $it")
            }
    }

    private fun stopObservingConnection() {
        if (connectingDisposable != null) {
            connectingDisposable!!.dispose()
            connectingDisposable = null
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return btState.value?.getOrNull()?.let {
            it.isEnabled == DeviceModuleState.DEVICE_STATE_ON
        } ?: false
    }

    //===================================================================================================
    fun setProfile(profile: Profile?) {
        _profile.onNext(Optional.ofNullable(profile))
    }

    fun checkIsSameProfile(newProfile: Profile?): Boolean {
        val setProfile = profile.value?.getOrNull() ?: return false
        val a = newProfile ?: return false
        val aVals = a.getBasalValues()
        val bVals = setProfile.getBasalValues()

        if (aVals.size != bVals.size) return false

        for (i in aVals.indices) {
            if (TimeUnit.SECONDS.toMinutes(aVals[i].timeAsSeconds.toLong()) !=
                TimeUnit.SECONDS.toMinutes(bVals[i].timeAsSeconds.toLong())
            ) return false

            if (!nearlyEqual(aVals[i].value.toFloat(), bVals[i].value.toFloat())) return false
        }
        return true
    }

    private fun nearlyEqual(a: Float, b: Float, epsilon: Float = 1e-3f): Boolean {
        val absA = abs(a)
        val absB = abs(b)
        val diff = abs(a - b)
        return if (a == b) {
            true
        } else if (a == 0f || b == 0f || absA + absB < java.lang.Float.MIN_NORMAL) {
            diff < epsilon * java.lang.Float.MIN_NORMAL
        } else {
            diff / min(absA + absB, Float.MAX_VALUE) < epsilon
        }
    }

    private fun observeBleState() {
        bleDisposable += CarelevoBleSource.bluetoothState
            .observeOn(aapsSchedulers.io)
            .distinctUntilChanged()
            .subscribe { state ->
                aapsLogger.debug(LTag.PUMPCOMM, "state : $state")
                if (state.isEnabled == DeviceModuleState.DEVICE_STATE_OFF) {
                    if (lastBtState != null && lastBtState?.isEnabled != DeviceModuleState.DEVICE_STATE_OFF) {
                        bleController.checkGatt()
                        bleController.clearOnlyGatt()
                        handleAlarm("alert", value = null, cause = AlarmCause.ALARM_ALERT_BLUETOOTH_OFF)
                    }
                }

                lastBtState = state
                _btState.onNext(Optional.ofNullable(state))
            }
    }

    fun releasePatch() {
        flushPatchInformation()
    }

    fun flushPatchInformation() {
        //unBondDevice()
        bleController.clearGatt()
        bleController.unRegisterPeripheralInfo()
        //_patchInfo.onNext(Optional.ofNullable(null))
        //_infusionInfo.onNext(Optional.ofNullable(null))
    }

    private fun observePatch() {
        bleDisposable += patchObserver.patchResponseEvent
            .observeOn(aapsSchedulers.io)
            .subscribe {
                proceedPatchEvent(it)
            }
    }

    private fun proceedPatchEvent(model: PatchResultModel) {
        when (model) {
            is BasalInfusionResumeResultModel   -> {}

            is FinishPulseReportResultModel     -> {}

            is WarningReportResultModel         -> handleAlarm("warning", model.value, model.cause)
            is AlertReportResultModel           -> handleAlarm("alert", model.value, model.cause)
            is NoticeReportResultModel          -> handleAlarm("notice", model.value, model.cause)
            is RetrieveOperationInfoResultModel -> updateRemainAndRefreshInfusion(model)
            is InfusionInfoReportResultModel    -> updateInfusionInfo(model)
        }
    }

    fun unBondDevice(): Single<Boolean> {
        return Single
            .fromCallable {
                when (bleController.unBondDevice()) {
                    is CommandResult.Success -> true
                    else                     -> false
                }
            }
            .subscribeOn(Schedulers.io())
    }

    private fun updateRemainAndRefreshInfusion(model: RetrieveOperationInfoResultModel) {
        val requestModel = CarelevoPatchRptInfusionInfoDefaultRequestModel(remains = model.remains)

        bleDisposable += patchRptInfusionInfoProcessUseCase.execute(requestModel)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.main)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response success")
                        refreshPatchInfusionInfo()
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response failed")
                    }
                }
            }
    }

    private fun updateInfusionInfo(model: InfusionInfoReportResultModel) {
        val requestModel = CarelevoPatchRptInfusionInfoRequestModel(
            runningMinute = model.runningMinutes,
            remains = model.remains,
            infusedTotalBasalAmount = model.infusedTotalBasalAmount,
            infusedTotalBolusAmount = model.infusedTotalBolusAmount,
            pumpState = model.pumpState.commandToCode(),
            mode = model.mode.commandToCode(),
            currentInfusedProgramVolume = model.currentInfusedProgramVolume,
            realInfusedTime = model.realInfusedTime
        )

        bleDisposable += patchRptInfusionInfoProcessUseCase.execute(requestModel)
            .timeout(3, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe()
    }

    private fun refreshPatchInfusionInfo() {
        if (!isBluetoothEnabled()) {
            return
        }
        if (!isCarelevoConnected()) {
            return
        }

        infoDisposable += requestPatchInfusionInfoUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .timeout(3, TimeUnit.SECONDS)
            .subscribe()
    }

    private fun handleAlarm(modelType: String, value: Int?, cause: AlarmCause) {
        aapsLogger.debug(LTag.PUMPCOMM, "$modelType report : $value, $cause")
        val info = CarelevoAlarmInfo(
            alarmId = System.currentTimeMillis().toString(),
            alarmType = cause.alarmType,
            cause = cause,
            value = value,
            createdAt = LocalDateTime.now().toString(),
            updatedAt = LocalDateTime.now().toString(),
            isAcknowledged = false
        )
        bleDisposable += carelevoAlarmInfoUseCase.upsertAlarm(info)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { aapsLogger.debug(LTag.PUMPCOMM, "handleAlarm upsert complete") },
                { e -> aapsLogger.error(LTag.PUMPCOMM, "handleAlarm upsert error", e) }
            )
    }

    private fun observeInfusionInfo() {
        infoDisposable += infusionInfoMonitorUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoInfusionInfoDomainModel?
                        aapsLogger.debug(LTag.PUMPCOMM, "response success result ==> $result")
                        _infusionInfo.onNext(Optional.ofNullable(result))
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response failed")
                    }
                }
            }
    }

    private fun observePatchInfo() {
        infoDisposable += patchInfoMonitorUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoPatchInfoDomainModel?
                        aapsLogger.debug(LTag.PUMPCOMM, "response success result ==> ${result?.needleFailedCount}")
                        _patchInfo.onNext(Optional.ofNullable(result))
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response failed")
                    }
                }
            }
    }

    private fun observeUserSettingInfo() {
        infoDisposable += userSettingInfoMonitorUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoUserSettingInfoDomainModel?
                        aapsLogger.debug(LTag.PUMPCOMM, "response success result ==> $result")
                        _userSettingInfo.onNext(Optional.ofNullable(result))
                        if (result == null) {
                            createUserSettingInfo()
                        }
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response failed")
                    }
                }
            }
    }

    private fun observeSyncPatch() {
        infoDisposable += Observable.combineLatest(
            patchState,
            infusionInfo,
            userSettingInfo
        ) { state, infusionInfo, userSettingInfo ->
            if (state.getOrNull() is PatchState.ConnectedBooted && (infusionInfo.getOrNull()?.extendBolusInfusionInfo == null || infusionInfo.getOrNull()?.immeBolusInfusionInfo == null) && (userSettingInfo?.getOrNull()?.needMaxBolusDoseSyncPatch == true)) {
                updateMaxBolusDose(userSettingInfo.getOrNull()?.maxBolusDose ?: 0.0)
            }

            if (state.getOrNull() is PatchState.ConnectedBooted && (userSettingInfo?.getOrNull()?.needLowInsulinNoticeAmountSyncPatch == true)) {
                updateLowInsulinNoticeAmount(userSettingInfo.getOrNull()?.lowInsulinNoticeAmount ?: 0)
            }
        }.observeOn(aapsSchedulers.main)
            .subscribeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.PUMPCOMM, "response success")
            }
    }

    private fun updateMaxBolusDose(maxBolusDose: Double) {
        val requestModel = CarelevoUserSettingInfoRequestModel(
            patchState = patchState.value?.getOrNull(),
            maxBolusDose = maxBolusDose
        )

        infoDisposable += updateMaxBolusDoseUseCase.execute(requestModel)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response success")
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response failed")
                    }
                }
            }
    }

    private fun updateLowInsulinNoticeAmount(lowInsulinNoticeAmount: Int) {
        val requestModel = CarelevoUserSettingInfoRequestModel(
            patchState = patchState.value?.getOrNull(),
            lowInsulinNoticeAmount = lowInsulinNoticeAmount
        )
        infoDisposable += updateLowInsulinNoticeAmountUseCase.execute(requestModel)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response success")
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response failed")
                    }
                }
            }
    }

    private fun createUserSettingInfo() {
        val maxBolusDose = preferences.get(DoubleKey.SafetyMaxBolus)
        val lowInsulinNoticeAmount = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key, 30)

        val requestModel = CarelevoUserSettingInfoRequestModel(
            lowInsulinNoticeAmount = lowInsulinNoticeAmount,
            maxBasalSpeed = 15.0,
            maxBolusDose = maxBolusDose
        )
        infoDisposable += createUserSettingInfoUseCase.execute(requestModel)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe()
    }

}
