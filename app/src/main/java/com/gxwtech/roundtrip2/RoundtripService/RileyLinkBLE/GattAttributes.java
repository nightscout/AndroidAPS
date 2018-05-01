package com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE;

import java.util.HashMap;
import java.util.UUID;

/**
 * Created by geoff on 5/21/16.
 */
public class GattAttributes {
    // NOTE: these uuid strings must be lower case!
    private static HashMap<String, String> attributes = new HashMap();
    public static String PREFIX = "0000";
    public static String SUFFIX = "-0000-1000-8000-00805f9b34fb";
    public static String SERVICE_GAP = PREFIX + "1800" + SUFFIX;
    public static String CHARA_GAP_NAME = PREFIX + "2a00" + SUFFIX;
    public static String CHARA_GAP_NUM = PREFIX + "2a01" + SUFFIX;

    public static String SERVICE_BATTERY = PREFIX + "180f" + SUFFIX;
    public static String CHARA_BATTERY_UNK = PREFIX + "2a19" + SUFFIX;

    public static String SERVICE_RADIO = "0235733b-99c5-4197-b856-69219c2a3845";
    public static String CHARA_RADIO_DATA = "c842e849-5028-42e2-867c-016adada9155";
    public static String CHARA_RADIO_RESPONSE_COUNT = "6e6c7910-b89e-43a5-a0fe-50c5e2b81f4a";
    public static String CHARA_RADIO_TIMER_TICK = "6e6c7910-b89e-43a5-78af-50c5e2b86f7e";
    public static String CHARA_RADIO_CUSTOM_NAME = "d93b2af0-1e28-11e4-8c21-0800200c9a66";
    public static String CHARA_RADIO_VERSION = "30d99dc9-7c91-4295-a051-0a104d238cf2";

    // table of names for uuids
    static {
        attributes.put(SERVICE_GAP, "Device Information Service");
        attributes.put(SERVICE_BATTERY,"Battery Service");
        attributes.put(CHARA_RADIO_CUSTOM_NAME,"customName");
        attributes.put(CHARA_RADIO_DATA,"data");
        attributes.put(CHARA_RADIO_RESPONSE_COUNT,"responseCount");
        attributes.put(CHARA_RADIO_TIMER_TICK,"timerTick");
        attributes.put(CHARA_RADIO_VERSION,"radioVersion");
    }

    public static String lookup(UUID uuid) {
        return lookup(uuid.toString());
    }

    public static String lookup(String uuid) {
        return lookup(uuid, uuid);
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }

}
