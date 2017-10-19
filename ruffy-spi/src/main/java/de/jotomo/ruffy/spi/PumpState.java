package de.jotomo.ruffy.spi;

import java.util.Date;

/** State displayed on the main screen of the pump. */
public class PumpState {
    public Date timestamp = new Date();
    public boolean tbrActive = false;
    /** TBR percentage. 100% means no TBR active, just the normal basal rate running. */
    public int tbrPercent = -1;
    /** The absolute rate the TBR is running, e.g. 0.80U/h. */
    public double tbrRate = -1;
    /** Remaining time of an active TBR. Note that 0:01 is te lowest displayed, the pump
     * jumps from that to TBR end, skipping 0:00(xx). */
    public int tbrRemainingDuration = -1;
    /**
     * This is the error message (if any) displayed by the pump if there is an alarm,
     * e.g. if a "TBR cancelled alarm" is active, the value will be "TBR CANCELLED".
     * Generally, an error code is also displayed, but it flashes and it might take
     * longer to read that and the pump connection gets interrupted if we're not
     * reacting quickly.
     */
    public String errorMsg;
    public boolean suspended;

    public static final int LOW = 1;
    public static final int EMPTY = 2;

    public int batteryState = - 1;
    public int insulinState = -1;

    public int activeBasalProfileNumber;

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

    public PumpState errorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
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

    public String getStateSummary() {
        if (suspended)
            return "Suspended";
        else if (errorMsg != null)
            return errorMsg;
        return "Running";
    }

    @Override
    public String toString() {
        return "PumpState{" +
                "timestamp=" + timestamp +
                ", tbrActive=" + tbrActive +
                ", tbrPercent=" + tbrPercent +
                ", tbrRate=" + tbrRate +
                ", tbrRemainingDuration=" + tbrRemainingDuration +
                ", errorMsg='" + errorMsg + '\'' +
                ", suspended=" + suspended +
                ", batteryState=" + batteryState +
                ", insulinState=" + insulinState +
                ", activeBasalProfileNumber=" + activeBasalProfileNumber +
                '}';
    }
}
