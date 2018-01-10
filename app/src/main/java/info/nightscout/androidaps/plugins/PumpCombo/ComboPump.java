package info.nightscout.androidaps.plugins.PumpCombo;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import de.jotomo.ruffy.spi.BasalProfile;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.history.PumpAlert;
import de.jotomo.ruffy.spi.history.Tdd;

class ComboPump {
    boolean initialized = false;
    volatile long lastSuccessfulCmdTime;

    public volatile String activity;
    @NonNull
    volatile PumpState state = new PumpState();
    @NonNull
    volatile BasalProfile basalProfile = new BasalProfile();

    // Alert and TDD histories are not stored in DB, but are read on demand and just cached  here
    List<PumpAlert> errorHistory = new ArrayList<>(0);
    List<Tdd> tddHistory = new ArrayList<>(0);
}
