package app.aaps.pump.omnipod.omnipod5.history

import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import app.aaps.pump.omnipod.omnipod5.history.database.HistoryRecordEntity

class HistoryMapper {

    fun toDomain(entity: HistoryRecordEntity): HistoryRecord = HistoryRecord(
        id = entity.id,
        createdAt = entity.createdAt,
        date = entity.date,
        commandType = entity.commandType,
        initialResult = entity.initialResult,
        record = entity.bolusRecord ?: entity.tempBasalRecord ?: entity.basalProfileRecord,
        totalAmountDelivered = entity.totalAmountDelivered,
        resolvedResult = entity.resolvedResult,
        resolvedAt = entity.resolvedAt
    )
}
