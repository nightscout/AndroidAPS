package app.aaps.pump.medtronic.defs

import androidx.annotation.StringRes
import app.aaps.pump.medtronic.R

/**
 * Created by andy on 6/4/18.
 */
@Suppress("SpellCheckingInspection")
enum class BatteryType(val key: String, @StringRes val friendlyName: Int, val lowVoltage: Double, val highVoltage: Double) {

    None("medtronic_pump_battery_no", R.string.medtronic_pump_battery_no, 0.0, 0.0),
    Alkaline("medtronic_pump_battery_alkaline", R.string.medtronic_pump_battery_alkaline, 1.20, 1.47),
    Lithium("medtronic_pump_battery_lithium", R.string.medtronic_pump_battery_lithium, 1.22, 1.64),
    NiZn("medtronic_pump_battery_nizn", R.string.medtronic_pump_battery_nizn, 1.40, 1.70),
    NiMH("medtronic_pump_battery_nimh", R.string.medtronic_pump_battery_nimh, 1.10, 1.40);

    companion object {

        fun getByKey(someKey: String) = BatteryType.entries.firstOrNull { someKey == it.key } ?: None
    }
}