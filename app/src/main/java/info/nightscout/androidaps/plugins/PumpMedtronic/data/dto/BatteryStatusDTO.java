package info.nightscout.androidaps.plugins.PumpMedtronic.data.dto;

import info.nightscout.androidaps.plugins.PumpMedtronic.defs.BatteryType;

/**
 * Created by andy on 6/14/18.
 */

public class BatteryStatusDTO {

    public BatteryStatusType batteryStatusType;
    public double voltage;


    public int getCalculatedPercent(BatteryType batteryType) {
        double percent = (voltage - batteryType.lowVoltage) / (batteryType.highVoltage - batteryType.lowVoltage);

        return (int) (percent * 100.0d);
    }


    public enum BatteryStatusType {
        Normal,
        Low,
        Unknown
    }

}
