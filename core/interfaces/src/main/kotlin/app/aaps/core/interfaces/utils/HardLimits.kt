package app.aaps.core.interfaces.utils

interface HardLimits {
    companion object {

        val MAX_BOLUS = doubleArrayOf(5.0, 10.0, 17.0, 25.0, 60.0)

        // Very Hard Limits Ranges
        // First value is the Lowest and second value is the Highest a Limit can define
        val LIMIT_MIN_BG = doubleArrayOf(80.0, 180.0)
        val LIMIT_MAX_BG = doubleArrayOf(90.0, 200.0)
        val LIMIT_TARGET_BG = doubleArrayOf(80.0, 200.0)

        // Very Hard Limits Ranges for Temp Targets
        val LIMIT_TEMP_MIN_BG = doubleArrayOf(72.0, 180.0)
        val LIMIT_TEMP_MAX_BG = doubleArrayOf(72.0, 270.0)
        val LIMIT_TEMP_TARGET_BG = doubleArrayOf(72.0, 200.0)
        val MIN_DIA = doubleArrayOf(5.0, 5.0, 5.0, 5.0, 5.0)
        val MAX_DIA = doubleArrayOf(9.0, 9.0, 9.0, 9.0, 10.0)
        val MIN_IC = doubleArrayOf(2.0, 2.0, 2.0, 2.0, 0.3)
        val MAX_IC = doubleArrayOf(100.0, 100.0, 100.0, 100.0, 100.0)
        const val MIN_ISF = 2.0 // mgdl
        const val MAX_ISF = 1000.0 // mgdl
        val MAX_IOB_AMA = doubleArrayOf(3.0, 5.0, 7.0, 12.0, 25.0)
        val MAX_IOB_SMB = doubleArrayOf(7.0, 13.0, 22.0, 30.0, 70.0)
        val MAX_BASAL = doubleArrayOf(2.0, 5.0, 10.0, 12.0, 25.0)

        //LGS Hard limits
        //No IOB at all
        const val MAX_IOB_LGS = 0.0

        const val MAX_CARBS_DURATION_HOURS  = 10L
        const val MAX_CARBS  = 400
    }

    fun maxBolus(): Double
    fun maxIobAMA(): Double
    fun maxIobSMB(): Double
    fun maxBasal(): Double
    fun minDia(): Double
    fun maxDia(): Double
    fun minIC(): Double
    fun maxIC(): Double

    // safety checks
    fun checkHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean

    fun isInRange(value: Double, lowLimit: Double, highLimit: Double): Boolean

    fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double

    fun ageEntries(): Array<CharSequence>
    fun ageEntryValues(): Array<CharSequence>

    enum class AgeType { CHILD, TEENAGE, ADULT, RESISTANT_ADULT, PREGNANT }
}