package app.aaps.core.data.model

data class ICfg(
    var insulinLabel: String,
    var insulinEndTime: Long, // DIA before [milliseconds]
    var peak: Long // [milliseconds]
)