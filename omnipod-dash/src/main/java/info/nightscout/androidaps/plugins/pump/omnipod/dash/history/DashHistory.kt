package info.nightscout.androidaps.plugins.pump.omnipod.dash.history

import com.github.guepardoapps.kulid.ULID
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType.SET_BOLUS
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType.SET_TEMPORARY_BASAL
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

    fun markSuccess(id: String, date: Long): Completable = dao.markResolved(id, ResolvedResult.SUCCESS, currentTimeMillis())

    fun markFailure(id: String, date: Long): Completable = dao.markResolved(id, ResolvedResult.FAILURE, currentTimeMillis())

    fun createRecord(
        commandType: OmnipodCommandType,
        date: Long,
        initialResult: InitialResult = InitialResult.UNCONFIRMED,
        tempBasalRecord: TempBasalRecord? = null,
        bolusRecord: BolusRecord? = null,
        resolveResult: ResolvedResult? = null,
        resolvedAt: Long? = null
    ): Single<String> {
        val id = ULID.random()

        when {
            commandType == SET_BOLUS && bolusRecord == null               ->
                Single.error(IllegalArgumentException("bolusRecord missing on SET_BOLUS"))
            commandType == SET_TEMPORARY_BASAL && tempBasalRecord == null ->
                Single.error<String>(IllegalArgumentException("tempBasalRecord missing on SET_TEMPORARY_BASAL"))
            else                                                          -> null
        }?.let { return it }


        return dao.save(
            HistoryRecordEntity(
                id = id,
                date = date,
                createdAt = currentTimeMillis(),
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