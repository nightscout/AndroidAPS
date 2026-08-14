package app.aaps.core.interfaces.utils

interface HardLimits {
    companion object {

        // Age dependent limits. Keyed by [AgeType] so a value can never be read with a wrong index.
        val MAX_BOLUS = mapOf(
            AgeType.CHILD to 5.0,
            AgeType.TEENAGE to 10.0,
            AgeType.ADULT to 17.0,
            AgeType.RESISTANT_ADULT to 25.0,
            AgeType.PREGNANT to 60.0
        )

        // Very Hard Limits Ranges [mg/dL]
        // The range says how low and how high the limit itself may be set
        // MIN_BG top is 180.2, not a flat 180.0. A 10.0 mmol low target (the highest the editor offers)
        // converts to 180.16 mg/dL, and the loop rounds the profile low target to 0.1 mg/dL BEFORE this
        // check (OpenAPSSMBPlugin: Round.roundTo(getTargetLowMgdl(), 0.1) -> 180.2). A tighter top (180.0,
        // or even the raw 180.16) makes the loop clamp a valid 10.0 mmol low target and warn every run.
        // (The temp-target low path is checked UNROUNDED, so LIMIT_TEMP_MIN_BG only needs 180.16.)
        val LIMIT_MIN_BG = 80.0..180.2
        val LIMIT_MAX_BG = 90.0..200.0
        val LIMIT_TARGET_BG = 80.0..200.0

        // Very Hard Limits Ranges for Temp Targets [mg/dL]
        // Top is 180.16 (= 10.0 mmol * 18.01559), not a flat 180.0. A user may pick 10.0 mmol as the
        // temp target — the highest value the range allows — and it converts to 180.16 mg/dL. With a
        // flat 180.0 the watch rejects that temp target and the loop clamps the bottom value to 180.00
        // and warns on every run.
        val LIMIT_TEMP_MIN_BG = 72.0..180.16
        val LIMIT_TEMP_MAX_BG = 72.0..270.0
        val LIMIT_TEMP_TARGET_BG = 72.0..200.0
        val LIMIT_DIA = mapOf(
            AgeType.CHILD to 5.0..9.0,
            AgeType.TEENAGE to 5.0..9.0,
            AgeType.ADULT to 5.0..9.0,
            AgeType.RESISTANT_ADULT to 5.0..9.0,
            AgeType.PREGNANT to 5.0..10.0
        )
        val LIMIT_PEAK = 35..120 // min
        val LIMIT_IC = mapOf(
            AgeType.CHILD to 2.0..100.0,
            AgeType.TEENAGE to 2.0..100.0,
            AgeType.ADULT to 2.0..100.0,
            AgeType.RESISTANT_ADULT to 2.0..100.0,
            AgeType.PREGNANT to 0.3..100.0
        )
        val LIMIT_ISF = 2.0..1000.0 // mgdl
        val MAX_IOB_AMA = mapOf(
            AgeType.CHILD to 3.0,
            AgeType.TEENAGE to 5.0,
            AgeType.ADULT to 7.0,
            AgeType.RESISTANT_ADULT to 12.0,
            AgeType.PREGNANT to 25.0
        )
        val MAX_IOB_SMB = mapOf(
            AgeType.CHILD to 7.0,
            AgeType.TEENAGE to 13.0,
            AgeType.ADULT to 22.0,
            AgeType.RESISTANT_ADULT to 30.0,
            AgeType.PREGNANT to 70.0
        )
        val MAX_BASAL = mapOf(
            AgeType.CHILD to 2.0,
            AgeType.TEENAGE to 5.0,
            AgeType.ADULT to 10.0,
            AgeType.RESISTANT_ADULT to 12.0,
            AgeType.PREGNANT to 25.0
        )

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
    fun diaRange(): ClosedFloatingPointRange<Double>
    fun peakRange(): IntRange
    fun icRange(): ClosedFloatingPointRange<Double>

    // safety checks
    fun checkHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean

    /** Same as [checkHardLimits], for the limit ranges defined above. */
    fun checkHardLimits(value: Double, valueName: Int, limits: ClosedFloatingPointRange<Double>): Boolean =
        checkHardLimits(value, valueName, limits.start, limits.endInclusive)

    fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double

    /** Same as [verifyHardLimits], for the limit ranges defined above. */
    fun verifyHardLimits(value: Double, valueName: Int, limits: ClosedFloatingPointRange<Double>): Double =
        verifyHardLimits(value, valueName, limits.start, limits.endInclusive)

    fun ageEntries(): Array<CharSequence>
    fun ageEntryValues(): Array<CharSequence>

    enum class AgeType { CHILD, TEENAGE, ADULT, RESISTANT_ADULT, PREGNANT }
}