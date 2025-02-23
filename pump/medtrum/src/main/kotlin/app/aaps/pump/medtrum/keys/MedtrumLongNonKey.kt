package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class MedtrumLongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    SessionToken("medtrum_session_token", defaultValue = 0L),
    LastConnection("last_connection", defaultValue = 0L),
    PatchId("patch_id", defaultValue = 0L),
    PatchStartTime("patch_start_time", defaultValue = 0L),
    BolusStartTime("bolus_start_time", defaultValue = 0L),
    LastBolusTime("last_bolus_time", defaultValue = 0L),
}
