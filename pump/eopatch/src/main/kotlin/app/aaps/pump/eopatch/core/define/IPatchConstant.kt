package app.aaps.pump.eopatch.core.define

import kotlin.time.Duration.Companion.hours

interface IPatchConstant {

    companion object {

        val NOW_BOLUS_ID: Short = 0xEFFE.toShort()
        val EXT_BOLUS_ID: Short = 0xEFFF.toShort()
        val BOLUS_EXTENDED_DURATION_STEP: Byte = 30
        val WARRANTY_OPERATING_LIFE_MILLI: Long = 84.hours.inWholeMilliseconds
        val SERVICE_TIME_MILLI: Long = 12.hours.inWholeMilliseconds
        val BASAL_SEQ_MAX: Int = 1153
        val BASAL_HISTORY_SIZE_BIG: Int = 220
    }
}
