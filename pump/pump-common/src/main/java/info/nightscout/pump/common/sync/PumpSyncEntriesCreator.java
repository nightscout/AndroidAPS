package info.nightscout.pump.common.sync;

import info.nightscout.interfaces.pump.defs.PumpType;

public interface PumpSyncEntriesCreator {

    long generateTempId(Object dataObject);

    PumpType model();

    String serialNumber();

}
