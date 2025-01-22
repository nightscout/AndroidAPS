package app.aaps.core.data.model

data class ICfg(
    var insulinLabel: String,
    var insulinEndTime: Long, // DIA before [milliseconds]
    var peak: Long // [milliseconds]
) {
    constructor(insulinLabel: String, dia: Double, peak: Int) : this(insulinLabel, (dia * 3600 * 1000).toLong(), (peak * 60000).toLong())

    fun isEqual(iCfg: ICfg) : Boolean {
        //if (insulinLabel != iCfg.insulinLabel)
        //    return false
        if (insulinEndTime != iCfg.insulinEndTime)
            return false
        if (peak != iCfg.peak)
            return false
        return true
    }

    fun getDia(): Double = insulinEndTime / 3600.0 / 1000.0

    fun getPeak(): Int = (peak / 60000).toInt()

    fun setDia(dia: Double) {
        insulinEndTime = (dia * 3600 * 1000).toLong()
    }

    fun setPeak(peak: Int) {
        this.peak = (peak * 60000).toLong()
    }

    fun deepClone(): ICfg = ICfg(insulinLabel, insulinEndTime, peak)
}