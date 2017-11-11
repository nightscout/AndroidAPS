package de.jotomo.ruffyscripter.commands;

import de.jotomo.ruffy.spi.BasalProfile;

public class ReadBasalProfileCommand extends BaseCommand {
    @Override
    public void execute() {
        // TODO
        result.basalProfile(new BasalProfile(new double[] {0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d}));
        result.success = true;
    }
}
