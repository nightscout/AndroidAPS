package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.DiscardPatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultFailed
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.model.AlarmClearUseCaseRequest
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import java.time.LocalDateTime

class AlarmClearPatchDiscardUseCase(
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val alarmRepository: CarelevoAlarmInfoRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                val req = request as? AlarmClearUseCaseRequest
                    ?: throw IllegalArgumentException("request is not AlarmClearUseCaseRequest")

                val clearEventSingle = patchObserver.patchEvent
                    .ofType<DiscardPatchResultModel>()
                    .firstOrError()
                    .timeout(10, java.util.concurrent.TimeUnit.SECONDS)

                when (val result = patchRepository.requestDiscardPatch().blockingGet()) {
                    is RequestResult.Pending<*> -> Unit
                    is RequestResult.Success<*> -> throw IllegalStateException("request set alarm clear returned Success (expected Pending)")
                    is RequestResult.Failure -> throw IllegalStateException("request set alarm clear failed: ${result.message}")
                    is RequestResult.Error -> throw result.e
                }

                val clearResult = clearEventSingle.blockingGet()

                if (clearResult.result == Result.SUCCESS) {
                    alarmRepository.markAcknowledged(
                        alarmId = req.alarmId,
                        acknowledged = true,
                        updatedAt = LocalDateTime.now().toString()
                    ).blockingAwait()

                    val userSettingInfo = userSettingInfoRepository.getUserSettingInfoBySync()
                        ?: throw NullPointerException("user setting info must be not null")

                    val updateUserSettingInfoResult = userSettingInfoRepository.updateUserSettingInfo(
                        userSettingInfo.copy(updatedAt = DateTime.now(), needMaxBolusDoseSyncPatch = false, needMaxBasalSpeedSyncPatch = false, needLowInsulinNoticeAmountSyncPatch = false)
                    )
                    if (!updateUserSettingInfoResult) {
                        throw IllegalStateException("update user setting info is failed")
                    }

                    val deleteInfusionInfoResult = infusionInfoRepository.deleteInfusionInfo()
                    if (!deleteInfusionInfoResult) {
                        throw IllegalStateException("delete infusion info is failed")
                    }

                    val deletePatchInfoResult = patchInfoRepository.deletePatchInfo()
                    if (!deletePatchInfoResult) {
                        throw IllegalStateException("delete patch info is failed")
                    }

                    ResultSuccess
                } else {
                    ResultFailed
                }
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
