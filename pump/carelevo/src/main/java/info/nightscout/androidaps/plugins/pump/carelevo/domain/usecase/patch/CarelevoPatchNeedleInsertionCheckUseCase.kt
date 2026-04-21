package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.CannulaInsertionAckResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.CannulaInsertionResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.NeedleCheckFailed
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.NeedleCheckSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPatchNeedleInsertionCheckUseCase @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository
) {

    fun execute(): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                val requestResult = patchRepository.requestCannulaInsertionCheck()
                    .blockingGet()

                aapsLogger.debug(LTag.PUMPCOMM, "requestCannulaInsertionCheck result=$requestResult")
                if (requestResult !is RequestResult.Pending) {
                    throw IllegalStateException("request cannula insertion check is not pending")
                }

                val insertionResult = patchObserver.patchEvent
                    .ofType<CannulaInsertionResultModel>()
                    .blockingFirst()

                aapsLogger.debug(LTag.PUMPCOMM, "insertionResult result=${insertionResult.result}")

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw IllegalStateException("patch info must not be null")

                if (insertionResult.result == Result.SUCCESS) {

                    val updated = patchInfoRepository.updatePatchInfo(
                        patchInfo.copy(
                            updatedAt = DateTime.now(),
                            checkNeedle = true
                        )
                    )

                    if (!updated) {
                        throw IllegalStateException("update patch info failed (success case)")
                    }

                    NeedleCheckSuccess

                } else {

                    val nextFailedCount = (patchInfo.needleFailedCount ?: 0) + 1

                    val updated = patchInfoRepository.updatePatchInfo(
                        patchInfo.copy(
                            updatedAt = DateTime.now(),
                            checkNeedle = false,
                            needleFailedCount = nextFailedCount
                        )
                    )

                    if (!updated) {
                        throw IllegalStateException("update patch info failed (failure case)")
                    }

                    NeedleCheckFailed(nextFailedCount)
                }
/*
                if (requestCannulaInsertionResult.result == Result.SUCCESS) {
                    patchRepository.requestConfirmCannulaInsertionCheck(true)
                        .blockingGet()
                        .takeIf { it is RequestResult.Pending }
                        ?: throw IllegalStateException("request confirm cannula insertion is not pending")

                    val requestConfirmCannulaInsertionResult = patchObserver.patchEvent
                        .ofType<CannulaInsertionAckResultModel>()
                        .blockingFirst()

                    if (requestConfirmCannulaInsertionResult.result == Result.SUCCESS) {
                        val patchInfo = patchInfoRepository.getPatchInfoBySync()
                            ?: throw NullPointerException("patch info must be not null")
                        val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                            patchInfo.copy(updatedAt = DateTime.now(), checkNeedle = true)
                        )
                        if (!updatePatchInfoResult) {
                            throw IllegalStateException("update patch info is failed")
                        }

                        NeedleCheckSuccess
                    } else {
                        val patchInfo = patchInfoRepository.getPatchInfoBySync()
                            ?: throw NullPointerException("patch info must be not null")
                        val needleFailedCount = (patchInfo.needleFailedCount ?: 0) + 1
                        val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                            patchInfo.copy(updatedAt = DateTime.now(), checkNeedle = false, needleFailedCount = needleFailedCount)
                        )
                        if (!updatePatchInfoResult) {
                            throw IllegalStateException("update patch info is failed")
                        }
                        NeedleCheckFailed(needleFailedCount)
                    }
                } else {
                    patchRepository.requestConfirmCannulaInsertionCheck(false)
                        .blockingGet()
                        .takeIf { it is RequestResult.Pending }
                        ?: throw IllegalStateException("request confirm cannula insertion is not pending")

                    val requestConfirmCannulaInsertionResult = patchObserver.patchEvent
                        .ofType<CannulaInsertionAckResultModel>()
                        .blockingFirst()

                    if (requestConfirmCannulaInsertionResult.result != Result.SUCCESS) {
                        throw IllegalStateException("request confirm cannula insertion result is failed")
                    }

                    val patchInfo = patchInfoRepository.getPatchInfoBySync()
                        ?: throw NullPointerException("patch info must be not null")
                    val needleFailedCount = (patchInfo.needleFailedCount ?: 0) + 1
                    val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                        patchInfo.copy(updatedAt = DateTime.now(), checkNeedle = false, needleFailedCount = needleFailedCount)
                    )
                    if (!updatePatchInfoResult) {
                        throw IllegalStateException("update patch info is failed")
                    }
                    NeedleCheckFailed(needleFailedCount)
                }*/
            }.fold(
                onSuccess = {
                    ResponseResult.Success(it)
                },
                onFailure = {
                    ResponseResult.Error(it)
                }
            )
        }.observeOn(Schedulers.io())
    }
}
