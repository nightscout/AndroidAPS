package app.aaps.core.keys

enum class StringKeys(override val key: Int, val defaultValue: String) : Keys {
    GeneralLanguage(R.string.key_language, "default")
}