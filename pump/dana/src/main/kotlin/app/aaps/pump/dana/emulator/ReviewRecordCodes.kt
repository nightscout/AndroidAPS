package app.aaps.pump.dana.emulator

/**
 * The pump's own record codes, as they appear in the first byte of a review-history record —
 * what `HistoryEventStore.buildReviewRecordData` writes and `DanaRSPacketHistory.handleMessage`
 * switches on.
 *
 * Deliberately **not** `RecordTypes`: those are the driver's internal categories, and the two do
 * not agree (`RecordTypes.RECORD_TYPE_BOLUS` is 0x02 here but not everywhere). Keeping the wire
 * codes named and in one place stops a magic number from drifting between the emulator and the
 * tests that seed it.
 */
object ReviewRecordCodes {

    const val BOLUS = 0x02
    const val DAILY = 0x03
    const val PRIME = 0x04
    const val REFILL = 0x05
    const val GLUCOSE = 0x06
    const val CARBO = 0x07
    const val SUSPEND = 0x09
    const val ALARM = 0x0a
    const val BASAL_HOUR = 0x0b
    const val TEMP_BASAL = 0x99

    /**
     * Sub-codes for [BOLUS] — the record's 8th byte. The driver reads the bolus type from the high
     * nibble and a duration from the low nibble, so an extended bolus carries both.
     */
    object BolusType {

        const val STANDARD = 0x80
        const val EXTENDED = 0xC0
        const val DUAL_STANDARD = 0xA0
        const val DUAL_EXTENDED = 0x90
    }

    /** Sub-codes for [ALARM] — the record's 8th byte, an ASCII letter naming the alarm. */
    object Alarm {

        val BASAL_COMPARE = 'P'.code
        val EMPTY_RESERVOIR = 'R'.code
        val CHECK = 'C'.code
        val OCCLUSION = 'O'.code
        val BASAL_MAX = 'M'.code
        val DAILY_MAX = 'D'.code
        val LOW_BATTERY = 'B'.code
        val SHUTDOWN = 'S'.code
    }
}
