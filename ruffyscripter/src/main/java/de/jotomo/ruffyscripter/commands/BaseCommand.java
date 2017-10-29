package de.jotomo.ruffyscripter.commands;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffyscripter.RuffyScripter;

public abstract class BaseCommand implements Command {
    // RS will inject itself here
    protected RuffyScripter scripter;

    protected CommandResult result = new CommandResult();

    @Override
    public void setScripter(RuffyScripter scripter) {
        this.scripter = scripter;
    }

    @Override
    public boolean needsRunMode() {
        return false;
    }

    // TODO i18n; can we work with error codes instead of messages? Like W07? that way we're language agnostic
    // error message ist still needed to cancel TBR though, let next-gen ruffy take care of that?

    /**
     * An alarm (or null) caused by a disconnect we can safely confirm on reconnect,
     * knowing it's not severe as it was caused by this command.
     */
    @Override
    public String getReconnectAlarm() {
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
