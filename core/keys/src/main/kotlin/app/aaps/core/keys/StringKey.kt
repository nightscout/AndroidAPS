package app.aaps.core.keys

enum class StringKey(override val key: Int, val defaultValue: String, override val affectedBySM: Boolean) : PreferenceKey {
    GeneralLanguage(R.string.key_language, "default", affectedBySM = true)
}