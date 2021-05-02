package info.nightscout.androidaps.plugins.pump.omnipod.dash.history

import com.github.guepardoapps.kulid.ULID
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType.SET_BOLUS
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType.SET_TEMPORARY_BASAL
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
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

    private fun markSuccess(id: String): Completable = dao.markResolved(
        id,
        ResolvedResult.SUCCESS,
        currentTimeMillis()
    )

    private fun markFailure(id: String): Completable = dao.markResolved(
        id,
        ResolvedResult.FAILURE,
        currentTimeMillis()
    )

    @Suppress("ReturnCount")
    fun createRecord(
        commandType: OmnipodCommandType,
        date: Long = System.currentTimeMillis(),
        initialResult: InitialResult = InitialResult.NOT_SENT,
        tempBasalRecord: TempBasalRecord? = null,
        bolusRecord: BolusRecord? = null,
        resolveResult: ResolvedResult? = null,
        resolvedAt: Long? = null
    ): Single<String> {
        val id = ULID.random()

        when {
            commandType == SET_BOLUS && bolusRecord == null ->
                return Single.error(IllegalArgumentException("bolusRecord missing on SET_BOLUS"))
            commandType == SET_TEMPORARY_BASAL && tempBasalRecord == null ->
                return Single.error(IllegalArgumentException("tempBasalRecord missing on SET_TEMPORARY_BASAL"))
        }

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
                resolvedAt = resolvedAt
            )
        ).toSingle { id }
    }

    fun getRecords(): Single<List<HistoryRecord>> =
        dao.all().map { list -> list.map(historyMapper::entityToDomain) }

    fun getRecordsAfter(time: Long): Single<List<HistoryRecordEntity>> = dao.allSince(time)

    fun updateFromState(podState: OmnipodDashPodStateManager) = Completable.defer {
        podState.activeCommand?.run {
            when {

                createdRealtime <= podState.lastStatusResponseReceived &&
                    sequence == podState.sequenceNumberOfLastProgrammingCommand ->
                    dao.setInitialResult(historyId, InitialResult.SENT)
                        .andThen(markSuccess(historyId))

                createdRealtime <= podState.lastStatusResponseReceived &&
                    sequence != podState.sequenceNumberOfLastProgrammingCommand ->
                    markFailure(historyId)

                // no response received after this point
                createdRealtime <= sentRealtime ->
                    dao.setInitialResult(historyId, InitialResult.SENT)

                createdRealtime > sentRealtime ->
                    dao.setInitialResult(historyId, InitialResult.FAILURE_SENDING)

                else -> Completable.error(IllegalStateException("This can't happen. Could not update history"))
            }
        } ?: Completable.complete() // no active programming command
    }
}
