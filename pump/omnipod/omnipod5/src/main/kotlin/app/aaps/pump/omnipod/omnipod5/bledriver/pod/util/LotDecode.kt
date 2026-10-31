package app.aaps.pump.omnipod.omnipod5.bledriver.pod.util

import java.util.Calendar
import java.util.Locale

/**
 * Decoded fields of a modern (Dash / Omnipod 5) 32-bit Insulet lot number.
 *
 * Ported from OmnipodKit's LotDecode.swift (loopandlearn/OmnipodKit). Does NOT work
 * for older (Eros and earlier) lot numbers, which use a different, simpler encoding
 * already handled elsewhere in the Eros driver.
 */
data class LotDecode(
    val lot: Long,
    val lotHex: String,
    val prefix: String,
    val productNum: Int,
    val productCode: String,
    val locationNum: Int,
    val locationCode: String,
    val dateMMDD: String,
    val dateYY: Int,
    val line: Int,
    val batch: String,
    val readableText: String
) {

    companion object {

        private val PRODUCT_CODE: Map<Int, String> = mapOf(
            0x04 to "D1",
            0x18 to "D2",
            0x36 to "D5",

            0x07 to "H1",
            0x1B to "H2",
            0x39 to "H5",

            0x02 to "E1",
            0x16 to "E2",
            0x34 to "E5",

            0x05 to "P1",
            0x19 to "P2",
            0x37 to "P5",

            0x03 to "A0",
            0x09 to "R1"
        )

        private val MFG_LOC: Map<Int, String> = mapOf(
            0 to "C",
            1 to "U",
            2 to "K",
            6 to "M"
        )

        private fun mask(bits: Int): Long = (1L shl bits) - 1L

        /**
         * Returns the decoded lot information for a modern Insulet 32-bit lot #.
         * This function does not work for older (Eros and before) lot #s.
         *
         * @param lot the 32-bit lot number, passed as a Long so the full unsigned
         *            range fits without overflow.
         */
        fun decode(lot: Long): LotDecode {
            val lot32 = lot and 0xFFFFFFFFL

            val prefix = if ((lot32 and 0x80000000L) == 0L) "P" else "E"

            val productNum = ((lot32 shr 25) and mask(6)).toInt()
            val productCode = PRODUCT_CODE[productNum] ?: "XX"

            val locationNum = ((lot32 shr 22) and mask(3)).toInt()
            val locationCode = MFG_LOC[locationNum] ?: "X"

            val dayNumber = ((lot32 shr 7) and mask(15)).toInt()
            val dateYY = dayNumber shr 9
            val dayOfYear = dayNumber - (dateYY shl 9)

            val dateMMDD: String = if (dayOfYear > 0) {
                val calendar = Calendar.getInstance()
                calendar.clear()
                calendar.set(dateYY + 2000, Calendar.JANUARY, 1)
                calendar.add(Calendar.DAY_OF_YEAR, dayOfYear - 1)
                String.format(Locale.US, "%02d%02d", calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
            } else {
                "0000"
            }

            val line = ((lot32 shr 4) and mask(3)).toInt()
            val batch = (lot32 and mask(4)).toString(16).uppercase(Locale.US)

            val readableText = "$prefix$productCode$locationCode$dateMMDD$dateYY$line$batch"

            return LotDecode(
                lot = lot32,
                lotHex = String.format(Locale.US, "0x%08X", lot32),
                prefix = prefix,
                productNum = productNum,
                productCode = productCode,
                locationNum = locationNum,
                locationCode = locationCode,
                dateMMDD = dateMMDD,
                dateYY = dateYY,
                line = line,
                batch = batch,
                readableText = readableText
            )
        }
    }
}
