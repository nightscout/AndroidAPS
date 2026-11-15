package app.aaps.pump.omnipod.dash.history.mapper

import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import app.aaps.pump.omnipod.dash.history.database.HistoryRecordEntity

class HistoryMapper {

    fun entityToDomain(entity: HistoryRecordEntity): HistoryRecord =
        HistoryRecord(
            id = entity.id,
            createdAt = entity.createdAt,
            date = entity.date,
            initialResult = entity.initialResult,
            commandType = entity.commandType,
            record = entity.bolusRecord?.toDomain() ?: entity.tempBasalRecord ?: entity.basalProfileRecord,
            totalAmountDelivered = entity.totalAmountDelivered,
            resolvedResult = entity.resolvedResult,
            resolvedAt = entity.resolvedAt
        )
}
