package app.aaps.core.data.model

data class ICfg(
    var insulinLabel: String,
    var insulinEndTime: Long, // DIA before [milliseconds]
    var insulinPeakTime: Long // [milliseconds]
) {

    constructor(insulinLabel: String, peak: Int, dia: Double) : this(insulinLabel = insulinLabel, insulinEndTime = (dia * 3600 * 1000).toLong(), insulinPeakTime = (peak * 60000).toLong())

    fun isEqual(iCfg: ICfg?): Boolean {
        iCfg?.let { iCfg ->
            if (insulinEndTime != iCfg.insulinEndTime)
                return false
            if (insulinPeakTime != iCfg.insulinPeakTime)
                return false
            return true
        }
        return false
    }

    fun getDia(): Double = insulinEndTime / 3600.0 / 1000.0
    fun getPeak(): Int = (insulinPeakTime / 60000).toInt()
    fun setDia(dia: Double) {
        insulinEndTime = (dia * 3600 * 1000).toLong()
    }

    fun setPeak(peak: Int) {
        this.insulinPeakTime = (peak * 60000).toLong()
    }

    fun deepClone(): ICfg = ICfg(insulinLabel, insulinEndTime, insulinPeakTime)

    companion object;
}