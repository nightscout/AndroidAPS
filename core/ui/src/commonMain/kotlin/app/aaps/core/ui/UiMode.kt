package app.aaps.core.ui

enum class UiMode(val stringValue: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system")
    ;

    companion object {

        fun fromString(name: String?) = entries.firstOrNull { it.stringValue == name } ?: SYSTEM
    }
}