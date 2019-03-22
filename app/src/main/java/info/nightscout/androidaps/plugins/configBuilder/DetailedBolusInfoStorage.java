package info.nightscout.androidaps.plugins.configBuilder;

import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.logging.L;

/**
 * Created by mike on 08.08.2017.
 */

public class DetailedBolusInfoStorage {
    private static Logger log = LoggerFactory.getLogger(L.PUMP);
    private static List<DetailedBolusInfo> store = new ArrayList<>();

    public static synchronized void add(DetailedBolusInfo detailedBolusInfo) {
        log.debug("Stored bolus info: " + detailedBolusInfo);
        store.add(detailedBolusInfo);
    }

    @Nullable
    public static synchronized DetailedBolusInfo findDetailedBolusInfo(long bolustime) {
        DetailedBolusInfo found = null;
        for (int i = 0; i < store.size(); i++) {
            long infoTime = store.get(i).date;
            if (L.isEnabled(L.PUMP))
                log.debug("Existing bolus info: " + store.get(i));
            if (bolustime > infoTime - 60 * 1000 && bolustime < infoTime + 60 * 1000) {
                found = store.get(i);
                if (L.isEnabled(L.PUMP))
                    log.debug("Using & removing bolus info: " + store.get(i));
                store.remove(i);
                break;
            }
        }
        return found;
    }
}
