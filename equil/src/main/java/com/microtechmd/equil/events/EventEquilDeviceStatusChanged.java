package com.microtechmd.equil.events;

import androidx.annotation.NonNull;

import info.nightscout.androidaps.events.EventStatus;
import info.nightscout.androidaps.interfaces.ResourceHelper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;

public class EventEquilDeviceStatusChanged extends EventStatus {
    PumpDeviceState pumpDeviceState;

    @NonNull @Override

    public String getStatus(@NonNull ResourceHelper rh) {
        return null;
    }
}
