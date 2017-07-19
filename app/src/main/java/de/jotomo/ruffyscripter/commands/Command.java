package de.jotomo.ruffyscripter.commands;

import java.util.List;

import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.RuffyScripter;

public interface Command {
    CommandResult execute(RuffyScripter ruffyScripter, PumpState initialPumpState);
    List<String> validateArguments();
}
