package app.aaps.plugins.source.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.source.R

enum class EversenseBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    titleRes: Int,
    summaryRes: Int? = null,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {
    EversenseDiagnosticLogging("eversense_diagnostic_logging", false, R.string.eversense_diagnostic_logging, R.string.eversense_diagnostic_logging_summary, exportable = false),
    EversenseCloudUploadEnabled("eversense_cloud_upload_enabled", true, R.string.eversense_cloud_upload_enabled),
    EversenseCloudUploadToast("eversense_notif_cloud_upload_toast", false, R.string.eversense_cloud_upload_toast),
    EversenseEuropeanRegion("eversense_european_region", false, R.string.eversense_european_region, R.string.eversense_european_region_summary);

    override val preferenceType = PreferenceType.SWITCH
    override val title = TextRef.AndroidRes(titleRes)
    override val summary = summaryRes?.let { TextRef.AndroidRes(it) }
}
