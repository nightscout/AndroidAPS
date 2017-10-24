package de.jotomo.ruffyscripter.commands;

import de.jotomo.ruffyscripter.RuffyScripter;

public abstract class BaseCommand implements Command {
    // RS will inject itself here
    protected RuffyScripter scripter;

    @Override
    public void setScripter(RuffyScripter scripter) {
        this.scripter = scripter;
    }

    @Override
    public boolean needsRunMode() {
        return false;
    }
}
