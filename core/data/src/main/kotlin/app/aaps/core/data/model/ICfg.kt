package app.aaps.core.data.model

data class ICfg(
    var insulinLabel: String,
    var insulinEndTime: Long, // DIA before [milliseconds]
    var peak: Long // [milliseconds]
) {
    fun isEqual(iCfg: ICfg) : Boolean {
        if (insulinLabel != iCfg.insulinLabel)
            return false
        if (insulinEndTime != iCfg.insulinEndTime)
            return false
        if (peak != iCfg.peak)
            return false
        return true
    }
}