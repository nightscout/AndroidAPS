package app.aaps.plugins.eversense.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.eversense.EversenseStrings

enum class EversenseBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {
    EversenseCloudUploadEnabled("eversense_cloud_upload_enabled", true, EversenseStrings.eversense_cloud_upload_enabled);

    override val preferenceType = PreferenceType.SWITCH
}
