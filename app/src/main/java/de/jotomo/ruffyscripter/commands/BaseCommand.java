package de.jotomo.ruffyscripter.commands;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffyscripter.RuffyScripter;

public abstract class BaseCommand implements Command {
    // RS will inject itself here
    protected RuffyScripter scripter;

    protected CommandResult result;

    public BaseCommand() {
        result = new CommandResult();
    }

    @Override
    public void setScripter(RuffyScripter scripter) {
        this.scripter = scripter;
    }

    @Override
    public boolean needsRunMode() {
        return true;
    }

    /**
     * A warning id (or null) caused by a disconnect we can safely confirm on reconnect,
     * knowing it's not severe as it was caused by this command.
     * @see de.jotomo.ruffy.spi.PumpWarningCodes
     */
    @Override
    public Integer getReconnectWarningId() {
        return null;
    }

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public CommandResult getResult() {
        return result;
    }
}
