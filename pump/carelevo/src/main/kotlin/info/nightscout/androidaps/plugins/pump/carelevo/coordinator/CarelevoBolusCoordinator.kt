package info.nightscout.androidaps.plugins.pump.carelevo.coordinator

import android.os.SystemClock
import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoCancelExtendBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoCancelImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoFinishImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoStartExtendBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoStartImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.CancelBolusInfusionResponseModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartExtendBolusInfusionRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartImmeBolusInfusionRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartImmeBolusInfusionResponseModel
import info.nightscout.androidaps.plugins.pump.carelevo.event.EventForceStopConnecting
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull
import kotlin.math.ceil
import kotlin.math.min

@Singleton
class CarelevoBolusCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val bolusProgressData: BolusProgressData,
    private val pumpSync: PumpSync,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val carelevoPatch: CarelevoPatch,
    private val startImmeBolusInfusionUseCase: CarelevoStartImmeBolusInfusionUseCase,
    private val finishImmeBolusInfusionUseCase: CarelevoFinishImmeBolusInfusionUseCase,
    private val cancelImmeBolusInfusionUseCase: CarelevoCancelImmeBolusInfusionUseCase,
    private val startExtendBolusInfusionUseCase: CarelevoStartExtendBolusInfusionUseCase,
    private val cancelExtendBolusInfusionUseCase: CarelevoCancelExtendBolusInfusionUseCase
) {

    companion object {
        private const val STOP_BOLUS_TIME_OUT = 15_000L
    }

    private var isImmeBolusStop = false
    private var bolusExpectMs: Long = 0
    private val _lastBolusTime = MutableStateFlow<Long?>(null)
    val lastBolusTime: StateFlow<Long?> = _lastBolusTime

    private val _lastBolusAmount = MutableStateFlow<PumpInsulin?>(null)
    val lastBolusAmount: StateFlow<PumpInsulin?> = _lastBolusAmount

    fun deliverTreatment(
        detailedBolusInfo: DetailedBolusInfo,
        serialNumber: String,
        onLastDataUpdated: () -> Unit,
        pluginDisposable: CompositeDisposable
    ): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "deliverTreatment.start bolusType=${detailedBolusInfo.bolusType}")
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        val result = pumpEnactResultProvider.get()
        if (!carelevoPatch.isBluetoothEnabled()) {
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            return result
        }

        val infusionInfo = carelevoPatch.infusionInfo.value?.getOrNull()
        aapsLogger.warn(
            LTag.PUMPCOMM,
            "deliverTreatment.gate type=${detailedBolusInfo.bolusType}, " +
                "immeInfo=${infusionInfo?.immeBolusInfusionInfo}"
        )
        if (infusionInfo?.immeBolusInfusionInfo != null) {
            aapsLogger.warn(LTag.PUMPCOMM, "deliverTreatment.reject reason=immeBolusInProgress")
            result.success = false
            result.enacted = false
            result.bolusDelivered = 0.0
            result.comment("Another bolus is in progress")
            return result
        }

        isImmeBolusStop = false
        val actionId = (carelevoPatch.patchInfo.value?.getOrNull()?.bolusActionSeq ?: 0) + 1
        val normalizedActionId = if (actionId <= 0) 1 else ((actionId - 1) % 255) + 1

        return try {
            startImmeBolusInfusionUseCase.execute(
                StartImmeBolusInfusionRequestModel(
                    actionSeq = normalizedActionId,
                    volume = detailedBolusInfo.insulin
                )
            )
                .timeout(30, TimeUnit.SECONDS)
                .observeOn(aapsSchedulers.io)
                .subscribeOn(aapsSchedulers.io)
                .doOnSuccess { response -> handleBolusSuccess(response, detailedBolusInfo, result, serialNumber, onLastDataUpdated, pluginDisposable) }
                .doOnError { e -> handleBolusError(e, result) }
                .map { result }
                .blockingGet()
        } catch (e: Throwable) {
            aapsLogger.error(LTag.PUMPCOMM, "deliverTreatment.exception error=$e")
            rxBus.send(EventForceStopConnecting())
            result.success = false
            result.enacted = false
            result.bolusDelivered = 0.0
            result
        }
    }

    fun cancelImmediateBolus(
        serialNumber: String,
        onLastDataUpdated: () -> Unit,
        pluginDisposable: CompositeDisposable
    ) {
        val maxRetry = calculateMaxRetry(totalAllowedMs = bolusExpectMs)
        stopBolusDeliveringInternal(retryCount = 0, maxRetry = maxRetry, serialNumber = serialNumber, onLastDataUpdated = onLastDataUpdated, pluginDisposable = pluginDisposable)
    }

    fun setExtendedBolus(
        insulin: Double,
        durationInMinutes: Int,
        serialNumber: String
    ): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (!carelevoPatch.isBluetoothEnabled()) return result
        if (!carelevoPatch.isCarelevoConnected()) return result

        val response = startExtendBolusInfusionUseCase.execute(
            StartExtendBolusInfusionRequestModel(
                volume = insulin,
                minutes = durationInMinutes
            )
        ).subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .onErrorReturn { e ->
                aapsLogger.error(LTag.PUMPCOMM, "setExtendedBolus.error", e)
                ResponseResult.Error(e)
            }
            .blockingGet()

        return when (response) {
            is ResponseResult.Success -> {
                runBlocking {
                    pumpSync.syncExtendedBolusWithPumpId(
                        timestamp = dateUtil.now(),
                        rate = PumpRate(insulin),
                        duration = T.mins(durationInMinutes.toLong()).msecs(),
                        isEmulatingTB = false,
                        pumpId = dateUtil.now(),
                        pumpType = PumpType.CAREMEDI_CARELEVO,
                        pumpSerial = serialNumber
                    )
                }

                result.success = true
                result.enacted = true
                result
            }

            else -> {
                result.success = false
                result.enacted = false
                result
            }
        }
    }

    fun cancelExtendedBolus(
        serialNumber: String,
        onLastDataUpdated: () -> Unit
    ): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (!carelevoPatch.isBluetoothEnabled()) return result
        if (!carelevoPatch.isCarelevoConnected()) return result

        val response = cancelExtendBolusInfusionUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .onErrorReturn { e ->
                aapsLogger.error(LTag.PUMPCOMM, "cancelExtendedBolus.error", e)
                ResponseResult.Error(e)
            }
            .blockingGet()

        return when (response) {
            is ResponseResult.Success -> {
                aapsLogger.debug(LTag.PUMPCOMM, "cancelExtendedBolus.success")
                onLastDataUpdated()
                runBlocking {
                    pumpSync.syncStopExtendedBolusWithPumpId(
                        timestamp = dateUtil.now(),
                        endPumpId = dateUtil.now(),
                        pumpType = PumpType.CAREMEDI_CARELEVO,
                        pumpSerial = serialNumber
                    )
                }

                result.success = true
                result.enacted = true
                result.isTempCancel = true
                result
            }

            else -> {
                result.success = false
                result.enacted = false
                result
            }
        }
    }

    private fun handleBolusSuccess(
        response: ResponseResult<*>,
        detailedInfo: DetailedBolusInfo,
        result: PumpEnactResult,
        serialNumber: String,
        onLastDataUpdated: () -> Unit,
        pluginDisposable: CompositeDisposable
    ) {
        if (response !is ResponseResult.Success) {
            val message = when (response) {
                is ResponseResult.Failure -> response.message
                is ResponseResult.Error -> response.e.message ?: response.e.toString()
                else -> "Unknown bolus response"
            }
            aapsLogger.error(LTag.PUMPCOMM, "deliverTreatment.nonSuccess response=$response")
            result.success = false
            result.enacted = false
            result.bolusDelivered = 0.0
            result.comment(message)
            return
        }

        val data = response.data as StartImmeBolusInfusionResponseModel

        val now = System.currentTimeMillis()
        onLastDataUpdated()
        _lastBolusTime.value = now
        _lastBolusAmount.value = PumpInsulin(detailedInfo.insulin)

        val stepUnit = 0.05
        val totalInsulin = detailedInfo.insulin
        val totalSteps = ceil(totalInsulin / stepUnit).toInt()

        bolusExpectMs = data.expectSec * 1000L
        val delayMs = bolusExpectMs / totalSteps

        (0..totalSteps).forEach { step ->
            if (!isImmeBolusStop) {
                if (step == totalSteps) {
                    bolusProgressData.updateProgress(
                        100,
                        rh.gs(
                            app.aaps.core.interfaces.R.string.bolus_delivered_successfully,
                            detailedInfo.insulin.toFloat()
                        ),
                        detailedInfo.insulin
                    )
                    runBlocking {
                        pumpSync.syncBolusWithPumpId(
                            detailedInfo.timestamp,
                            PumpInsulin(detailedInfo.insulin),
                            detailedInfo.bolusType,
                            dateUtil.now(),
                            PumpType.CAREMEDI_CARELEVO,
                            serialNumber
                        )
                    }
                    handleFinishImmeBolus(onLastDataUpdated, pluginDisposable)
                } else {
                    SystemClock.sleep(delayMs)
                    val delivering = min(step * stepUnit, detailedInfo.insulin)
                    val percent = if (totalInsulin <= 0.0) 0 else ((delivering / totalInsulin) * 100).toInt()
                    bolusProgressData.updateProgress(
                        percent,
                        rh.gs(app.aaps.core.interfaces.R.string.bolus_delivering, delivering),
                        delivering
                    )
                }
            } else {
                return@forEach
            }
        }

        result.success = true
        result.enacted = true
        result.bolusDelivered = detailedInfo.insulin
    }

    private fun handleBolusError(e: Throwable, result: PumpEnactResult) {
        aapsLogger.error(LTag.PUMPCOMM, "deliverTreatment.error error=$e")
        result.success = false
        result.enacted = false
        result.bolusDelivered = 0.0
        if (e is TimeoutException) {
            result.comment(rh.gs(R.string.alarm_feat_msg_check_patch_connect))
        }
    }

    private fun handleFinishImmeBolus(
        onLastDataUpdated: () -> Unit,
        pluginDisposable: CompositeDisposable
    ) {
        pluginDisposable += finishImmeBolusInfusionUseCase.execute()
            .observeOn(aapsSchedulers.main)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe(
                { response ->
                    when (response) {
                        is ResponseResult.Success -> {
                            onLastDataUpdated()
                            aapsLogger.debug(LTag.PUMPCOMM, "finishImmeBolus.success")
                        }

                        is ResponseResult.Error -> {
                            aapsLogger.error(LTag.PUMPCOMM, "finishImmeBolus.responseError error=${response.e}")
                        }

                        else -> {
                            aapsLogger.error(LTag.PUMPCOMM, "finishImmeBolus.failure")
                        }
                    }
                },
                { e ->
                    aapsLogger.error(LTag.PUMPCOMM, "finishImmeBolus.subscribeError error=$e")
                }
            )
    }

    private fun calculateMaxRetry(
        totalAllowedMs: Long,
        timeoutMs: Long = STOP_BOLUS_TIME_OUT
    ): Int {
        aapsLogger.debug(LTag.PUMPCOMM, "stopBolus.calculateMaxRetry totalAllowedMs=$totalAllowedMs timeoutMs=$timeoutMs")
        if (timeoutMs == 0L) {
            return 3
        }
        return ((totalAllowedMs + timeoutMs - 1) / timeoutMs).toInt() - 1
    }

    private fun stopBolusDeliveringInternal(
        retryCount: Int,
        maxRetry: Int = 3,
        serialNumber: String,
        onLastDataUpdated: () -> Unit,
        pluginDisposable: CompositeDisposable
    ) {
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "stopBolus.start retry=$retryCount maxRetry=$maxRetry"
        )

        pluginDisposable += cancelImmeBolusInfusionUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .timeout(STOP_BOLUS_TIME_OUT, TimeUnit.MILLISECONDS)
            .subscribe(
                { response ->
                    when (response) {
                        is ResponseResult.Success -> {
                            onLastDataUpdated()
                            val cancelResult = response.data as CancelBolusInfusionResponseModel
                            aapsLogger.debug(LTag.PUMPCOMM, "stopBolus.success result=$cancelResult")
                            bolusProgressData.updateProgress(
                                bolusProgressData.state.value?.percent ?: 100,
                                rh.gs(
                                    app.aaps.core.interfaces.R.string.bolus_delivered_successfully,
                                    cancelResult.infusedAmount.toFloat()
                                ),
                                cancelResult.infusedAmount
                            )
                            runBlocking {
                                pumpSync.syncBolusWithPumpId(
                                    dateUtil.now(),
                                    PumpInsulin(cancelResult.infusedAmount),
                                    BS.Type.NORMAL,
                                    dateUtil.now(),
                                    PumpType.CAREMEDI_CARELEVO,
                                    serialNumber
                                )
                            }
                            isImmeBolusStop = true
                        }

                        is ResponseResult.Error -> {
                            aapsLogger.error(LTag.PUMPCOMM, "stopBolus.responseError error=${response.e}")
                        }

                        else -> {
                            aapsLogger.error(LTag.PUMPCOMM, "stopBolus.failure")
                        }
                    }
                },
                { throwable ->
                    if (throwable is TimeoutException) {
                        aapsLogger.error(LTag.PUMPCOMM, "stopBolus.timeout timeoutMs=$STOP_BOLUS_TIME_OUT retry=$retryCount")
                        if (retryCount < maxRetry) {
                            stopBolusDeliveringInternal(
                                retryCount = retryCount + 1,
                                maxRetry = maxRetry,
                                serialNumber = serialNumber,
                                onLastDataUpdated = onLastDataUpdated,
                                pluginDisposable = pluginDisposable
                            )
                        } else {
                            aapsLogger.error(LTag.PUMPCOMM, "stopBolus.timeout.exhausted maxRetry=$maxRetry")
                        }
                    } else {
                        aapsLogger.error(LTag.PUMPCOMM, "stopBolus.error error=$throwable")
                    }
                }
            )
    }
}
