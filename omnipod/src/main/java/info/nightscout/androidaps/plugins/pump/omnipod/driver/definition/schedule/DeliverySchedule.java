package info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.IRawRepresentable;

public abstract class DeliverySchedule implements IRawRepresentable {

    public abstract InsulinScheduleType getType();

    public abstract int getChecksum();
}
