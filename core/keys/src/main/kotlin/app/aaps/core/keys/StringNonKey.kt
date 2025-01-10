package app.aaps.core.keys

enum class StringNonKey(
    override val key: String,
    override val defaultValue: String,
) : StringNonPreferenceKey {

    WearCwfWatchfaceName(key = "wear_cwf_watchface_name", defaultValue = ""),
    WearCwfAuthorVersion(key = "wear_cwf_author_version", defaultValue = ""),
    WearCwfFileName(key = "wear_cwf_filename", defaultValue = ""),
}
