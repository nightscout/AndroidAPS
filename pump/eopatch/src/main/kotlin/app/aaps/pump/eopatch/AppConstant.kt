package app.aaps.pump.eopatch

interface AppConstant {
    companion object {

        const val BASAL_MIN_AMOUNT = 0.05f

        const val INSULIN_UNIT_P = 0.05f

        const val INSULIN_UNIT_STEP_U = INSULIN_UNIT_P

        const val OFF = 0
        const val ON = 1

        const val PUMP_DURATION_MILLI = 4 * 1000L

        const val BASAL_RATE_PER_HOUR_MIN = BASAL_MIN_AMOUNT

        const val SEGMENT_MAX_SIZE_48 = 48
        const val SEGMENT_COUNT_MAX = SEGMENT_MAX_SIZE_48

        const val BOLUS_ACTIVE_EXTENDED_WAIT = 0x2

        const val BOLUS_UNIT_STEP = INSULIN_UNIT_STEP_U

        const val DAY_START_MINUTE = 0 * 60
        const val DAY_END_MINUTE = 24 * 60
        const val INSULIN_DURATION_MIN = 2.0f
    }
}