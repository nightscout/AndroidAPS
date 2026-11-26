package app.aaps.pump.aaruy.common.enums

/**
 * The model the pump reports in its settings. B200A and B200C can loop, see `AaruyPump.pumpType`.
 * @param productCode the name the vendor server knows the model by. "AR-UNKOWN" is spelled as the vendor app sends it.
 */
enum class AaruyType(val value: Int, val productCode: String) {
    INVALID(-1, "AR-UNKOWN"),
    B200D(0, "AR-B200D"),
    B200C(1, "AR-B200C"),
    B200B(2, "AR-B200B"),
    B200A(3, "AR-B200A");

    companion object {

        fun fromValue(value: Int): AaruyType = entries.find { it.value == value } ?: INVALID
    }
}
