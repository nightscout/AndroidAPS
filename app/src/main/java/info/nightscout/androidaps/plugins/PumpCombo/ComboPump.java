package info.nightscout.androidaps.plugins.PumpCombo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Date;

import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.history.PumpHistory;

class ComboPump {
    @Nullable
    volatile CommandResult lastCmdResult;
    @NonNull
    volatile PumpState state = new PumpState();
    @NonNull
    volatile PumpHistory history = new PumpHistory();
}
