package info.nightscout.interfaces

enum class ApsMode {
    OPEN,
    CLOSED,
    LGS,
    UNDEFINED;

    companion object {

        fun fromString(stringValue: String?) = values().firstOrNull { it.name == stringValue?.uppercase() } ?: UNDEFINED
    }
}
