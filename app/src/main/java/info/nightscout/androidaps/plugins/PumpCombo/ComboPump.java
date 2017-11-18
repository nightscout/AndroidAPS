package info.nightscout.androidaps.plugins.PumpCombo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import de.jotomo.ruffy.spi.BasalProfile;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpHistory;

class ComboPump {
    boolean initialized = false;
    volatile long lastSuccessfulCmdTime;

    @Nullable
    volatile CommandResult lastCmdResult;
    public volatile String activity;
    @NonNull
    volatile PumpState state = new PumpState();
    volatile int reservoirLevel = -1;
    volatile Bolus lastBolus = null;
    @NonNull
    volatile BasalProfile basalProfile = new BasalProfile();
    @NonNull
    volatile PumpHistory history = new PumpHistory();
    /** Time the active TBR was set (if any). Needed to calculate remaining time in fragment */
    long tbrSetTime;
}
