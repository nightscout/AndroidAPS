package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm;

import java.math.BigInteger;
import java.util.UUID;

public enum CharacteristicType {
    CMD("1a7e-2441-e3ed-4464-8b7e-751e03d0dc5f"),
    DATA("1a7e-2442-e3ed-4464-8b7e-751e03d0dc5f");

    public final String value;

    CharacteristicType(String value) {
        this.value = value;
    }

    public static CharacteristicType byValue(byte value) {
        for (CharacteristicType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Characteristic Type: " + value);
    }

    public String getValue() {
        return this.value;
    }

    public UUID getUUID() {
        return new UUID(
                new BigInteger(this.value.replace("-", "").substring(0, 16), 16).longValue(),
                new BigInteger(this.value.replace("-", "").substring(16), 16).longValue()
        );
    }
}
