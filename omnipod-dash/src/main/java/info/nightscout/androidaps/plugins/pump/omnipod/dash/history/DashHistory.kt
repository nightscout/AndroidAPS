package info.nightscout.androidaps.plugins.pump.omnipod.dash.history

import com.github.guepardoapps.kulid.ULID
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType.SET_BOLUS
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType.SET_TEMPORARY_BASAL
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.HistoryRecordDao
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.HistoryRecordEntity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.mapper.HistoryMapper
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import java.lang.System.currentTimeMillis
import javax.inject.Inject

class DashHistory @Inject constructor(
    private val dao: HistoryRecordDao,
    private val historyMapper: HistoryMapper,
    private val logger: AAPSLogger
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

    fun getById(id: String): HistoryRecord {
        val entry = dao.byIdBlocking(id)
            ?: throw java.lang.IllegalArgumentException("history entry [$id] not found")
        return historyMapper.entityToDomain(entry)
    }

    @Suppress("ReturnCount")
    fun createRecord(
        commandType: OmnipodCommandType,
        date: Long = currentTimeMillis(),
        initialResult: InitialResult = InitialResult.NOT_SENT,
        tempBasalRecord: TempBasalRecord? = null,
        bolusRecord: BolusRecord? = null,
        basalProfileRecord: BasalValuesRecord? = null,
        resolveResult: ResolvedResult? = null,
        resolvedAt: Long? = null
    ): Single<String> = Single.defer {
        val id = ULID.random()

        when {
            commandType == SET_BOLUS && bolusRecord == null ->
                Single.error(IllegalArgumentException("bolusRecord missing on SET_BOLUS"))
            commandType == SET_TEMPORARY_BASAL && tempBasalRecord == null ->
                Single.error(IllegalArgumentException("tempBasalRecord missing on SET_TEMPORARY_BASAL"))
            else ->
                dao.save(
                    HistoryRecordEntity(
                        id = id,
                        date = date,
                        createdAt = currentTimeMillis(),
                        commandType = commandType,
                        tempBasalRecord = tempBasalRecord,
                        bolusRecord = bolusRecord,
                        basalProfileRecord = basalProfileRecord,
                        initialResult = initialResult,
                        resolvedResult = resolveResult,
                        resolvedAt = resolvedAt
                    )
                ).toSingle { id }
        }
    }

    fun getRecords(): Single<List<HistoryRecord>> =
        dao.all().map { list -> list.map(historyMapper::entityToDomain) }

    fun getRecordsAfter(time: Long): Single<List<HistoryRecord>> =
        dao.allSince(time).map { list -> list.map(historyMapper::entityToDomain) }

    fun updateFromState(podState: OmnipodDashPodStateManager) = Completable.defer {
        val historyId = podState.activeCommand?.historyId
        if (historyId == null) {
            logger.error(LTag.PUMP, "HistoryId not found to for updating from state")
            return@defer Completable.complete()
        }
        when (podState.getCommandConfirmationFromState()) {
            CommandSendingFailure ->
                dao.setInitialResult(historyId, InitialResult.FAILURE_SENDING)
            CommandSendingNotConfirmed ->
                dao.setInitialResult(historyId, InitialResult.SENT)
            CommandConfirmationDenied ->
                markFailure(historyId)
            CommandConfirmationSuccess ->
                dao.setInitialResult(historyId, InitialResult.SENT)
                    .andThen(markSuccess(historyId))
            NoActiveCommand ->
                Completable.complete()
        }
    }
}
