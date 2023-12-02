package info.nightscout.androidaps.plugins.pump.medtronic.defs

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.medtronic.R

/**
 * Created by andy on 6/4/18.
 */
enum class BatteryType(@field:StringRes val description: Int, val lowVoltage: Double, val highVoltage: Double) {

    None(R.string.key_medtronic_pump_battery_no, 0.0, 0.0),
    Alkaline(R.string.key_medtronic_pump_battery_alkaline, 1.20, 1.47),  //
    Lithium(R.string.key_medtronic_pump_battery_lithium, 1.22, 1.64),  //
    NiZn(R.string.key_medtronic_pump_battery_nizn, 1.40, 1.70),  //
    NiMH(R.string.key_medtronic_pump_battery_nimh, 1.10, 1.40 //
    );

}