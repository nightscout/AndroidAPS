package app.aaps.pump.omnipod.eros.driver.definition;

import org.apache.commons.lang3.NotImplementedException;

import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.ErrorResponse;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.VersionResponse;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoResponse;

public enum MessageBlockType {
    VERSION_RESPONSE(0x01),
    POD_INFO_RESPONSE(0x02),
    SETUP_POD(0x03),
    ERROR_RESPONSE(0x06),
    ASSIGN_ADDRESS(0x07),
    FAULT_CONFIG(0x08),
    GET_STATUS(0x0e),
    ACKNOWLEDGE_ALERT(0x11),
    BASAL_SCHEDULE_EXTRA(0x13),
    TEMP_BASAL_EXTRA(0x16),
    BOLUS_EXTRA(0x17),
    CONFIGURE_ALERTS(0x19),
    SET_INSULIN_SCHEDULE(0x1a),
    DEACTIVATE_POD(0x1c),
    STATUS_RESPONSE(0x1d),
    BEEP_CONFIG(0x1e),
    CANCEL_DELIVERY(0x1f);

    private final byte value;

    MessageBlockType(int value) {
        this.value = (byte) value;
    }

    public static MessageBlockType fromByte(byte value) {
        for (MessageBlockType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MessageBlockType: " + value);
    }

    public byte getValue() {
        return value;
    }

    public MessageBlock decode(byte[] encodedData) {
        switch (this) {
            case VERSION_RESPONSE:
                return new VersionResponse(encodedData);
            case ERROR_RESPONSE:
                return new ErrorResponse(encodedData);
            case POD_INFO_RESPONSE:
                return new PodInfoResponse(encodedData);
            case STATUS_RESPONSE:
                return new StatusResponse(encodedData);
            default:
                throw new NotImplementedException(this.name());
        }
    }
}
