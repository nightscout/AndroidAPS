package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

/** Only what the pump cannot tell again after an app restart is kept. What only this phone knows is not exported. */
enum class AaruyStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true,
) : StringNonPreferenceKey {

    /** The paired pump. It goes with "Replace pump settings" on import, as for other pumps. */
    MacAddress("key_aaruy_address", ""),
    PumpName("key_aaruy_name", ""),

    /** The status has no alarms: the pump sends each alarm once. */
    PumpActiveAlarm("key_aaruy_active_alarms", "", exportable = false),

    /** Empty after pairing until the first connection. */
    PumpBoundAddress("key_binded_address", "", exportable = false),
}
