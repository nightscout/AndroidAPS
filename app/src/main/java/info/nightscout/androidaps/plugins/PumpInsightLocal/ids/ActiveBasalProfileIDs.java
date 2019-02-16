package info.nightscout.androidaps.plugins.PumpInsightLocal.ids;

import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.BasalProfile;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.IDStorage;

public class ActiveBasalProfileIDs {

    public static final IDStorage<BasalProfile, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(BasalProfile.PROFILE_1, 31);
        IDS.put(BasalProfile.PROFILE_2, 227);
        IDS.put(BasalProfile.PROFILE_3, 252);
        IDS.put(BasalProfile.PROFILE_4, 805);
        IDS.put(BasalProfile.PROFILE_5, 826);
    }

}
