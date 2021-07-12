package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;

public class ServiceIDs {

    public static final IDStorage<Service, Byte> IDS = new IDStorage<>();

    static {
        IDS.put(Service.CONNECTION, (byte) 0);
        IDS.put(Service.STATUS, (byte) 15);
        IDS.put(Service.HISTORY, (byte) 60);
        IDS.put(Service.CONFIGURATION, (byte) 85);
        IDS.put(Service.REMOTE_CONTROL, (byte) 102);
        IDS.put(Service.PARAMETER, (byte) 51);
    }
}
