package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType;

/**
 * Created by andy on 6/14/18.
 */

public class BatteryStatusDTO {

    @Expose
    public BatteryStatusType batteryStatusType;
    @Expose
    public Double voltage;

    public boolean extendedDataReceived = false;


    public int getCalculatedPercent(BatteryType batteryType) {
        if (voltage == null || batteryType == BatteryType.None) {
            return (batteryStatusType == BatteryStatusType.Low || batteryStatusType == BatteryStatusType.Unknown) ? 18 : 70;
        }

        double percent = (voltage - batteryType.lowVoltage) / (batteryType.highVoltage - batteryType.lowVoltage);

        int percentInt = (int) (percent * 100.0d);

        if (percentInt > 100)
            percentInt = 100;

        return percentInt;
    }


    public String toString() {
        return String.format("BatteryStatusDTO [voltage=%.2f, alkaline=%d, lithium=%d]",
                voltage == null ? 0.0f : voltage,
                getCalculatedPercent(BatteryType.Alkaline),
                getCalculatedPercent(BatteryType.Lithium));
    }


    public enum BatteryStatusType {
        Normal,
        Low,
        Unknown
    }

}
