package app.aaps.core.ui.compose.pump

/**
 * Common interface for pump history records. Implemented by pump-specific Room entities
 * (e.g. DanaHistoryRecord, DiaconnHistoryRecord).
 */
interface PumpHistoryRecord {

    val timestamp: Long
    val code: Byte
    val value: Double
    val bolusType: String
    val stringValue: String
    val durationMinutes: Int
    val dailyBasal: Double
    val dailyBolus: Double
    val alarm: String
}

data class PumpHistoryType(val type: Byte, val name: String)

data class PumpHistoryUiState<T : PumpHistoryRecord>(
    val selectedType: PumpHistoryType? = null,
    val records: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val availableTypes: List<PumpHistoryType> = emptyList()
)
