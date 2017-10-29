package info.nightscout.androidaps.plugins.PumpCombo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import de.jotomo.ruffy.spi.BasalProfile;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpHistory;

class ComboPump {
    // TODO all non-state (==main screen) data is overriden by commands, no? put them seperately
    // at least skim over how dana does it!
    boolean initialized = false;
    // TODO actually ... this isn't about successful command execution, but whether we could connect to the pump at all
    volatile long lastSuccessfulConnection;
    volatile long lastConnectionAttempt;

    @Nullable
    volatile CommandResult lastCmdResult;
    public volatile String activity;
    @NonNull
    volatile PumpState state = new PumpState();
    volatile int reservoirLevel = -1;
    volatile Bolus lastBolus = null;
    @Nullable
    volatile BasalProfile basalProfile;
    @NonNull
    volatile PumpHistory history = new PumpHistory();
    /** Time the active TBR was set (if any) */
    long tbrSetTime;
}
