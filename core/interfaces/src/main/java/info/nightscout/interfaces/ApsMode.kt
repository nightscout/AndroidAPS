package info.nightscout.interfaces

enum class ApsMode {
    OPEN,
    CLOSED,
    LGS,
    UNDEFINED;

    companion object {

        fun secureValueOf(stringValue: String): ApsMode {
            return try {
                valueOf(stringValue)
            } catch (e: IllegalArgumentException) {
                UNDEFINED
            }
        }
    }
}
