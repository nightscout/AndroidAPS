package app.aaps.plugins.source.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.source.R

enum class Libre3IntentKey(
    override val key: String,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.CLICK,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    Status(
        key = "libre3_status",
        titleResId = R.string.libre3_status_title,
        summaryResId = R.string.libre3_status_summary
    ),
    Start(
        key = "libre3_start",
        titleResId = R.string.libre3_start_title,
        summaryResId = R.string.libre3_start_summary
    ),
    Warmup(
        key = "libre3_warmup",
        titleResId = R.string.libre3_warmup_title,
        summaryResId = R.string.libre3_warmup_summary
    )
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}
