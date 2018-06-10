package info.nightscout.androidaps.plugins.PumpMedtronic.comm.message;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by geoff on 5/29/16.
 * refactored into enum
 */
public enum PacketType {
    Invalid(0x00), //
    MySentry(0xa2), //
    Meter(0xa5), //
    Carelink(0xa7), //
    Sensor(0xa8) //
    ;

    private byte value = 0;
    public static Map<Byte, PacketType> mapByValue;

    static {
        mapByValue = new HashMap<>();

        for(PacketType packetType : values()) {
            mapByValue.put(packetType.value, packetType);
        }
    }


    PacketType(int value) {
        this.value = (byte) value;
    }


    public byte getValue() {
        return value;
    }


    public static PacketType getByValue(short value) {
        if (mapByValue.containsKey(value)) return mapByValue.get(value);
        else return PacketType.Invalid;
    }
}
