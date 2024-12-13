package app.aaps.pump.eopatch.ble.task

import app.aaps.pump.eopatch.core.api.BasalHistoryGetExBig
import app.aaps.pump.eopatch.core.api.BasalHistoryIndexGet
import app.aaps.pump.eopatch.core.api.TempBasalHistoryGetExBig
import app.aaps.pump.eopatch.core.response.BasalHistoryIndexResponse
import app.aaps.pump.eopatch.core.response.BasalHistoryResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused", "PrivatePropertyName")
@Singleton
class SyncBasalHistoryTask @Inject constructor() : TaskBase(TaskFunc.SYNC_BASAL_HISTORY) {

    private val BASAL_HISTORY_INDEX_GET: BasalHistoryIndexGet = BasalHistoryIndexGet()
    private val BASAL_HISTORY_GET_EX_BIG: BasalHistoryGetExBig = BasalHistoryGetExBig()
    private val TEMP_BASAL_HISTORY_GET_EX_BIG: TempBasalHistoryGetExBig = TempBasalHistoryGetExBig()

    fun sync(end: Int): Single<Int> {
        return Single.just<Int>(1) // 베이젤 싱크 사용 안함
    }

    fun sync(): Single<Int> {
        return Single.just<Int>(1) // 베이젤 싱크 사용 안함
    }

    private fun getLastIndex(): Single<Int> {
        return BASAL_HISTORY_INDEX_GET.get()
            .doOnSuccess(Consumer { response: BasalHistoryIndexResponse -> this.checkResponse(response) })
            .map<Int>(Function { obj: BasalHistoryIndexResponse -> obj.lastFinishedIndex })
    }

    private fun syncBoth(start: Int, end: Int): Single<Int> {
        val count = end - start + 1

        return if (count > 0) {
            Single.zip<BasalHistoryResponse, BasalHistoryResponse, Int>(
                BASAL_HISTORY_GET_EX_BIG.get(start, count),
                TEMP_BASAL_HISTORY_GET_EX_BIG.get(start, count),
                BiFunction { normal: BasalHistoryResponse, temp: BasalHistoryResponse -> onBasalHistoryResponse(normal, temp, start, end) })
        } else {
            Single.just<Int>(-1)
        }
    }

    @Synchronized fun enqueue(end: Int) {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = sync(end)
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe()
        }
    }

    @Synchronized override fun enqueue() {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = sync()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe()
        }
    }

    private fun onBasalHistoryResponse(n: BasalHistoryResponse, t: BasalHistoryResponse, startRequested: Int, end: Int): Int {
        if (!n.isSuccess || !t.isSuccess || n.seq != t.seq) {
            return -1
        }

        val start = n.seq

        val normal = n.injectedDoseValues
        val temp = t.injectedDoseValues

        //        int count = Math.min(end - start + 1, BASAL_HISTORY_SIZE_BIG);
//        count = Math.min(count, normal.length);
//        count = Math.min(count, temp.length);
        return updateInjected(normal, temp, start, end)
    }

    @Synchronized fun updateInjected(normal: FloatArray, temp: FloatArray, start: Int, end: Int): Int {
        if (pm.patchState.isPatchInternalSuspended && !patchConfig.isInBasalPausedTime) {
            return -1
        }

        var lastUpdatedIndex = -1
        var count = end - start + 1

        if (count > normal.size) {
            count = normal.size
        }

        if (count > 0) {
            val lastSyncIndex = patchConfig.lastIndex
            for (i in 0 until count) {
                val seq = start + i
                if (seq < lastSyncIndex) continue

                if (start <= seq && seq <= end) {
                    lastUpdatedIndex = seq
                }
            }
        }

        return lastUpdatedIndex
    }

    private fun updatePatchLastIndex(newIndex: Int) {
        val lastIndex = patchConfig.lastIndex

        if (lastIndex < newIndex) {
            patchConfig.lastIndex = newIndex
            pm.flushPatchConfig()
        }
    }
}