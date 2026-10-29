package app.aaps.cgm.dexcomg7.protocol

/**
 * What the Data Matrix on a sensor applicator says.
 *
 * The code is a GS1 element string. For pairing we need the pairing code in AI (240) and the sensor
 * serial in AI (21). The serial lets pairing skip sensors that cannot be this one. The GTIN in AI (01)
 * tells us Dexcom made it.
 */
class G7SensorPackage private constructor(
    val gtin: String?,
    val serial: String?,
    val pairingCode: String?,
    val lot: String?,
    val expiry: String?
) {

    val isDexcom: Boolean get() = gtin?.length == 14 && gtin.startsWith(DEXCOM_COMPANY_PREFIX)

    companion object {

        /** The first digits of a Dexcom GTIN-14 as it appears in the barcode. */
        const val DEXCOM_COMPANY_PREFIX = "0038627"

        /** Returns null when nothing useful was found, so a random barcode is not taken for an applicator. */
        fun parse(payload: String): G7SensorPackage? {
            val elements = Gs1ElementString.parse(payload)
            val candidate = elements["240"]
            val pairingCode = candidate?.takeIf { it.length == 4 && it.all { c -> c in '0'..'9' } }
            val gtin = elements["01"]
            val serial = elements["21"]
            if (gtin == null && serial == null && pairingCode == null) return null
            return G7SensorPackage(gtin = gtin, serial = serial, pairingCode = pairingCode, lot = elements["10"], expiry = elements["17"])
        }
    }
}

/**
 * A small GS1 element string parser: enough of the application identifier table to read an applicator.
 *
 * Fixed-length identifiers run straight into the next one. Variable-length ones end at a group
 * separator (FNC1, sent as ASCII 0x1D) or at the end. Scanners sometimes put a symbology identifier
 * (`]d2`) or a leading FNC1 in front; both are skipped.
 */
object Gs1ElementString {

    private const val GROUP_SEPARATOR = '\u001D'

    private val fixedLengths = mapOf(
        "00" to 18, "01" to 14, "02" to 14,
        "11" to 6, "12" to 6, "13" to 6, "15" to 6, "16" to 6, "17" to 6,
        "20" to 2,
        "31" to 8, "32" to 8, "33" to 8, "34" to 8, "35" to 8, "36" to 8,
        "41" to 15
    )

    /** Known identifiers, longest first, so "240" is matched before "24" could be. */
    private val knownIdentifiers = listOf(
        "240", "241", "242", "243", "250", "251", "253", "254", "255",
        "00", "01", "02", "10", "11", "12", "13", "15", "16", "17",
        "20", "21", "22", "30", "37", "90", "91", "92", "93", "94",
        "95", "96", "97", "98", "99"
    )

    fun parse(payload: String): Map<String, String> {
        var input = payload
        if (input.startsWith("]d2")) input = input.drop(3)
        input = input.trimStart(GROUP_SEPARATOR)

        val elements = mutableMapOf<String, String>()
        while (input.isNotEmpty()) {
            // An unknown identifier means the rest cannot be framed safely.
            val identifier = knownIdentifiers.firstOrNull { input.startsWith(it) } ?: break
            input = input.drop(identifier.length)

            val value: String
            val length = fixedLengths[identifier]
            if (length != null) {
                value = input.take(length)
                input = input.drop(length)
            } else {
                val separator = input.indexOf(GROUP_SEPARATOR)
                if (separator >= 0) {
                    value = input.substring(0, separator)
                    input = input.substring(separator + 1)
                } else {
                    value = input
                    input = ""
                }
            }
            // Some encoders put a separator after a fixed-length field too.
            input = input.trimStart(GROUP_SEPARATOR)
            elements.putIfAbsent(identifier, value)
        }
        return elements
    }
}
