package app.aaps.plugins.source.keys
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.plugins.source.R
enum class EversenseStringKey(
    override val key: String,
    override val defaultValue: String,
    override val titleResId: Int,
    override val summaryResId: Int? = null,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : StringPreferenceKey {
    EversenseUsername(
        key = "eversense_credentials_username",
        defaultValue = "",
        titleResId = R.string.eversense_credentials_username,
        summaryResId = R.string.eversense_credentials_not_set,
        exportable = true
    ),
    EversensePassword(
        key = "eversense_credentials_password",
        defaultValue = "",
        titleResId = R.string.eversense_credentials_password,
        summaryResId = R.string.eversense_credentials_not_set,
        isPassword = true,
        exportable = true
    )
}
