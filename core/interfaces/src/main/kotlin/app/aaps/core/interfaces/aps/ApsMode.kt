package app.aaps.core.interfaces.aps

enum class ApsMode {
    OPEN,
    CLOSED,
    LGS,
    UNDEFINED;

    companion object {

        fun fromString(stringValue: String?) = values().firstOrNull { it.name == stringValue?.uppercase() } ?: UNDEFINED
    }
}
