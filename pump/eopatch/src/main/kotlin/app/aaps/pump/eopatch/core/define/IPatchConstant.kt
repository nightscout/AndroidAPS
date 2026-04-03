package app.aaps.pump.eopatch.core.define

import java.util.concurrent.TimeUnit

interface IPatchConstant {

    companion object {

        val NOW_BOLUS_ID: Short = 0xEFFE.toShort()
        val EXT_BOLUS_ID: Short = 0xEFFF.toShort()
        val BOLUS_EXTENDED_DURATION_STEP: Byte = 30
        val WARRANTY_OPERATING_LIFE_MILLI: Long = TimeUnit.HOURS.toMillis(84)
        val SERVICE_TIME_MILLI: Long = TimeUnit.HOURS.toMillis(12)
        val BASAL_SEQ_MAX: Int = 1153
        val BASAL_HISTORY_SIZE_BIG: Int = 220
    }
}
