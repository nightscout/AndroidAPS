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

        for(int i = 0; i < 23; i++) {
            if (Math.abs(hourlyRates[i] - that.hourlyRates[i]) > 0.01) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(hourlyRates);
    }

    @Override
    public String toString() {
        double total = 0d;
        for(int i = 0; i < 23; i++) {
            total += hourlyRates[i];
        }
        return "BasalProfile{" +
                "hourlyRates=" + Arrays.toString(hourlyRates) + ", total " + total + " U" +
                '}';
    }
}
