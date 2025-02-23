package app.aaps.plugins.constraints.versionChecker.keys

import app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey

enum class VersionCheckerComposedLongKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongComposedNonPreferenceKey {

    AppExpiration("app_expiration_", "%s", 0L),
}