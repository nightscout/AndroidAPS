package app.aaps.pump.omnipod.common.history.data

import app.aaps.pump.omnipod.common.definition.OmnipodCommandType

data class HistoryRecord(
    val id: Long,
    val createdAt: Long,
    val date: Long,
    val commandType: OmnipodCommandType,
    val initialResult: InitialResult,
    val record: Record?,
    val totalAmountDelivered: Double?,
    val resolvedResult: ResolvedResult?,
    val resolvedAt: Long?
) {

    fun pumpId(): Long = id

    fun displayTimestamp(): Long = date

    fun isSuccess(): Boolean = initialResult == InitialResult.SENT && resolvedResult == ResolvedResult.SUCCESS
}
