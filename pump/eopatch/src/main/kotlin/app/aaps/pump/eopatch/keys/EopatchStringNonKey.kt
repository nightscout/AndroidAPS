package app.aaps.pump.eopatch.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class EopatchStringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    Alarms("eopatch_alarms", ""),
    BolusCurrent("eopatch_bolus_current", ""),
    PatchState("eopatch_patch_state", ""),
    PatchConfig("eopatch_patch_config", ""),
    NormalBasal("eopatch_normal_basal", ""),
    TempBasal("eopatch_temp_basal", ""),
}
