package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.CannulaInsertionAckResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPatchCannulaInsertionConfirmUseCase @Inject constructor(
    private val patchObserver : CarelevoPatchObserver,
    private val patchRepository : CarelevoPatchRepository,
    private val patchInfoRepository : CarelevoPatchInfoRepository
) {

    fun execute() : Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                val pending = patchRepository
                    .requestConfirmCannulaInsertionCheck(true)
                    .blockingGet() as? RequestResult.Pending
                    ?: throw IllegalStateException("request confirm cannula insertion is not pending")

                require(pending.data) {
                    "request confirm cannula insertion result is failed"
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw IllegalStateException("patch info must be not null")
                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                    patchInfo.copy(updatedAt = DateTime.now(), checkNeedle = true)
                )
                if(!updatePatchInfoResult) {
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
        }.subscribeOn(Schedulers.io())
    }
}