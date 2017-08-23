package de.jotomo.ruffyscripter.commands;

import java.util.List;

import de.jotomo.ruffyscripter.RuffyScripter;

/**
 * Interface for all commands to be executed by the pump.
 *
 * Note on cammond methods and timing: a method shall wait before and after executing
 * as necessary to not cause timing issues, so the caller can just call methods in
 * sequence, letting the methods take care of waits.
 */
public interface Command {
    CommandResult execute();
    List<String> validateArguments();
    void setScripter(RuffyScripter scripter);
}
