package de.jotomo.ruffyscripter;

/**
 * Created by adrian on 26/07/17.
 * <p>
 * Contains the capabilities of the current pump model.
 */

public class PumpCapabilities {
    public long maxTempPercent;

    public PumpCapabilities maxTempPercent(long maxTempPercent) {
        this.maxTempPercent = maxTempPercent;
        return this;
    }

    @Override
    public String toString() {
        return "PumpCapabilities{" +
                "maxTempPercent=" + maxTempPercent +
                '}';
    }
}
