package info.nightscout.androidaps.plugins.PumpCombo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.history.PumpHistory;

class ComboPump {
    // TODO actually ... this isn't about successful command execution, but whether we could connect to the pump at all
    volatile long lastSuccessfulConnection;
    volatile long lastConnectionAttempt;
    @Nullable
    volatile CommandResult lastCmdResult;

    public volatile String activity;
    @NonNull
    volatile PumpState state = new PumpState();
    volatile int reservoirLevel = -1;
    @NonNull

    volatile PumpHistory history = new PumpHistory();
}
