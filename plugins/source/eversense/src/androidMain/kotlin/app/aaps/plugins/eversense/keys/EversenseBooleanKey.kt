package app.aaps.plugins.eversense.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.eversense.R

enum class EversenseBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    titleRes: Int,
    summaryRes: Int? = null,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {
    EversenseCloudUploadEnabled("eversense_cloud_upload_enabled", true, R.string.eversense_cloud_upload_enabled);

    override val preferenceType = PreferenceType.SWITCH
    override val title = TextRef.AndroidRes(titleRes)
    override val summary = summaryRes?.let { TextRef.AndroidRes(it) }
}
