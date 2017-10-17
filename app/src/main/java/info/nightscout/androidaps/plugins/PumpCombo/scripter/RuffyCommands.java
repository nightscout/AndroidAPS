package info.nightscout.androidaps.plugins.PumpCombo.scripter;

import info.nightscout.androidaps.plugins.PumpCombo.scripter.commands.CommandResult;
import info.nightscout.androidaps.plugins.PumpCombo.scripter.commands.BolusCommand;

/**
 * Main entry point for clients, implemented by RuffyScripter.
 */
public interface RuffyCommands {
    CommandResult deliverBolus(double amount, BolusCommand.ProgressReportCallback progressReportCallback);

    void cancelBolus();

    CommandResult setTbr(int percent, int duraton);

    CommandResult cancelTbr();

    CommandResult readReservoirLevel();

    // PumpHistory.fields.*: null=don't care. empty history=we know nothing yet. filled history=this is what we know so far
    CommandResult readHistory(PumpHistory knownHistory);

    CommandResult readBasalProfile();

    CommandResult setBasalProfile(BasalProfile basalProfile);
}

