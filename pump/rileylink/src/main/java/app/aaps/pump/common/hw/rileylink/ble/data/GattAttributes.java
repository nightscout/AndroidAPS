package app.aaps.pump.common.hw.rileylink.ble.data;

import androidx.annotation.NonNull;

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

    // Generic Access
    @NonNull public static String SERVICE_GA = PREFIX + "1800" + SUFFIX;
    @NonNull public static String CHARA_GA_NAME = PREFIX + "2a00" + SUFFIX; // RileyLink RFSpy
    @NonNull public static String CHARA_GA_APPEARANCE = PREFIX + "2a01" + SUFFIX; // 0000
    public static String CHARA_GA_PPCP = PREFIX + "2a04" + SUFFIX; // 0000
    public static String CHARA_GA_CAR = PREFIX + "2aa6" + SUFFIX; // 0000

    // Generic Attribute
    @NonNull public static String SERVICE_G_ATTR = PREFIX + "1801" + SUFFIX;

    // Battery Service
    public static String SERVICE_BATTERY = PREFIX + "180f" + SUFFIX; // Battery
    public static String CHARA_BATTERY_LEVEL = PREFIX + "2a19" + SUFFIX;

    // RileyLink Radio Service
    @NonNull public static String SERVICE_RADIO = "0235733b-99c5-4197-b856-69219c2a3845";
    public static String CHARA_RADIO_DATA = "c842e849-5028-42e2-867c-016adada9155";
    public static String CHARA_RADIO_RESPONSE_COUNT = "6e6c7910-b89e-43a5-a0fe-50c5e2b81f4a";
    public static String CHARA_RADIO_TIMER_TICK = "6e6c7910-b89e-43a5-78af-50c5e2b86f7e";
    public static String CHARA_RADIO_CUSTOM_NAME = "d93b2af0-1e28-11e4-8c21-0800200c9a66";
    public static String CHARA_RADIO_VERSION = "30d99dc9-7c91-4295-a051-0a104d238cf2";
    public static String CHARA_RADIO_LED_MODE = "c6d84241-f1a7-4f9c-a25f-fce16732f14e";

    // Secure DFU Service (Orange 1.5 - 3.2)
    @NonNull public static String SERVICE_DFU = "0000fe59-0000-1000-8000-00805f9b34fb";
    public static String CHARA_BUTTONLESS_DFU = "8ec90003-f315-4f60-9fb8-838830daea50";

    // Nordic UART Service (Orange 2.1 - 3.2)
    public static String SERVICE_NORDIC_UART = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static String CHARA_NORDIC_RX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    public static String CHARA_NORDIC_TX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";


    // Orange Radio Service
    public static String SERVICE_RADIO_ORANGE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static String CHARA_NOTIFICATION_ORANGE = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

    @NonNull private static final Map<String, String> attributes;
    @NonNull private static final Map<String, String> attributesRileyLinkSpecific;

    // table of names for uuids
    static {
        attributes = new HashMap<>();

        attributes.put(SERVICE_GA, "Generic Access");
        attributes.put(CHARA_GA_NAME, "Device Name"); //
        attributes.put(CHARA_GA_APPEARANCE, "Appearance"); //
        attributes.put(CHARA_GA_PPCP, "Peripheral Preffered Connection Parameters");
        attributes.put(CHARA_GA_CAR, "Central Address Resolution");

        attributes.put(SERVICE_G_ATTR, "Generic Attribute");

        attributes.put(SERVICE_BATTERY, "Battery Service");
        attributes.put(CHARA_BATTERY_LEVEL, "Battery Level");

        attributes.put(SERVICE_RADIO, "Radio Interface Service");
        attributes.put(CHARA_RADIO_CUSTOM_NAME, "Custom Name");
        attributes.put(CHARA_RADIO_DATA, "Data");
        attributes.put(CHARA_RADIO_RESPONSE_COUNT, "Response Count");
        attributes.put(CHARA_RADIO_TIMER_TICK, "Timer Tick");
        attributes.put(CHARA_RADIO_VERSION, "Version"); // firmwareVersion
        attributes.put(CHARA_RADIO_LED_MODE, "Led Mode");

        attributes.put(SERVICE_DFU, "Secure DFU Service");
        attributes.put(CHARA_BUTTONLESS_DFU, "Buttonless DFU");

        attributes.put(SERVICE_NORDIC_UART, "Nordic UART Service");
        attributes.put(CHARA_NORDIC_RX, "RX Characteristic");
        attributes.put(CHARA_NORDIC_TX, "TX Characteristic");

        attributesRileyLinkSpecific = new HashMap<>();

        attributesRileyLinkSpecific.put(SERVICE_RADIO, "Radio Interface"); // a
        attributesRileyLinkSpecific.put(CHARA_RADIO_CUSTOM_NAME, "Custom Name");
        attributesRileyLinkSpecific.put(CHARA_RADIO_DATA, "Data");
        attributesRileyLinkSpecific.put(CHARA_RADIO_RESPONSE_COUNT, "Response Count");
        attributesRileyLinkSpecific.put(CHARA_RADIO_TIMER_TICK, "Timer Tick");
        attributesRileyLinkSpecific.put(CHARA_RADIO_VERSION, "Version"); // firmwareVersion
        attributesRileyLinkSpecific.put(CHARA_RADIO_LED_MODE, "Led Mode");

        attributesRileyLinkSpecific.put(SERVICE_RADIO_ORANGE, "Orange Radio Interface");
        attributesRileyLinkSpecific.put(CHARA_NOTIFICATION_ORANGE, "Orange Notification");
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


    public static boolean isOrange(@NonNull UUID uuid) {
        return SERVICE_RADIO_ORANGE.equals(uuid.toString());
    }

}
