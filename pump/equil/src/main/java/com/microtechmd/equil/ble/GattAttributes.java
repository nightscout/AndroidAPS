package com.microtechmd.equil.ble;

import java.util.UUID;

public class GattAttributes {
    public static String SERVICE_RADIO = "0000f000-0000-1000-8000-00805f9b34fb";
    public static final String NRF_UART_NOTIFY = "0000f001-0000-1000-8000-00805f9b34fb";//
    public static final String NRF_UART_WIRTE = "0000f001-0000-1000-8000-00805f9b34fb";
    public final static UUID mCharacteristicConfigDescriptor = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static boolean isEquil(String uuid) {
        return false;
    }


    public static String lookup(UUID uuid) {
        return lookup(uuid.toString());
    }


    public static String lookup(String uuid) {
        return lookup(uuid, uuid);
    }


    public static String lookup(String uuid, String defaultName) {
        return uuid;
    }

}
