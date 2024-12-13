package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.GetGlobalTime
import app.aaps.pump.eopatch.core.api.SetGlobalTime
import app.aaps.pump.eopatch.core.response.GlobalTimeResponse
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Suppress("unused", "PrivatePropertyName")
@Singleton
class SetGlobalTimeTask @Inject constructor() : TaskBase(TaskFunc.SET_GLOBAL_TIME) {

    private val SET_GLOBAL_TIME: SetGlobalTime = SetGlobalTime()
    private val GET_GLOBAL_TIME: GetGlobalTime = GetGlobalTime()

    fun set(): Single<PatchBooleanResponse> {
        return isReady()
            .concatMapSingle<GlobalTimeResponse>(Function { GET_GLOBAL_TIME.get(false) })
            .doOnNext(Consumer { response: GlobalTimeResponse -> this.checkResponse(response) })
            .doOnNext(Consumer { response: GlobalTimeResponse -> this.checkPatchTime(response) })
            .concatMapSingle<PatchBooleanResponse>(Function { SET_GLOBAL_TIME.set() })
            .doOnNext(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
            .firstOrError()
            .doOnSuccess(Consumer { onSuccess() })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "SetGlobalTimeTask error") })
    }

    @Throws(Exception::class) private fun checkPatchTime(response: GlobalTimeResponse) {
        val newMilli = System.currentTimeMillis()
        val oldMilli = response.globalTimeInMilli
        val oldOffset = response.timeZoneOffset.toLong()
        val offset = TimeZone.getDefault().getOffset(newMilli)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(offset.toLong()).toInt()
        val newOffset = minutes / 15

        val diff: Long = abs(oldMilli - newMilli)

        if (diff > 60000 || oldOffset != newOffset.toLong()) {
            aapsLogger.debug(LTag.PUMPCOMM, String.format("checkPatchTime %s %s %s", diff, oldOffset, newOffset))
            return
        }

        throw Exception("No time set required")
    }

    @Synchronized override fun enqueue() {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = set()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe(Consumer { }, Consumer { }) // Exception 을 사용하기에...
        }
    }

    private fun onSuccess() {
    }
}
