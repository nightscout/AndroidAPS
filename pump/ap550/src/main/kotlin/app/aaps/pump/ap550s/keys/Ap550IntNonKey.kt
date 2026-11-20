package app.aaps.pump.ap550s.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

/**
 * Int values the AP550 driver stores but does not show on its own preference screen.
 * The key strings are the legacy raw SharedPreferences keys the old driver already used.
 */
enum class Ap550IntNonKey(
    override val key: String,
    override val defaultValue: Int
) : IntNonPreferenceKey {

    /** Index of the last configuration-change record the pump sent and we acknowledged. */
    PumpConfigChangeRecordIndex("PumpConfigChangeRecordIndex", 0),

    /** Index of the last reset/vent record the pump sent and we acknowledged. */
    PumpResetAndVentRecordIndex("PumpResetAndVentRecordIndex", 0),

    /** DCN version of the pump firmware (legacy empty key, defaults to 0 = unset). */
    DcnVersion("", 0),
}
