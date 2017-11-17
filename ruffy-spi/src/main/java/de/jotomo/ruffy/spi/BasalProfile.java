package de.jotomo.ruffy.spi;

import java.util.Arrays;

public class BasalProfile {
    public final double[] hourlyRates;

    public BasalProfile() {
        this.hourlyRates = new double[24];
    }

    public BasalProfile( double[] hourlyRates) {
        this.hourlyRates = hourlyRates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BasalProfile that = (BasalProfile) o;

        return Arrays.equals(hourlyRates, that.hourlyRates);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(hourlyRates);
    }
}
