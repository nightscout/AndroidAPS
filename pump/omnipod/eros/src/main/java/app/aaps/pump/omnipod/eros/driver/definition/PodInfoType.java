package app.aaps.pump.omnipod.eros.driver.definition;

import androidx.annotation.NonNull;

import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfo;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoActiveAlerts;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoDataLog;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoDetailedStatus;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoFaultAndInitializationTime;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoOlderPulseLog;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoRecentPulseLog;

public enum PodInfoType {
    NORMAL((byte) 0x00),
    ACTIVE_ALERTS((byte) 0x01),
    DETAILED_STATUS((byte) 0x02),
    DATA_LOG((byte) 0x03), // Similar to types $50 & $51. Returns up to the last 60 dwords of data.
    FAULT_AND_INITIALIZATION_TIME((byte) 0x05),
    RECENT_PULSE_LOG((byte) 0x50),  // Starting at $4200
    OLDER_PULSE_LOG((byte) 0x51); // Starting at $4200 but dumps entries before the last 50

    private final byte value;

    PodInfoType(byte value) {
        this.value = value;
    }

    public static PodInfoType fromByte(byte value) {
        for (PodInfoType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown PodInfoType: " + value);
    }

    public byte getValue() {
        return value;
    }

    @NonNull public PodInfo decode(byte[] encodedData, int bodyLength) {
        switch (this) {
            case NORMAL:
                // We've never observed a PodInfoResponse with 0x00 subtype
                // Instead, the pod returns a StatusResponse
                throw new UnsupportedOperationException("Cannot decode PodInfoType.NORMAL");
            case ACTIVE_ALERTS:
                return new PodInfoActiveAlerts(encodedData);
            case DETAILED_STATUS:
                return new PodInfoDetailedStatus(encodedData);
            case DATA_LOG:
                return new PodInfoDataLog(encodedData, bodyLength);
            case FAULT_AND_INITIALIZATION_TIME:
                return new PodInfoFaultAndInitializationTime(encodedData);
            case RECENT_PULSE_LOG:
                return new PodInfoRecentPulseLog(encodedData, bodyLength);
            case OLDER_PULSE_LOG:
                return new PodInfoOlderPulseLog(encodedData);
            default:
                throw new IllegalArgumentException("Cannot decode " + this.name());
        }
    }
}