package de.jotomo.ruffyscripter.commands;

import java.util.ArrayList;
import java.util.List;

import de.jotomo.ruffy.spi.BasalProfile;

public class SetBasalProfileCommand extends BaseCommand {
    private final BasalProfile basalProfile;

    public SetBasalProfileCommand(BasalProfile basalProfile) {
        this.basalProfile = basalProfile;
    }

    @Override
    public void execute() {
        // TODO
    }

    @Override
    public List<String> validateArguments() {
        ArrayList<String> violations = new ArrayList<>();
        if (basalProfile == null) {
            violations.add("No basal profile supplied");
        }

        return violations;
    }
}
