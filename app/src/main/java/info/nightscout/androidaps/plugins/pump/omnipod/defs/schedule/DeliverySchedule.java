package info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.IRawRepresentable;

public abstract class DeliverySchedule implements IRawRepresentable {

    public abstract InsulinScheduleType getType();

    public abstract int getChecksum();
}
