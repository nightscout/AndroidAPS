package de.jotomo.ruffyscripter.commands;

import de.jotomo.ruffy.spi.BasalProfile;

public class ReadBasalProfileCommand extends BaseCommand {
    @Override
    public void execute() {
        if (1==1) throw new RuntimeException("No implemented yet");
        // TODO
        result.basalProfile(new BasalProfile(new double[] {0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d}));
        //scripter.returnToRootMenu();
        result.success = true;
    }
}
