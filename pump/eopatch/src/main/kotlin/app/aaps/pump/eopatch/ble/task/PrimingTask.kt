package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.StartPriming
import app.aaps.pump.eopatch.core.api.UpdateConnection
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.core.response.UpdateConnectionResponse
import app.aaps.pump.eopatch.vo.PatchState
import app.aaps.pump.eopatch.vo.PatchState.Companion.create
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import java.lang.Exception
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class PrimingTask @Inject constructor() : TaskBase(TaskFunc.PRIMING) {

    private val UPDATE_CONNECTION: UpdateConnection = UpdateConnection()
    private val START_PRIMING: StartPriming = StartPriming()

    fun start(count: Long): Observable<Long> {
        return isReady().concatMapSingle<PatchBooleanResponse>(Function { START_PRIMING.start() })
            .doOnNext(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
            .flatMap<Long>(Function { observePrimingSuccess(count) })
            .takeUntil(Predicate { value: Long -> (value == count) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "PrimingTask error") })
    }

    private fun observePrimingSuccess(count: Long): Observable<Long> {
        return Observable.merge<Long>(
            Observable.interval(1, TimeUnit.SECONDS).take(count + 10)
                .map<Long>(Function { v: Long -> v * 3 })
                .doOnNext(Consumer { v: Long ->
                    if (v >= count) {
                        throw Exception("Priming failed")
                    }
                }),

            Observable.interval(3, TimeUnit.SECONDS)
                .concatMapSingle<UpdateConnectionResponse>(Function { UPDATE_CONNECTION.get() })
                .map<PatchState>(Function { response: UpdateConnectionResponse -> create(response.getPatchState(), System.currentTimeMillis()) })
                .filter(PatchState::isPrimingSuccess)
                .map<Long>(Function { count })
        )
    }
}
