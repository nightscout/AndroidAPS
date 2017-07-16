package de.jotomo.ruffyscripter.commands;

import java.util.Date;

/**
 * State representing the state of the MAIN_MENU.
 */
public class PumpState {
    public Date timestamp = new Date();
    public boolean tbrActive = false;
    public int tbrPercent = -1;
    public double tbrRate = -1;
    public int tbrRemainingDuration = -1;
    public String errorMsg;

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

    @Override
    public String toString() {
        return "PumpState{" +
                "tbrActive=" + tbrActive +
                ", tbrPercent=" + tbrPercent +
                ", tbrRate=" + tbrRate +
                ", tbrRemainingDuration=" + tbrRemainingDuration +
                ", errorMsg=" + errorMsg +
                ", timestamp=" + timestamp +
                '}';
    }
}
