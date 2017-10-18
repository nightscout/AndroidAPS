package de.jotomo.ruffy.spi;

public class BasalProfile {
    public final int number;
    public final double[] hourlyRates;

    public BasalProfile(int number, double[] hourlyRates) {
        this.number = number;
        if (hourlyRates.length != 24)
            throw new IllegalArgumentException("Profile must have 24 hourly rates");
        this.hourlyRates = hourlyRates;
    }
}
