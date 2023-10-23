package app.aaps.pump.equil.events;

import android.content.Context;

import androidx.annotation.NonNull;

import app.aaps.core.interfaces.rx.events.EventStatus;
import info.nightscout.pump.common.defs.PumpDeviceState;


public class EventEquilDeviceStatusChanged extends EventStatus {
    PumpDeviceState pumpDeviceState;

    @NonNull @Override public String getStatus(@NonNull Context context) {
        return null;
    }
}
