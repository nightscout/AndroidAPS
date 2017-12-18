package de.jotomo.ruffy.spi;

/** State displayed on the main screen of the pump. */
public class PumpState {
    /** Time the state was captured. */
    public long timestamp;
    /** Pump time. Note that this is derived from the time displayed on the main menu and assumes
     * the date is set correctly */
    public long pumpTime;
    public String menu = null;
    public boolean suspended;

    public boolean tbrActive = false;
    /** TBR percentage. 100% means no TBR active, just the normal basal rate running. */
    public int tbrPercent = -1;
    /** The absolute rate the TBR is running, e.g. 0.80U/h. */
    public double tbrRate = -1;
    /** Remaining time of an active TBR. Note that 0:01 is te lowest displayed, the pump
     * jumps from that to TBR end, skipping 0:00(xx). */
    public int tbrRemainingDuration = -1;

    /** Warning or error code displayed if a warning or alert alert is active,
     * see {@link PumpWarningCodes}, {@link PumpErrorCodes} */
    public WarningOrErrorCode activeAlert;

    public static final int UNKNOWN = -1;
    public static final int LOW = 1;
    public static final int EMPTY = 2;
    public int batteryState = UNKNOWN;
    public int insulinState = UNKNOWN;

    public int activeBasalProfileNumber;
    /** True if use of an extended or multiwave bolus has been detected */
    public boolean unsafeUsageDetected;

    public PumpState menu(String menu) {
        this.menu = menu;
        return this;
    }

    public PumpState tbrActive(boolean tbrActive) {
        this.tbrActive = tbrActive;
        return this;
    }

    public PumpState tbrPercent(int tbrPercent) {
        this.tbrPercent = tbrPercent;
        return this;
    }

    public PumpState tbrRate(double tbrRate) {
        this.tbrRate = tbrRate;
        return this;
    }

    public PumpState tbrRemainingDuration(int tbrRemainingDuration) {
        this.tbrRemainingDuration = tbrRemainingDuration;
        return this;
    }

    public PumpState suspended(boolean suspended) {
        this.suspended = suspended;
        return this;
    }

    public PumpState batteryState(int batteryState) {
        this.batteryState = batteryState;
        return this;
    }

    public PumpState insulinState(int insulinState) {
        this.insulinState = insulinState;
        return this;
    }

    public PumpState activeBasalProfileNumber(int activeBasalProfileNumber) {
        this.activeBasalProfileNumber = activeBasalProfileNumber;
        return this;
    }

    @Override
    public String toString() {
        return "PumpState{" +
                "timestamp=" + timestamp +
                ", pumpTime=" + pumpTime +
                ", menu='" + menu + '\'' +
                ", suspended=" + suspended +
                ", tbrActive=" + tbrActive +
                ", tbrPercent=" + tbrPercent +
                ", tbrRate=" + tbrRate +
                ", tbrRemainingDuration=" + tbrRemainingDuration +
                ", activeAlert=" + activeAlert +
                ", batteryState=" + batteryState +
                ", insulinState=" + insulinState +
                ", activeBasalProfileNumber=" + activeBasalProfileNumber +
                ", unsafeUsageDetected=" + unsafeUsageDetected +
                '}';
    }
}
