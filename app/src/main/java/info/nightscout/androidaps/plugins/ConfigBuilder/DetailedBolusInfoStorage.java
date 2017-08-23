package info.nightscout.androidaps.plugins.ConfigBuilder;

import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.data.DetailedBolusInfo;

/**
 * Created by mike on 08.08.2017.
 */

public class DetailedBolusInfoStorage {
    private static Logger log = LoggerFactory.getLogger(DetailedBolusInfoStorage.class);
    private static List<DetailedBolusInfo> store = new ArrayList<>();

    public static void add(DetailedBolusInfo detailedBolusInfo) {
        log.debug("Bolus info stored: " + new Date(detailedBolusInfo.date).toLocaleString());
        store.add(detailedBolusInfo);
    }

    @Nullable
    public static DetailedBolusInfo findDetailedBolusInfo(long bolustime) {
        DetailedBolusInfo found = null;
        for (int i = 0; i < store.size(); i++) {
            long infoTime = store.get(i).date;
            log.debug("Existing info: " + new Date(infoTime).toLocaleString());
            if (bolustime > infoTime - 60 * 1000 && bolustime < infoTime + 60 * 1000) {
                found = store.get(i);
                break;
            }
        }
        return found;
    }

    public static void remove(long bolustime) {
        for (int i = 0; i < store.size(); i++) {
            long infoTime = store.get(i).date;
            if (bolustime > infoTime - 60 * 1000 && bolustime < infoTime + 60 * 1000) {
                log.debug("Removing info: " + new Date(infoTime).toLocaleString());
                store.remove(i);
                break;
            }
        }
    }
}
