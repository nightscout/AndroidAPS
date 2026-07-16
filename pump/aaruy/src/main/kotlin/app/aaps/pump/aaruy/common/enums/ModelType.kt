package app.aaps.pump.aaruy.common.enums

enum class AaruyType(val value: Int) {
    INVALID(-1),
    B200D(0),
    B200C(1),
    B200B(2),
    B200A(3);

    companion object {
        fun fromValue(value: Int): AaruyType {
            return entries.find { it.value == value } ?: INVALID
        }

        fun getTypeString(value: Int): String {
            val type: AaruyType = entries.find { it.value == value } ?: INVALID
            var typeString: String = ""
            when (type) {
                AaruyType.B200A-> typeString = "AR-B200A"
                AaruyType.B200B -> typeString = "AR-B200B"
                AaruyType.B200C -> typeString = "AR-B200C"
                AaruyType.B200D -> typeString = "AR-B200D"
                AaruyType.INVALID -> typeString = "AR-UNKOWN"
            }
            return typeString
        }
    }
}
