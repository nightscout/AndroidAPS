package app.aaps.plugins.source.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.source.SourceStrings

enum class DexcomOnePlusIntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
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
        key = "dexcom_oneplus_status",
        title = SourceStrings.dexcom_oneplus_status_title,
        summary = SourceStrings.dexcom_oneplus_status_summary
    ),
    Start(
        key = "dexcom_oneplus_start",
        title = SourceStrings.dexcom_oneplus_start_title,
        summary = SourceStrings.dexcom_oneplus_start_summary
    ),
    Warmup(
        key = "dexcom_oneplus_warmup",
        title = SourceStrings.dexcom_oneplus_warmup_title,
        summary = SourceStrings.dexcom_oneplus_warmup_summary
    )
}
