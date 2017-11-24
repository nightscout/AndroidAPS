package de.jotomo.ruffyscripter.commands;

import org.junit.Test;

import de.jotomo.ruffy.spi.BasalProfile;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class SetBasalProfileCommandTest {
    private SetBasalProfileCommand setBasalProfileCommand = new SetBasalProfileCommand(new BasalProfile());

    @Test
    public void belowOneToAboveOne() {
        assertThat(
                // 0.85 -> 1.00 = 15 + 1.00 -> 1.25 = 5 == 20
                setBasalProfileCommand.calculateRequiredSteps(0.85, 1.25),
                is(equalTo(20L)));
    }

    @Test
    public void aboveOneToBelowOne() {
        assertThat(
                // 2.85 -> 1.00 = 37 + 1.00 -> 0.25 = 75 == -112
                setBasalProfileCommand.calculateRequiredSteps(2.85, 0.25),
                is(equalTo(-112L)));
    }

    @Test
    public void belowOneToBelowOne() {
        assertThat(
                setBasalProfileCommand.calculateRequiredSteps(0.85, 0.25),
                is(equalTo(-60L)));
    }

    @Test
    public void aboveOneToAboveOne() {
        assertThat(
                setBasalProfileCommand.calculateRequiredSteps(2.85, 3.25),
                is(equalTo(8L)));
    }

    @Test
    public void greaterOrEqualIssuesAroundOne() {
        assertThat(
                setBasalProfileCommand.calculateRequiredSteps(0.99, 1.00),
                is(equalTo(1L)));
        assertThat(
                setBasalProfileCommand.calculateRequiredSteps(1.00, 1.05),
                is(equalTo(1L)));
        assertThat(
                setBasalProfileCommand.calculateRequiredSteps(1.10, 1.00),
                is(equalTo(-2L)));
        assertThat(
                setBasalProfileCommand.calculateRequiredSteps(1.00, 1.10),
                is(equalTo(2L)));
        assertThat(
                setBasalProfileCommand.calculateRequiredSteps(1.10, 0.98),
                is(equalTo(-4L)));
    }
}