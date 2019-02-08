package info.nightscout.androidaps.plugins.PumpInsightLocal.ids;

import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.CartridgeType;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.IDStorage;

public class CartridgeTypeIDs {

    public static final IDStorage<CartridgeType, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(CartridgeType.PREFILLED, 31);
        IDS.put(CartridgeType.SELF_FILLED, 227);
    }

}
