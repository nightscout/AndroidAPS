package info.nightscout.androidaps.plugins.pump.omnipod.dash.history

import com.github.guepardoapps.kulid.ULID
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.HistoryRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.InitialResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.ResolvedResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.TempBasalRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.HistoryRecordDao
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.HistoryRecordEntity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.mapper.HistoryMapper
import io.reactivex.Completable
import io.reactivex.Single
import java.lang.System.currentTimeMillis
import javax.inject.Inject

class DashHistory @Inject constructor(
    private val dao: HistoryRecordDao,
    private val historyMapper: HistoryMapper
) {

    fun markSuccess(id: String): Completable = dao.markResolved(id, ResolvedResult.SUCCESS, currentTimeMillis()) // TODO pass time

    fun markFailure(id: String): Completable = dao.markResolved(id, ResolvedResult.FAILURE, currentTimeMillis()) // TODO pass time

    fun createRecord(
        commandType: OmnipodCommandType,
        initialResult: InitialResult = InitialResult.UNCONFIRMED,
        tempBasalRecord: TempBasalRecord? = null,
        bolusRecord: BolusRecord? = null,
        resolveResult: ResolvedResult? = null,
        resolvedAt: Long? = null
    ): Single<String> {
        val id = ULID.random()

        // TODO: verify that on OmnipodCommandType.SET_BOLUS bolusRecord is not null?
        // TODO: verify that on SET_TEMPORARY_BASAL tempBasalRecord is not null

        return dao.save(
            HistoryRecordEntity(
                id = id,
                createdAt = currentTimeMillis(), // TODO pass time (as date, keep createdAt)
                commandType = commandType,
                tempBasalRecord = tempBasalRecord,
                bolusRecord = bolusRecord,
                initialResult = initialResult,
                resolvedResult = resolveResult,
                resolvedAt = resolvedAt,
            )
        ).toSingle { id }
    }

    fun getRecords(): Single<List<HistoryRecord>> =
        dao.all().map { list -> list.map(historyMapper::entityToDomain) }

    fun getRecordsAfter(time: Long): Single<List<HistoryRecordEntity>> = dao.allSince(time)

}