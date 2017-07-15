package de.jotomo.ruffyscripter.commands;

/**
 * State represeting the state of the MAIN_MENU
 */
public class State {
    public boolean tbrActive = false;
    public int tbrPercent = -1;
    public int tbrRemainingDuration = -1;
    public boolean isErrorOrWarning = false;
    public int errorCode = -1;

    public State tbrActive(boolean tbrActive) {
        this.tbrActive = tbrActive;
        return this;
    }

    public State tbrPercent(int tbrPercent) {
        this.tbrPercent = tbrPercent;
        return this;
    }

    public State tbrRemainingDuration(int tbrRemainingDuration) {
        this.tbrRemainingDuration = tbrRemainingDuration;
        return this;
    }

    public State isErrorOrWarning(boolean isErrorOrWarning) {
        this.isErrorOrWarning = isErrorOrWarning;
        return this;
    }

    public State errorCode(int errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    @Override
    public String toString() {
        return "State{" +
                "tbrActive=" + tbrActive +
                ", tbrPercent=" + tbrPercent +
                ", tbrRemainingDuration=" + tbrRemainingDuration +
                ", isErrorOrWarning=" + isErrorOrWarning +
                ", errorCode=" + errorCode +
                '}';
    }
}
