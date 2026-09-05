package app.aaps.plugins.sync.xdrip.keys

import app.aaps.plugins.sync.SyncStrings
import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

enum class XdripIntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.CLICK,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    Info(
        key = "xdrip_info",
        title = SyncStrings.xdrip_local_broadcasts_title,
        summary = SyncStrings.xdrip_local_broadcasts_summary,
        preferenceType = PreferenceType.CLICK
    )
    ;

}
