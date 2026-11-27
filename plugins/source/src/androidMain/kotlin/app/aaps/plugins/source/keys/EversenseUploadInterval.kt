package app.aaps.plugins.source.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.source.R

internal object EversenseUploadInterval : IntPreferenceKey {
    override val key = "eversense_cloud_upload_interval_hours"
    override val title = TextRef.AndroidRes(R.string.eversense_cloud_upload_interval)
    override val defaultValue = 1
    override val min = 0
    override val max = 24
    override val preferenceType = PreferenceType.LIST
    override val useDropdown = true
    override val dependency = EversenseBooleanKey.EversenseCloudUploadEnabled
    override val hideParentScreenIfHidden = true
    override val entries: Map<Int, TextRef> = mapOf(
        0 to TextRef.Literal("Live"),
        1 to TextRef.Literal("1 h"),
        3 to TextRef.Literal("3 h"),
        6 to TextRef.Literal("6 h"),
        12 to TextRef.Literal("12 h"),
        24 to TextRef.Literal("24 h")
    )
}
