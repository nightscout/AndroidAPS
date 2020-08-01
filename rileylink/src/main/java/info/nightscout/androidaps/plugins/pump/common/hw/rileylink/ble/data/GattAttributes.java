package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by geoff on 5/21/16.
 */
public class GattAttributes {

    // NOTE: these uuid strings must be lower case!

    public static String PREFIX = "0000";
    public static String SUFFIX = "-0000-1000-8000-00805f9b34fb";
    public static String SERVICE_GAP = PREFIX + "1800" + SUFFIX;
    public static String CHARA_GAP_NAME = PREFIX + "2a00" + SUFFIX; // RileyLink RFSpy
    public static String CHARA_GAP_NUM = PREFIX + "2a01" + SUFFIX; // 0000
    public static String CHARA_GAP_UNK = PREFIX + "2a01" + SUFFIX; // a

    public static String SERVICE_BATTERY = PREFIX + "180f" + SUFFIX; // Battery
    public static String CHARA_BATTERY_UNK = PREFIX + "2a19" + SUFFIX;

    // RileyLink Radio Service
    public static String SERVICE_RADIO = "0235733b-99c5-4197-b856-69219c2a3845";
    public static String CHARA_RADIO_DATA = "c842e849-5028-42e2-867c-016adada9155";
    public static String CHARA_RADIO_RESPONSE_COUNT = "6e6c7910-b89e-43a5-a0fe-50c5e2b81f4a";
    public static String CHARA_RADIO_TIMER_TICK = "6e6c7910-b89e-43a5-78af-50c5e2b86f7e";
    public static String CHARA_RADIO_CUSTOM_NAME = "d93b2af0-1e28-11e4-8c21-0800200c9a66";
    public static String CHARA_RADIO_VERSION = "30d99dc9-7c91-4295-a051-0a104d238cf2";
    public static String CHARA_RADIO_LED_MODE = "c6d84241-f1a7-4f9c-a25f-fce16732f14e";

    private static Map<String, String> attributes;
    private static Map<String, String> attributesRileyLinkSpecific;

    // table of names for uuids
    static {
        attributes = new HashMap<>();

        attributes.put(SERVICE_GAP, "Device Information Service");
        attributes.put(CHARA_GAP_NAME, "Name"); //
        attributes.put(CHARA_GAP_NUM, "Number"); //

        attributes.put(SERVICE_BATTERY, "Battery Service");

        attributes.put(SERVICE_RADIO, "Radio Interface"); // a
        attributes.put(CHARA_RADIO_CUSTOM_NAME, "Custom Name");
        attributes.put(CHARA_RADIO_DATA, "Data");
        attributes.put(CHARA_RADIO_RESPONSE_COUNT, "Response Count");
        attributes.put(CHARA_RADIO_TIMER_TICK, "Timer Tick");
        attributes.put(CHARA_RADIO_VERSION, "Version"); // firmwareVersion
        attributes.put(CHARA_RADIO_LED_MODE, "Led Mode");

        attributesRileyLinkSpecific = new HashMap<>();

        attributesRileyLinkSpecific.put(SERVICE_RADIO, "Radio Interface"); // a
        attributesRileyLinkSpecific.put(CHARA_RADIO_CUSTOM_NAME, "Custom Name");
        attributesRileyLinkSpecific.put(CHARA_RADIO_DATA, "Data");
        attributesRileyLinkSpecific.put(CHARA_RADIO_RESPONSE_COUNT, "Response Count");
        attributesRileyLinkSpecific.put(CHARA_RADIO_TIMER_TICK, "Timer Tick");
        attributesRileyLinkSpecific.put(CHARA_RADIO_VERSION, "Version"); // firmwareVersion
        attributesRileyLinkSpecific.put(CHARA_RADIO_LED_MODE, "Led Mode");
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


    // we check for specific UUID (Radio ones, because thoose seem to be unique
    public static boolean isRileyLink(UUID uuid) {
        return attributesRileyLinkSpecific.containsKey(uuid.toString());
    }

}
