package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.Result
import app.aaps.pump.carelevo.domain.model.bt.SetThresholdNoticeRequest
import app.aaps.pump.carelevo.domain.model.bt.SetThresholdNoticeResultModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.userSetting.model.CarelevoPatchExpiredThresholdModifyRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CarelevoPatchExpiredThresholdModifyUseCase @Inject constructor(
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single
            .fromCallable {
                runCatching {

                    val req = request as? CarelevoPatchExpiredThresholdModifyRequestModel
                        ?: throw IllegalArgumentException(MSG_REQ_TYPE)
                    val threshold = req.patchExpiredThreshold
                        ?: throw IllegalArgumentException(MSG_MISSING_THRESHOLD)
                    val patchState = req.patchState
                        ?: throw IllegalArgumentException(MSG_MISSING_STATE)

                    when (patchState) {
                        is PatchState.ConnectedBooted -> {
                            val pending = patchRepository
                                .requestSetThresholdNotice(SetThresholdNoticeRequest(threshold, /* 1=expired? */ 1))
                                .blockingGet()

                            if (pending !is RequestResult.Pending) {
                                throw IllegalStateException(MSG_NOT_PENDING)
                            }

                            val result = patchObserver.patchEvent
                                .ofType(SetThresholdNoticeResultModel::class.java)
                                .firstOrError()
                                .timeout(10, TimeUnit.SECONDS)
                                .blockingGet()

                            if (result.result != Result.SUCCESS) {
                                throw IllegalStateException(MSG_PATCH_UPDATE_FAILED + ": ${result.result}")
                            }
                        }

                        is PatchState.NotConnectedNotBooting -> {

                        }

                        else -> {

                        }
                    }

                    ResultSuccess
                }.fold(
                    onSuccess = { ResponseResult.Success(it as CarelevoUseCaseResponse) },
                    onFailure = { ResponseResult.Error(it) }
                )
            }.subscribeOn(Schedulers.io())
    }

    private companion object {

        private const val MSG_REQ_TYPE = "Invalid request type: expected CarelevoUserSettingInfoRequestModel."
        private const val MSG_MISSING_THRESHOLD = "Missing lowInsulinNoticeAmount (threshold)."
        private const val MSG_MISSING_STATE = "Missing patchState."
        private const val MSG_NOT_PENDING = "Patch request did not return Pending state."
        private const val MSG_PATCH_UPDATE_FAILED = "Patch update (threshold notice) returned non-success result."
    }
}
