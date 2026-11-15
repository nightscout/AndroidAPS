package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.medtrum.R

enum class MedtrumBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
) : BooleanPreferenceKey {

    MedtrumWarningNotification(
        key = "pump_warning_notification",
        defaultValue = true,
        titleResId = R.string.pump_warning_notification_title,
        summaryResId = R.string.pump_warning_notification_summary
    ),
    MedtrumPatchExpiration(
        key = "patch_expiration",
        defaultValue = true,
        titleResId = R.string.patch_expiration_title,
        summaryResId = R.string.patch_expiration_summary
    ),
    MedtrumScanOnConnectionErrors(
        key = "scan_on_connection_error",
        defaultValue = false,
        titleResId = R.string.scan_on_connection_error_title,
        summaryResId = R.string.scan_on_connection_error_summary
    ),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}
