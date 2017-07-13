package de.jotomo.ruffyscripter.commands;

import de.jotomo.ruffyscripter.RuffyScripter;

public interface Command {
    CommandResult execute(RuffyScripter ruffyScripter);

//    default String toString() {
//       return getClass().getSimpleName();
//    }
}
