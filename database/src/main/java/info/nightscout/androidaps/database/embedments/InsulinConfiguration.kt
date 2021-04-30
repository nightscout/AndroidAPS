package info.nightscout.androidaps.database.embedments

data class InsulinConfiguration(
        var insulinLabel: String,
        var insulinEndTime: Long, // DIA before [milliseconds]
        var peak: Long // [milliseconds]
)