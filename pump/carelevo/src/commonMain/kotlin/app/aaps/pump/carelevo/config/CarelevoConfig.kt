package app.aaps.pump.carelevo.config

// BleEnvConfig was here. The Bluetooth ids are app.aaps.pump.carelevo.ble.CarelevoGatt in the protocol
// module now, where the emulator can use them too; CarelevoGattTest pins the values.

/**
 * Insulin fill limits of the patch reservoir. Single source of truth for the wizard's amount
 * picker AND the user-facing range texts — must stay consistent with
 * `PumpType.CAREMEDI_CARELEVO.maxReservoirReading` (300) in `core:data`.
 */
class FillConfig {
    companion object {

        const val FILL_MIN_UNITS = 50
        const val FILL_MAX_UNITS = 300
        const val FILL_STEP_UNITS = 10
    }
}

// PrefEnvConfig was here. Its seven preference keys are now
// app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey, registered through the plugin's
// ownPreferences, so the preference registry can see them - an unregistered key looks like rubbish to
// a settings import, and these hold the state of a running patch. The stored key strings are
// unchanged; CarelevoStringNonKeyTest pins them, as this file's own test used to.