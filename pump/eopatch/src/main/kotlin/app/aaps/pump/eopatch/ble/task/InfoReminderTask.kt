package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.InfoReminderSet
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName", "SpellCheckingInspection")
@Singleton
class InfoReminderTask @Inject constructor() : TaskBase(TaskFunc.INFO_REMINDER) {

    private val INFO_REMINDER_SET: InfoReminderSet = InfoReminderSet()

    /* alert delay 사용안함 */
    fun set(infoReminder: Boolean): Single<PatchBooleanResponse> {
        return isReady()
            .concatMapSingle<PatchBooleanResponse>(Function { INFO_REMINDER_SET.set(infoReminder) })
            .doOnNext(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
            .firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "InfoReminderTask error") })
    }

    @Synchronized override fun enqueue() {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = set(patchConfig.infoReminder)
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe()
        }
    }
}
