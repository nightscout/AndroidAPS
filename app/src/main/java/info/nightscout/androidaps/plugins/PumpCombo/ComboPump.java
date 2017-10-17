package info.nightscout.androidaps.plugins.PumpCombo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Date;

import info.nightscout.androidaps.plugins.PumpCombo.spi.PumpState;
import info.nightscout.androidaps.plugins.PumpCombo.spi.CommandResult;

class ComboPump {
    @Nullable
    volatile CommandResult lastCmdResult;
    @NonNull
    volatile Date lastCmdTime = new Date(0);
    volatile PumpState state = new PumpState();
}
