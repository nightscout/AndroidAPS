package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetAlarmClearRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetAlarmClearResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultFailed
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmType
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.model.AlarmClearUseCaseRequest
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.LocalDateTime

class AlarmClearRequestUseCase(
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val alarmRepository: CarelevoAlarmInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                val req = request as? AlarmClearUseCaseRequest
                    ?: throw IllegalArgumentException("request is not AlarmClearUseCaseRequest")

                val now = LocalDateTime.now().toString()
                val alarmTypeCmd = when (req.alarmCause.alarmType) {
                    AlarmType.ALERT -> 162
                    AlarmType.NOTICE -> 163
                    else -> throw IllegalArgumentException("alarmType is not supported")
                }

                val clearEventSingle = patchObserver.patchEvent
                    .ofType<SetAlarmClearResultModel>()
                    .firstOrError()
                    .timeout(10, java.util.concurrent.TimeUnit.SECONDS)

                when (val result = patchRepository.requestSetAlarmClear(
                    SetAlarmClearRequest(
                        alarmType = alarmTypeCmd,
                        causeId = req.alarmCause.code ?: 0
                    )
                ).blockingGet()) {
                    is RequestResult.Pending<*> -> Unit
                    is RequestResult.Success<*> -> throw IllegalStateException("request set alarm clear returned Success (expected Pending)")
                    is RequestResult.Failure    -> throw IllegalStateException("request set alarm clear failed: ${result.message}")
                    is RequestResult.Error      -> throw result.e
                }

                val clearResult = clearEventSingle.blockingGet()

                if (clearResult.result == Result.SUCCESS) {
                    alarmRepository.markAcknowledged(
                        alarmId = req.alarmId,
                        acknowledged = true,
                        updatedAt = now
                    ).blockingAwait()

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
