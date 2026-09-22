package app.aaps.pump.carelevo.config

class BleEnvConfig {
    companion object {

        const val BLE_CCC_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb"
        const val BLE_SERVICE_UUID = "e1b40001-ffc4-4daa-a49b-1c92f99072ab"
        const val BLE_TX_CHAR_UUID = "e1b40003-ffc4-4daa-a49b-1c92f99072ab"
        const val BLE_RX_CHAR_UUID = "e1b40002-ffc4-4daa-a49b-1c92f99072ab"
    }
}

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