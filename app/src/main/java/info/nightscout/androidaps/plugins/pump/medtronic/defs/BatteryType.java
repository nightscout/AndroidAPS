package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by andy on 6/4/18.
 */

public enum BatteryType {

    None(R.string.key_medtronic_pump_battery_no, 0, 0),
    Alkaline(R.string.key_medtronic_pump_battery_alkaline, 1.20d, 1.47d), //
    Lithium(R.string.key_medtronic_pump_battery_lithium, 1.22d, 1.64d), //
    NiZn(R.string.key_medtronic_pump_battery_nizn, 1.40d, 1.70d) //
    ;

    private final String description;
    public double lowVoltage;
    public double highVoltage;

    static Map<String, BatteryType> mapByDescription;

    static {
        mapByDescription = new HashMap<>();

        for (BatteryType value : values()) {
            mapByDescription.put(value.description, value);
        }
    }

    BatteryType(int resId, double lowVoltage, double highVoltage) {
        this.description = MainApp.gs(resId);
        this.lowVoltage = lowVoltage;
        this.highVoltage = highVoltage;
    }

    public static BatteryType getByDescription(String batteryTypeStr) {
        if (mapByDescription.containsKey(batteryTypeStr)) {
            return mapByDescription.get(batteryTypeStr);
        }
        return BatteryType.None;
    }
}
