package app.aaps.plugins.constraints.versionChecker.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

@Suppress("SpellCheckingInspection")
enum class VersionCheckerLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LastWarningTimestamp("last_versionchecker_plugin_warning_timestamp", 0L),
    LastSuccessfulVersionCheck("last_successful_version_check_timestamp", 0L),
    LastExpiredWarning("last_expired_version_checker_warning", 0L),
    LastVersionCheckWarning("last_versionchecker_warning", 0L),
}