package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class MedtrumStringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    ActualBasalProfile("actual_basal_profile", "0"),
    ActiveAlarms("active_alarms", ""),
    SwVersion("sw_version", ""),
}
