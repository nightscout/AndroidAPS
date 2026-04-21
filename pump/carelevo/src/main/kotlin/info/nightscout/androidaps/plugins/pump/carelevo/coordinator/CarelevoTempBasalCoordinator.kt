package info.nightscout.androidaps.plugins.pump.carelevo.coordinator

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.model.StartTempBasalInfusionRequestModel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class CarelevoTempBasalCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val carelevoPatch: CarelevoPatch,
    private val startTempBasalInfusionUseCase: CarelevoStartTempBasalInfusionUseCase,
    private val cancelTempBasalInfusionUseCase: CarelevoCancelTempBasalInfusionUseCase
) {

    fun setTempBasalAbsolute(
        absoluteRate: Double,
        durationInMinutes: Int,
        tbrType: PumpSync.TemporaryBasalType,
        serialNumber: String,
        onLastDataUpdated: () -> Unit
    ): PumpEnactResult {
        aapsLogger.info(
            LTag.PUMPCOMM,
            "setTempBasalAbsolute.start absoluteRate=${absoluteRate.toFloat()} durationInMinutes=${durationInMinutes.toLong()}"
        )
        val result = pumpEnactResultProvider.get()
        if (!carelevoPatch.isBluetoothEnabled()) {
            aapsLogger.info(LTag.PUMPCOMM, "setTempBasalAbsolute.skip reason=bluetoothDisabled")
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            aapsLogger.info(LTag.PUMPCOMM, "setTempBasalAbsolute.skip reason=notConnected")
            return result
        }

        val response = startTempBasalInfusionUseCase.execute(
            StartTempBasalInfusionRequestModel(
                isUnit = true,
                speed = absoluteRate,
                minutes = durationInMinutes
            )
        )
            .subscribeOn(aapsSchedulers.io)
            .timeout(10, TimeUnit.SECONDS)
            .onErrorReturn { throwable ->
                aapsLogger.error(LTag.PUMPCOMM, "setTempBasalAbsolute.error", throwable)
                ResponseResult.Error(throwable)
            }
            .blockingGet()

        return when (response) {
            is ResponseResult.Success -> {
                aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalAbsolute.success")
                onLastDataUpdated()
                runBlocking {
                    pumpSync.syncTemporaryBasalWithPumpId(
                        timestamp = dateUtil.now(),
                        rate = PumpRate(absoluteRate),
                        duration = T.mins(durationInMinutes.toLong()).msecs(),
                        isAbsolute = true,
                        type = tbrType,
                        pumpId = dateUtil.now(),
                        pumpType = PumpType.CAREMEDI_CARELEVO,
                        pumpSerial = serialNumber
                    )
                }

                result.success(true).enacted(true)
                    .duration(durationInMinutes)
                    .absolute(absoluteRate)
                    .isPercent(false)
                    .isTempCancel(false)
            }

            else -> {
                aapsLogger.error(LTag.PUMPCOMM, "setTempBasalAbsolute.failure response=$response")
                result.success(false).enacted(false).comment("Internal error")
            }
        }
    }

    fun setTempBasalPercent(
        percent: Int,
        durationInMinutes: Int,
        tbrType: PumpSync.TemporaryBasalType,
        serialNumber: String,
        onLastDataUpdated: () -> Unit
    ): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalPercent.start percent=$percent durationInMinutes=$durationInMinutes")
        if (!carelevoPatch.isBluetoothEnabled()) {
            aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalPercent.skip reason=bluetoothDisabled")
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalPercent.skip reason=notConnected")
            return result
        }

        return startTempBasalInfusionUseCase.execute(
            StartTempBasalInfusionRequestModel(
                isUnit = false,
                percent = percent,
                minutes = durationInMinutes
            )
        )
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .doOnSuccess { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalPercent.success")
                        onLastDataUpdated()
                        runBlocking {
                            pumpSync.syncTemporaryBasalWithPumpId(
                                timestamp = dateUtil.now(),
                                rate = PumpRate(percent.toDouble()),
                                duration = T.mins(durationInMinutes.toLong()).msecs(),
                                isAbsolute = false,
                                type = tbrType,
                                pumpId = dateUtil.now(),
                                pumpType = PumpType.CAREMEDI_CARELEVO,
                                pumpSerial = serialNumber
                            )
                        }

                        result.success = true
                        result.enacted = true
                        result.duration = durationInMinutes
                        result.percent = percent
                        result.isPercent = true
                        result.isTempCancel = false
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.error(LTag.PUMPCOMM, "setTempBasalPercent.responseError error=${response.e}")
                    }

                    else -> {
                        aapsLogger.error(LTag.PUMPCOMM, "setTempBasalPercent.failure")
                    }
                }
            }.doOnError {
                aapsLogger.error(LTag.PUMPCOMM, "setTempBasalPercent.error", it)
                result.success = false
                result.enacted = false
            }.map {
                result
            }.blockingGet()
    }

    fun cancelTempBasal(
        serialNumber: String,
        onLastDataUpdated: () -> Unit
    ): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal.start")
        if (!carelevoPatch.isBluetoothEnabled()) {
            aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal.skip reason=bluetoothDisabled")
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal.skip reason=notConnected")
            return result
        }

        return cancelTempBasalInfusionUseCase.execute()
            .delaySubscription(2000L, TimeUnit.MILLISECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .timeout(15000L, TimeUnit.MILLISECONDS)
            .map { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal.success")
                        onLastDataUpdated()
                        runBlocking {
                            pumpSync.syncStopTemporaryBasalWithPumpId(
                                timestamp = dateUtil.now(),
                                endPumpId = dateUtil.now(),
                                pumpType = PumpType.CAREMEDI_CARELEVO,
                                pumpSerial = serialNumber
                            )
                        }

                        result.success = true
                        result.enacted = true
                        result.isTempCancel = true
                    }

                    else -> {
                        aapsLogger.error(LTag.PUMPCOMM, "cancelTempBasal.failure response=$response")
                        result.success = false
                        result.enacted = false
                    }
                }
                result
            }
            .onErrorReturn { e ->
                aapsLogger.error(LTag.PUMPCOMM, "cancelTempBasal.error error=$e")
                result.success = false
                result.enacted = false
                result
            }
            .blockingGet()
    }

}
