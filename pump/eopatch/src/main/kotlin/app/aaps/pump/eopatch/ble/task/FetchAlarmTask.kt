package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.core.api.GetErrorCodes
import app.aaps.pump.eopatch.core.response.AeCodeResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class FetchAlarmTask @Inject constructor(
    private val alarmRegistry: IAlarmRegistry
) : TaskBase(TaskFunc.FETCH_ALARM) {

    private val ALARM_ALERT_ERROR_CODE_GET: GetErrorCodes = GetErrorCodes()

    fun getPatchAlarm(): Single<AeCodeResponse> {
        return isReady()
            .concatMapSingle<AeCodeResponse>(Function { ALARM_ALERT_ERROR_CODE_GET.get() })
            .doOnNext(Consumer { response: AeCodeResponse -> this.checkResponse(response) })
            .firstOrError()
            .doOnSuccess(Consumer { aeCodeResponse: AeCodeResponse -> alarmRegistry.add(aeCodeResponse.alarmCodes) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "FetchAlarmTask error") })
    }

    @Synchronized override fun enqueue() {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = getPatchAlarm()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe()
        }
    }
}
