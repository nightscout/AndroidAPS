package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by andy on 6/4/18.
 */

public enum BatteryType {

    None(R.string.medtronic_pump_battery_no, 0, 0),
    Alkaline(R.string.medtronic_pump_battery_alkaline,1.20f, 1.47f), //
    Lithium(R.string.medtronic_pump_battery_lithium,1.32f, 1.58f);

    private final String description;
    public float lowVoltage;
    public float highVoltage;

    static Map<String,BatteryType> mapByDescription;

    static {
        mapByDescription = new HashMap<>();

        for (BatteryType value : values()) {
            mapByDescription.put(value.description, value);
        }
    }

    BatteryType(int resId, float lowVoltage, float highVoltage) {
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
