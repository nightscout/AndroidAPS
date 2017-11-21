package info.nightscout.androidaps.plugins.PumpCombo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import de.jotomo.ruffy.spi.BasalProfile;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpAlert;
import de.jotomo.ruffy.spi.history.PumpHistoryRequest;
import de.jotomo.ruffy.spi.history.Tdd;

class ComboPump {
    boolean initialized = false;
    volatile long lastSuccessfulCmdTime;

    public volatile String activity;
    @NonNull
    volatile PumpState state = new PumpState();
    volatile int reservoirLevel = -1;
    volatile Bolus lastBolus = null;
    @NonNull
    volatile BasalProfile basalProfile = new BasalProfile();

    // Last known history record times to skip over old ones when reading history
    long lastHistoryBolusTime = PumpHistoryRequest.FULL;
    long lastHistoryTbrTime = PumpHistoryRequest.FULL;

    // Alert and TDD histories are not stored in DB, but are read on demand and just cached  here
    List<PumpAlert> errorHistory = new ArrayList<>(0);
    List<Tdd> tddHistory = new ArrayList<>(0);
}
