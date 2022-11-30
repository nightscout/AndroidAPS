package info.nightscout.interfaces

enum class ApsMode(val lowercase: String) {
    OPEN("open"),
    CLOSED("closed"),
    LGS("lgs"),
    UNDEFINED("undefined");

    companion object {

        fun secureValueOf(stringValue: String): ApsMode {
            return try {
                valueOf(stringValue.uppercase())
            } catch (e: IllegalArgumentException) {
                UNDEFINED
            }
        }
    }
}
