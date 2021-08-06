package info.nightscout.androidaps.plugins.pump.common.sync;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

public interface PumpSyncEntriesCreator {

    long generateTempId(Object dataObject);

    PumpType model();

    String serialNumber();

}
