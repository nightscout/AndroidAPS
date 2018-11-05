package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.data.PumpEnactResult;

/**
 * Created by mike on 12.06.2017.
 */

public interface DanaRInterface {
    PumpEnactResult loadHistory(byte type); // for history browser
    PumpEnactResult loadEvents(); // events history to build treatments from
    PumpEnactResult setUserOptions(); // like AnyDana does
}
