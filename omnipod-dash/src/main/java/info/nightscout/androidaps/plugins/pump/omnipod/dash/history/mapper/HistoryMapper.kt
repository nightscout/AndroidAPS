package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.mapper

import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.HistoryRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.TempBasalRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.HistoryRecordEntity

class HistoryMapper {

    fun domainToEntity(historyRecord: HistoryRecord): HistoryRecordEntity =
        HistoryRecordEntity(
            id = historyRecord.id,
            createdAt = historyRecord.createdAt,
            date = historyRecord.date,
            commandType = historyRecord.commandType,
            initialResult = historyRecord.initialResult,
            tempBasalRecord = historyRecord.record as? TempBasalRecord,
            bolusRecord = historyRecord.record as? BolusRecord,
            resolvedResult = historyRecord.resolvedResult,
            resolvedAt = historyRecord.resolvedAt
        )

    fun entityToDomain(entity: HistoryRecordEntity): HistoryRecord =
        HistoryRecord(
            id = entity.id,
            createdAt = entity.createdAt,
            date = entity.date,
            initialResult = entity.initialResult,
            commandType = entity.commandType,
            record = entity.bolusRecord ?: entity.tempBasalRecord,
            resolvedResult = entity.resolvedResult,
            resolvedAt = entity.resolvedAt
        )
}
