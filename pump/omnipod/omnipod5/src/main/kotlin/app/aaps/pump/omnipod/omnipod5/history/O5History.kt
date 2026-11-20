package app.aaps.pump.omnipod.omnipod5.history

import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.common.history.data.BolusRecord
import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.history.data.ResolvedResult
import app.aaps.pump.omnipod.common.history.data.TempBasalRecord
import app.aaps.pump.omnipod.omnipod5.history.database.HistoryRecordDao
import app.aaps.pump.omnipod.omnipod5.history.database.HistoryRecordEntity
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.Completable

@Inject
class O5History(
    private val dao: HistoryRecordDao,
    private val mapper: HistoryMapper
) {

    fun recordSuccess(
        commandType: OmnipodCommandType,
        date: Long = System.currentTimeMillis(),
        bolusRecord: BolusRecord? = null,
        tempBasalRecord: TempBasalRecord? = null,
        basalProfileRecord: BasalValuesRecord? = null,
        totalAmountDelivered: Double? = null,
        initialResult: InitialResult = InitialResult.SENT,
        resolvedResult: ResolvedResult? = ResolvedResult.SUCCESS,
        resolvedAt: Long? = System.currentTimeMillis()
    ) {
        createRecord(
            commandType = commandType,
            date = date,
            bolusRecord = bolusRecord,
            tempBasalRecord = tempBasalRecord,
            basalProfileRecord = basalProfileRecord,
            totalAmountDelivered = totalAmountDelivered,
            initialResult = initialResult,
            resolvedResult = resolvedResult,
            resolvedAt = resolvedAt
        ).blockingGet()
    }

    fun recordFailure(commandType: OmnipodCommandType, date: Long = System.currentTimeMillis()) {
        createRecord(
            commandType = commandType,
            date = date,
            initialResult = InitialResult.FAILURE_SENDING,
            resolvedResult = ResolvedResult.FAILURE,
            resolvedAt = System.currentTimeMillis()
        ).blockingGet()
    }

    fun createRecord(
        commandType: OmnipodCommandType,
        date: Long = System.currentTimeMillis(),
        initialResult: InitialResult = InitialResult.SENT,
        bolusRecord: BolusRecord? = null,
        tempBasalRecord: TempBasalRecord? = null,
        basalProfileRecord: BasalValuesRecord? = null,
        totalAmountDelivered: Double? = null,
        resolvedResult: ResolvedResult? = null,
        resolvedAt: Long? = null
    ): Single<Long> = dao.save(
        HistoryRecordEntity(
            createdAt = System.currentTimeMillis(),
            date = date,
            commandType = commandType,
            initialResult = initialResult,
            tempBasalRecord = tempBasalRecord,
            bolusRecord = bolusRecord,
            basalProfileRecord = basalProfileRecord,
            totalAmountDelivered = totalAmountDelivered,
            resolvedResult = resolvedResult,
            resolvedAt = resolvedAt
        )
    )

    fun getById(id: Long): HistoryRecord =
        dao.byIdBlocking(id)?.let(mapper::toDomain)
            ?: throw IllegalArgumentException("history entry [$id] not found")

    fun markSuccess(id: Long): Completable =
        dao.markResolved(id, ResolvedResult.SUCCESS, System.currentTimeMillis())

    fun markFailure(id: Long): Completable =
        dao.markResolved(id, ResolvedResult.FAILURE, System.currentTimeMillis())

    fun markSent(id: Long): Completable =
        dao.setInitialResult(id, InitialResult.SENT)

    fun markSendingFailure(id: Long): Completable =
        dao.setInitialResult(id, InitialResult.FAILURE_SENDING)

    fun setTotalAmountDelivered(id: Long, amount: Double?): Completable =
        dao.setTotalAmountDelivered(id, amount)

    fun delete(record: HistoryRecord): Completable =
        dao.delete(
            HistoryRecordEntity(
                id = record.id,
                createdAt = record.createdAt,
                date = record.date,
                commandType = record.commandType,
                initialResult = record.initialResult,
                tempBasalRecord = record.record as? TempBasalRecord,
                bolusRecord = record.record as? BolusRecord,
                basalProfileRecord = record.record as? BasalValuesRecord,
                totalAmountDelivered = record.totalAmountDelivered,
                resolvedResult = record.resolvedResult,
                resolvedAt = record.resolvedAt
            )
        )

    fun getRecords(): Single<List<HistoryRecord>> =
        dao.all().map { records -> records.map(mapper::toDomain) }

    fun getRecordsAfter(time: Long): Single<List<HistoryRecord>> =
        dao.allSince(time).map { records -> records.map(mapper::toDomain) }
}
