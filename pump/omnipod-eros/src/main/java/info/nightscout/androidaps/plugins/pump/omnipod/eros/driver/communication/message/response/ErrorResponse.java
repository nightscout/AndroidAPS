package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response;

import androidx.annotation.NonNull;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus;
import info.nightscout.pump.core.utils.ByteUtil;

public class ErrorResponse extends MessageBlock {
    public static final byte ERROR_RESPONSE_CODE_BAD_NONCE = (byte) 0x14;

    private static final int MESSAGE_LENGTH = 5;

    private final byte errorResponseCode;

    private final Integer nonceSearchKey; // only valid for BAD_NONCE
    private final FaultEventCode faultEventCode; // valid for all but BAD_NONCE

    private final PodProgressStatus podProgressStatus; // valid for all but BAD_NONCE

    public ErrorResponse(byte[] encodedData) {
        if (encodedData.length < MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }
        this.encodedData = ByteUtil.substring(encodedData, 2, MESSAGE_LENGTH - 2);

        errorResponseCode = encodedData[2];

        if (errorResponseCode == ERROR_RESPONSE_CODE_BAD_NONCE) {
            nonceSearchKey = ByteUtil.makeUnsignedShort(encodedData[3], encodedData[4]);

            faultEventCode = null;
            podProgressStatus = null;
        } else {
            faultEventCode = FaultEventCode.fromByte(encodedData[3]);
            podProgressStatus = PodProgressStatus.fromByte(encodedData[4]);

            nonceSearchKey = null;
        }
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.ERROR_RESPONSE;
    }

    public byte getErrorResponseCode() {
        return errorResponseCode;
    }

    public FaultEventCode getFaultEventCode() {
        return faultEventCode;
    }

    public PodProgressStatus getPodProgressStatus() {
        return podProgressStatus;
    }

    public Integer getNonceSearchKey() {
        return nonceSearchKey;
    }

    @Override @NonNull public String toString() {
        return "ErrorResponse{" +
                "errorResponseCode=" + errorResponseCode +
                ", nonceSearchKey=" + nonceSearchKey +
                ", faultEventCode=" + faultEventCode +
                ", podProgressStatus=" + podProgressStatus +
                '}';
    }
}
