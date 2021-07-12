package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class BatteryStatus {

    private BatteryType batteryType;
    private int batteryAmount;
    private SymbolStatus symbolStatus;

    public BatteryType getBatteryType() {
        return this.batteryType;
    }

    public int getBatteryAmount() {
        return this.batteryAmount;
    }

    public SymbolStatus getSymbolStatus() {
        return this.symbolStatus;
    }

    public void setBatteryType(BatteryType batteryType) {
        this.batteryType = batteryType;
    }

    public void setBatteryAmount(int batteryAmount) {
        this.batteryAmount = batteryAmount;
    }

    public void setSymbolStatus(SymbolStatus symbolStatus) {
        this.symbolStatus = symbolStatus;
    }
}
