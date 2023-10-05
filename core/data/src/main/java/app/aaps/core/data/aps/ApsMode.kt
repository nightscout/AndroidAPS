package app.aaps.core.data.aps

enum class ApsMode {
    OPEN,
    CLOSED,
    LGS,
    UNDEFINED;

    companion object {

        fun fromString(stringValue: String?) = entries.firstOrNull { it.name == stringValue?.uppercase() } ?: UNDEFINED
    }
}
