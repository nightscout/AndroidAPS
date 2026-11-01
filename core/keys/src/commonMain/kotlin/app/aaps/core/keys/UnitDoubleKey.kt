package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey

enum class UnitDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val minMgdl: Int,
    override val maxMgdl: Int,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val defaultedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val sync: SyncSpec? = null
) : UnitDoublePreferenceKey {

    OverviewLowMark(key = "low_mark", defaultValue = 72.0, minMgdl = 25, maxMgdl = 160, title = KeysStrings.pref_title_low_mark, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    OverviewHighMark(key = "high_mark", defaultValue = 180.0, minMgdl = 90, maxMgdl = 250, title = KeysStrings.pref_title_high_mark, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsLgsThreshold(
        key = "lgsThreshold",
        defaultValue = 65.0,
        minMgdl = 60,
        maxMgdl = 100,
        title = KeysStrings.pref_title_lgs_threshold,
        summary = KeysStrings.lgs_threshold_summary,
        defaultedBySM = true,
        dependency = BooleanKey.ApsUseDynamicSensitivity,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    AlertHypoThreshold(
        key = "alert_hypo_threshold",
        defaultValue = 70.0,
        minMgdl = 50,
        maxMgdl = 100,
        title = KeysStrings.pref_title_alert_hypo_threshold,
        dependency = BooleanKey.AlertHypo
    ),
    AlertHyperThreshold(
        key = "alert_hyper_threshold",
        defaultValue = 250.0,
        minMgdl = 140,
        maxMgdl = 400,
        title = KeysStrings.pref_title_alert_hyper_threshold,
        dependency = BooleanKey.AlertHyper
    )
    // The rapid fall drop is a glucose difference, so it is the IntKey `AlertRapidFallDrop` in mg/dL:
    // this type guesses the unit from the number and gets small differences wrong.
    ;

}
