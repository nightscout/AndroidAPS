package app.aaps.pump.aaruy.common.enums

/** The model the pump reports in its settings. B200A and B200C can loop, see `AaruyPump.pumpType`. */
enum class AaruyType(val value: Int) {
    INVALID(-1),
    B200D(0),
    B200C(1),
    B200B(2),
    B200A(3);

    companion object {

        fun fromValue(value: Int): AaruyType = entries.find { it.value == value } ?: INVALID
    }
}
