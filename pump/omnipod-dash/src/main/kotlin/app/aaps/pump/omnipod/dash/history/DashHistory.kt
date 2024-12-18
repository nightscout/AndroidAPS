package app.aaps.pump.omnipod.dash.history

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType.SET_BOLUS
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType.SET_TEMPORARY_BASAL
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodConstants.Companion.POD_PULSE_BOLUS_UNITS
import app.aaps.pump.omnipod.dash.driver.pod.state.CommandConfirmationDenied
import app.aaps.pump.omnipod.dash.driver.pod.state.CommandConfirmationSuccess
import app.aaps.pump.omnipod.dash.driver.pod.state.CommandSendingFailure
import app.aaps.pump.omnipod.dash.driver.pod.state.CommandSendingNotConfirmed
import app.aaps.pump.omnipod.dash.driver.pod.state.NoActiveCommand
import app.aaps.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.dash.history.data.BolusRecord
import app.aaps.pump.omnipod.dash.history.data.HistoryRecord
import app.aaps.pump.omnipod.dash.history.data.InitialResult
import app.aaps.pump.omnipod.dash.history.data.ResolvedResult
import app.aaps.pump.omnipod.dash.history.data.TempBasalRecord
import app.aaps.pump.omnipod.dash.history.database.HistoryRecordDao
import app.aaps.pump.omnipod.dash.history.database.HistoryRecordEntity
import app.aaps.pump.omnipod.dash.history.mapper.HistoryMapper
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import java.lang.System.currentTimeMillis
import javax.inject.Inject

class DashHistory @Inject constructor(
    private val dao: HistoryRecordDao,
    private val historyMapper: HistoryMapper,
    private val logger: AAPSLogger
) {

    private fun markSuccess(id: Long): Completable = dao.markResolved(
        id,
        ResolvedResult.SUCCESS,
        currentTimeMillis()
    )

    private fun markFailure(id: Long): Completable = dao.markResolved(
        id,
        ResolvedResult.FAILURE,
        currentTimeMillis()
    )

    fun getById(id: Long): HistoryRecord {
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
        totalAmountDeliveredRecord: Double? = null,
        resolveResult: ResolvedResult? = null,
        resolvedAt: Long? = null
    ): Single<Long> = Single.defer {
        var id: Long = 0
        if (dao.first() == null) {
            id = currentTimeMillis()
        }
        when {
            commandType == SET_BOLUS && bolusRecord == null               ->
                Single.error(IllegalArgumentException("bolusRecord missing on SET_BOLUS"))

            commandType == SET_TEMPORARY_BASAL && tempBasalRecord == null ->
                Single.error(IllegalArgumentException("tempBasalRecord missing on SET_TEMPORARY_BASAL"))

            else                                                          ->
                dao.save(
                    HistoryRecordEntity(
                        id = id,
                        date = date,
                        createdAt = currentTimeMillis(),
                        commandType = commandType,
                        tempBasalRecord = tempBasalRecord,
                        bolusRecord = bolusRecord,
                        basalProfileRecord = basalProfileRecord,
                        totalAmountDelivered = totalAmountDeliveredRecord,
                        initialResult = initialResult,
                        resolvedResult = resolveResult,
                        resolvedAt = resolvedAt
                    )
                )
        }
    }

    fun getRecords(): Single<List<HistoryRecord>> =
        dao.all().map { list -> list.map(historyMapper::entityToDomain) }

    fun getRecordsAfter(time: Long): Single<List<HistoryRecord>> =
        dao.allSince(time).map { list -> list.map(historyMapper::entityToDomain) }

    fun updateFromState(podState: OmnipodDashPodStateManager): Completable = Completable.defer {
        val historyId = podState.activeCommand?.historyId
        if (historyId == null) {
            logger.error(LTag.PUMP, "HistoryId not found to for updating from state")
            return@defer Completable.complete()
        }

        val setTotalAmountDelivered = dao.setTotalAmountDelivered(historyId, podState.pulsesDelivered?.times(POD_PULSE_BOLUS_UNITS))

        val commandConfirmation = when (podState.getCommandConfirmationFromState()) {
            CommandSendingFailure      ->
                dao.setInitialResult(historyId, InitialResult.FAILURE_SENDING)

            CommandSendingNotConfirmed ->
                dao.setInitialResult(historyId, InitialResult.SENT)

            CommandConfirmationDenied  ->
                markFailure(historyId)

            CommandConfirmationSuccess ->
                dao.setInitialResult(historyId, InitialResult.SENT)
                    .andThen(markSuccess(historyId))

            NoActiveCommand            ->
                Completable.complete()
        }

        Completable.concat(listOf(setTotalAmountDelivered, commandConfirmation))
    }
}
