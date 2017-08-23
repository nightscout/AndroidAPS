package de.jotomo.ruffyscripter.commands;

import de.jotomo.ruffyscripter.RuffyScripter;

public abstract class BaseCommand implements Command {
    // RS will inject itself here
    protected RuffyScripter scripter;
    @Override public void setScripter(RuffyScripter scripter) { this.scripter = scripter; }

    // TODO upcoming
    protected volatile boolean cancelRequested = false;
    public void requestCancellation() {
       cancelRequested = true;
    }
}
