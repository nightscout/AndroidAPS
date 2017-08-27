package info.nightscout.androidaps.plugins.PumpCombo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Date;

import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.commands.Command;
import de.jotomo.ruffyscripter.commands.CommandResult;

class ComboPump {
    @NonNull
    volatile String stateSummary = "Initializing";
    @Nullable
    volatile Command lastCmd;
    @Nullable
    volatile CommandResult lastCmdResult;
    @NonNull
    volatile Date lastCmdTime = new Date(0);
    volatile PumpState state = new PumpState();
}
