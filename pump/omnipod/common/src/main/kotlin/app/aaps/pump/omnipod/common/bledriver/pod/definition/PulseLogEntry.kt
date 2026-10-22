package app.aaps.pump.omnipod.common.bledriver.pod.definition

/**
 * What kind of delivery activity a [PulseLogEntry] represents - the `ppp` bit-field
 * documented on the openaps/openomni wiki's "Pulse Log Entry" page.
 */
enum class PulseLogEntryType(val value: Int) {

    NONE(0),
    BASAL(1),
    TEMP_BASAL(2),
    BOLUS(3),
    EXTENDED_BOLUS(4),
    UNKNOWN(-1);

    companion object {

        fun fromValue(value: Int): PulseLogEntryType = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}

/**
 * A single decoded pulse-log dword, as returned in status pages 3/80/81 ([PodInfoPulseLogPlusResponse]
 * /[PodInfoPulseLogRecentResponse]/[PodInfoPulseLogPreviousResponse] - see
 * `app.aaps.pump.omnipod.common.bledriver.pod.response`). Bit layout ("eeeeee0a pppliiib
 * cccccccc dfgggggg") and field meanings are documented on the openaps/openomni project's
 * wiki (github.com/openaps/openomni/wiki/Pulse-Log-Entry) - the only place either OmnipodKit
 * or this codebase's own (production) Eros pulse-log classes turned out to have any semantic
 * decoding at all; both of those just store/display the raw dwords.
 *
 * [loadCountVal] is deliberately NOT converted to an insulin-unit amount here - the wiki
 * documents it as a motor-load measurement (`LoadCountVal = (((LOADCNT & 0x1FF) * 1000) +
 * 488) / 976`), not a pulse count, and no source found ties it to a confirmed units-per-count
 * scaling factor. Treat it as a diagnostic/relative value, not a dose quantity - asserting a
 * specific insulin amount here without that confirmation would be actively misleading in a
 * dosing-history context.
 *
 * Verified against the wiki's own worked example: dword `0x51213E00` decodes to
 * encoder=20, drivingFlag=LOAD2, pulseMode=BASAL, immediateBolusTick=0, loadCountVal=125,
 * faultFlag=false - matching this class's [PulseLogEntryTest] golden-value test exactly.
 */
data class PulseLogEntry(
    /** 6-bit encoder count (`eeeeee`, bits 31-26). */
    val encoder: Int,
    /** Which of the two drive coils is active (`a`, bit 24) - false = LOAD1, true = LOAD2. */
    val load2Active: Boolean,
    /** What kind of delivery this entry represents (`ppp`, bits 23-21). */
    val pulseMode: PulseLogEntryType,
    /** Low-reservoir signal at the time of this entry (`l`, bit 20). */
    val lowReservoir: Boolean,
    /** Immediate-bolus tick counter, in quarter-seconds (`iii`, bits 19-17). */
    val immediateBolusTick: Int,
    /** Motor-load measurement for this entry (`b`+`cccccccc`, bits 16-8) - see this class's
     *  doc comment for why this is not converted to an insulin-unit amount. */
    val loadCountVal: Int,
    /** Last analog comparator read (`d`, bit 7) - motor-diagnostic bit. */
    val lastComparatorRead: Boolean,
    /** Encoder-validation fault flag (`f`, bit 6). */
    val faultFlag: Boolean,
    /** Signed 6-bit last computed encoder value (`gggggg`, bits 5-0) - 0 is nominal. */
    val lastEncoderValue: Int
) {

    companion object {

        fun decode(dword: Int): PulseLogEntry {
            val encoder = (dword ushr 26) and 0x3F
            val load2Active = ((dword ushr 24) and 0x1) != 0
            val pulseMode = PulseLogEntryType.fromValue((dword ushr 21) and 0x7)
            val lowReservoir = ((dword ushr 20) and 0x1) != 0
            val immediateBolusTick = (dword ushr 17) and 0x7
            val loadCountValHigh = (dword ushr 8) and 0xFF
            val loadCountValLow = (dword ushr 16) and 0x1
            val loadCountVal = (loadCountValHigh shl 1) or loadCountValLow
            val lastComparatorRead = ((dword ushr 7) and 0x1) != 0
            val faultFlag = ((dword ushr 6) and 0x1) != 0
            val encoderRaw = dword and 0x3F
            val lastEncoderValue = if (encoderRaw and 0x20 != 0) encoderRaw - 64 else encoderRaw

            return PulseLogEntry(
                encoder = encoder,
                load2Active = load2Active,
                pulseMode = pulseMode,
                lowReservoir = lowReservoir,
                immediateBolusTick = immediateBolusTick,
                loadCountVal = loadCountVal,
                lastComparatorRead = lastComparatorRead,
                faultFlag = faultFlag,
                lastEncoderValue = lastEncoderValue
            )
        }

        fun decodeAll(dwords: List<Int>): List<PulseLogEntry> = dwords.map { decode(it) }
    }
}
