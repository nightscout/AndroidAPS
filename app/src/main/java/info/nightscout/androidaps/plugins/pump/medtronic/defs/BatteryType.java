package info.nightscout.androidaps.plugins.pump.medtronic.defs;

/**
 * Created by andy on 6/4/18.
 */

public enum BatteryType {

    Alkaline(1.20f, 1.47f), //
    Lithium(1.32f, 1.58f);

    public float lowVoltage;
    public float highVoltage;


    BatteryType(float lowVoltage, float highVoltage) {
        this.lowVoltage = lowVoltage;
        this.highVoltage = highVoltage;
    }
}
