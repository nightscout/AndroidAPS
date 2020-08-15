package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.plugins.pump.medtronic.R;


/**
 * Created by andy on 6/4/18.
 */

public enum BatteryType {

    None(R.string.key_medtronic_pump_battery_no, 0, 0),
    Alkaline(R.string.key_medtronic_pump_battery_alkaline, 1.20d, 1.47d), //
    Lithium(R.string.key_medtronic_pump_battery_lithium, 1.22d, 1.64d), //
    NiZn(R.string.key_medtronic_pump_battery_nizn, 1.40d, 1.70d), //
    NiMH(R.string.key_medtronic_pump_battery_nimh, 1.10d, 1.40d) //
    ;

    public final @StringRes int description;
    public final double lowVoltage;
    public final double highVoltage;


    BatteryType(int resId, double lowVoltage, double highVoltage) {
        this.description = resId;
        this.lowVoltage = lowVoltage;
        this.highVoltage = highVoltage;
    }
}
