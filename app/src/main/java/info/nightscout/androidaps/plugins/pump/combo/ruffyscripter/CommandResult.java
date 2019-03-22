package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter;

import android.support.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.PumpHistory;

public class CommandResult {
    /** True if a condition indicating a broken pump setup/configuration is detected */
    public boolean invalidSetup;
    /** Whether the command was executed successfully. */
    public boolean success;
    /** State of the pump *after* command execution. */
    public PumpState state;
    /** History if requested by the command. */
    @Nullable
    public PumpHistory history;
    /** Basal rate profile if requested. */
    public BasalProfile basalProfile;

    /** Warnings raised on the pump that are forwarded to AAPS to be turned into AAPS
     * notifications. */
    public List<Integer> forwardedWarnings = new LinkedList<>();

    public int reservoirLevel = -1;

    public CommandResult success(boolean success) {
        this.success = success;
        return this;
    }

    public CommandResult state(PumpState state) {
        this.state = state;
        return this;
    }

    public CommandResult history(PumpHistory history) {
        this.history = history;
        return this;
    }

    public CommandResult basalProfile(BasalProfile basalProfile) {
        this.basalProfile = basalProfile;
        return this;
    }

    @Override
    public String toString() {
        return "CommandResult{" +
                "success=" + success +
                ", state=" + state +
                ", history=" + history +
                ", basalProfile=" + basalProfile +
                ", forwardedWarnings='" + forwardedWarnings + '\'' +
                '}';
    }
}
