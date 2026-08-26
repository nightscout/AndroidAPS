package app.aaps.pump.carelevo.presentation.model

data class CarelevoOverviewUiModel(
    val serialNumber: String,
    val lotNumber: String,
    val bootDateTimeUi: String,
    val expirationTime: String,
    val infusionStatus: Int?,
    val insulinRemainText: String,
    val totalBasal: Double,
    val totalBolus: Double,
    val isPumpStopped: Boolean,
    val runningRemainMinutes: Int
)
