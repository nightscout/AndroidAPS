package app.aaps.pump.omnipod.eros.history

import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordDao
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordEntity
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

class ErosHistory(private val dao: ErosHistoryRecordDao) {

    fun getAllErosHistoryRecordsFromTimestamp(timeInMillis: Long): List<ErosHistoryRecordEntity> {
        return dao.allSinceAsc(timeInMillis)
            .subscribeOn(Schedulers.io())
            .blockingGet()
    }

    fun findErosHistoryRecordByPumpId(pumpId: Long): ErosHistoryRecordEntity? {
        val record = dao.byId(pumpId)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()
            .blockingGet()
        return if (record is ValueWrapper.Existing) record.value else null
    }

    fun create(historyRecord: ErosHistoryRecordEntity?): Long =
        Single.fromCallable { dao.insert(historyRecord!!) }
            .subscribeOn(Schedulers.io())
            .blockingGet()
}

@Suppress("USELESS_CAST")
inline fun <reified T : Any> Maybe<T>.toWrappedSingle(): Single<ValueWrapper<T>> =
    this.map { ValueWrapper.Existing(it) as ValueWrapper<T> }
        .switchIfEmpty(Maybe.just(ValueWrapper.Absent()))
        .toSingle()

sealed class ValueWrapper<T> {
    data class Existing<T>(val value: T) : ValueWrapper<T>()
    class Absent<T> : ValueWrapper<T>()
}