package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.AlertAlarmSetResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.AppAuthAckResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PatchInformationInquiryDetailModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PatchInformationInquiryModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.RetrieveAddressRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.RetrieveAddressResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetAlertAlarmModeRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetTimeRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.ThresholdSetRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.ThresholdSetResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.ext.checkSumV2
import info.nightscout.androidaps.plugins.pump.carelevo.ext.convertHexToByteArray
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CarelevoConnectNewPatchUseCase @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
) {
    companion object {
        private const val PATCH_INFO_ROUND_RETRY_COUNT = 2
        private const val PATCH_EVENT_TIMEOUT_SEC = 10L
        private val BOOT_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMddHHmm")
    }

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is CarelevoConnectNewPatchRequestModel) {
                    throw IllegalArgumentException("request is not CarelevoConnectNewPatchRequestModel")
                }

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] execute called")

                val randomKey = generateRandomKey(0..255)

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 1. Generated random key: $randomKey")

                patchRepository.requestRetrieveMacAddress(RetrieveAddressRequest(randomKey.toByte()))
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("")

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 2. Requesting MAC address")

                val addressInfoResult = patchObserver.patchEvent
                    .ofType<RetrieveAddressResultModel>()
                    .blockingFirst()

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 3. Received MAC address response: $addressInfoResult")

                if (addressInfoResult.address.isEmpty()) {
                    throw NullPointerException("mac address must be not empty")
                }

                val address = buildString {
                    for (i in 2 until 24 step 4) {
                        append(addressInfoResult.address.subSequence(i, i + 2))
                        if (i < 20) {
                            append(":")
                        }
                    }
                }

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 4. Built MAC address: $address")

                val checkSum = (addressInfoResult.address + addressInfoResult.checkSum).convertHexToByteArray()

                val checkSumData = checkSum.checkSumV2(randomKey)

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 5. Generated checksum payload: $checkSumData")

                patchRepository.requestAppAuth(checkSumData)
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("")

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 6. Requesting checksum verification")

                val appAuthResult = patchObserver.patchEvent
                    .ofType<AppAuthAckResultModel>()
                    .blockingFirst()

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 7. Received checksum verification response: $appAuthResult")

                if (appAuthResult.result != Result.SUCCESS) {
                    throw IllegalStateException("")
                }

                var patchInfoResult: PatchInformationInquiryModel? = null
                var inquiryDetailModel: PatchInformationInquiryDetailModel? = null
                var lastPatchInfoError: Throwable? = null

                for (round in 1..PATCH_INFO_ROUND_RETRY_COUNT) {
                    try {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 8. Sending SET TIME round=$round/$PATCH_INFO_ROUND_RETRY_COUNT")
                        val (info, detail) = requestPatchInfoRound(request, round)
                        aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 9. Received patch base info: $info")
                        aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 10. Received patch detail info: $detail")

                        val serial = info.serialNum.trim()
                        if (info.result == Result.SUCCESS && serial.isNotEmpty() && detail.result == Result.SUCCESS) {
                            patchInfoResult = info
                            inquiryDetailModel = detail
                            break
                        }

                        aapsLogger.warn(
                            LTag.PUMP,
                            "[CarelevoRxConnectNewPatchUseCase] invalid patch info round=$round/$PATCH_INFO_ROUND_RETRY_COUNT result=${info.result} serial=$serial detailResult=${detail.result}"
                        )
                    } catch (e: Throwable) {
                        lastPatchInfoError = e
                        aapsLogger.error(
                            LTag.PUMP,
                            "[CarelevoRxConnectNewPatchUseCase] requestPatchInfoRound failed round=$round/$PATCH_INFO_ROUND_RETRY_COUNT",
                            e
                        )
                    }
                }

                val finalPatchInfo = patchInfoResult ?: throw IllegalStateException("patch info invalid after retry", lastPatchInfoError)
                val finalPatchDetail = inquiryDetailModel ?: throw IllegalStateException("patch detail missing after retry", lastPatchInfoError)
                val serial = finalPatchInfo.serialNum

                if (finalPatchDetail.result != Result.SUCCESS) {
                    throw IllegalStateException("")
                }

                patchRepository.requestSetAlertAlarmMode(SetAlertAlarmModeRequest(0))
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request set alarm mode is not pending")

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 11. Sending alarm mode request")

                val setAlarmModeResultModel = patchObserver.patchEvent
                    .ofType<AlertAlarmSetResultModel>()
                    .blockingFirst()

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 12. Received alarm mode response: $setAlarmModeResultModel")

                if (setAlarmModeResultModel.result != Result.SUCCESS) {
                    throw IllegalStateException("")
                }

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] : ${request.remains}, ${request.expiry}, ${request.maxBasalSpeed}, ${request.maxVolume}")
                patchRepository.requestSetThreshold(ThresholdSetRequest(request.remains, request.expiry, request.maxBasalSpeed, request.maxVolume, request.isBuzzOn))
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending } ?: throw IllegalStateException("request set time is not pending")




                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 13. Sending threshold settings request")

                val setThresholdResult = patchObserver.patchEvent
                    .ofType<ThresholdSetResultModel>()
                    .blockingFirst()

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 14. Received threshold settings response: $setThresholdResult")

                if (setThresholdResult.result != Result.SUCCESS) {
                    throw IllegalStateException("")
                }

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                    CarelevoPatchInfoDomainModel(
                        address = address,
                        manufactureNumber = serial,
                        firmwareVersion = finalPatchDetail.firmwareVer,
                        bootDateTime = finalPatchDetail.bootDateTime,
                        bootDateTimeUtcMillis = parseBootDateTimeUtcMillis(finalPatchDetail.bootDateTime),
                        modelName = finalPatchDetail.modelName,
                        insulinAmount = request.volume,
                        insulinRemain = request.volume.toDouble(),
                        thresholdInsulinRemain = request.remains,
                        thresholdExpiry = request.expiry,
                        thresholdMaxBasalSpeed = request.maxBasalSpeed,
                        thresholdMaxBolusDose = request.maxVolume
                    )
                )

                aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] 15. Saved patch info: $updatePatchInfoResult")

                if (!updatePatchInfoResult) {
                    throw IllegalStateException("update patch info is failed")
                }
                ResultSuccess
            }.fold(
                onSuccess = {
                    ResponseResult.Success(it as CarelevoUseCaseResponse)
                },
                onFailure = {
                    ResponseResult.Error(it)
                }
            )
        }.observeOn(Schedulers.io())
    }

    private fun requestPatchInfoRound(
        request: CarelevoConnectNewPatchRequestModel,
        round: Int
    ): Pair<PatchInformationInquiryModel, PatchInformationInquiryDetailModel> {
        aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] waiting 9. Patch base info round=$round timeout=${PATCH_EVENT_TIMEOUT_SEC}s")
        val patchInfoFuture = patchObserver.patchEvent
            .ofType<PatchInformationInquiryModel>()
            .timeout(PATCH_EVENT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .firstOrError()
            .toFuture()

        aapsLogger.debug(LTag.PUMP, "[CarelevoRxConnectNewPatchUseCase] waiting 10. Patch detail info round=$round timeout=${PATCH_EVENT_TIMEOUT_SEC}s")
        val patchDetailFuture = patchObserver.patchEvent
            .ofType<PatchInformationInquiryDetailModel>()
            .timeout(PATCH_EVENT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .firstOrError()
            .toFuture()

        patchRepository.requestSetTime(SetTimeRequest("", request.volume, 0, 0))
            .blockingGet()
            .takeIf { it is RequestResult.Pending }
            ?: throw IllegalStateException("request set time is not pending")

        val patchInfo = patchInfoFuture.get()
        val patchDetail = patchDetailFuture.get()

        return patchInfo to patchDetail
    }

    private fun generateRandomKey(range: ClosedRange<Int>): Int {
        return range.run {
            (Math.random() * (endInclusive - start + 1) + start).toInt()
        }
    }

    private fun parseBootDateTimeUtcMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }

        return runCatching {
            LocalDateTime.parse(raw, BOOT_DATE_TIME_FORMATTER)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
