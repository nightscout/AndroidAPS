package app.aaps.pump.omnipod.eros.driver.communication.message.response;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.FaultEventCode;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;
import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;

public class ErrorResponse extends MessageBlock {
    public static final byte ERROR_RESPONSE_CODE_BAD_NONCE = (byte) 0x14;

    private static final int MESSAGE_LENGTH = 5;

    private final byte errorResponseCode;

    @Nullable private final Integer nonceSearchKey; // only valid for BAD_NONCE
    private final FaultEventCode faultEventCode; // valid for all but BAD_NONCE

    private final PodProgressStatus podProgressStatus; // valid for all but BAD_NONCE

    public ErrorResponse(@NonNull byte[] encodedData) {
        if (encodedData.length < MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }
        this.encodedData = ByteUtil.INSTANCE.substring(encodedData, 2, MESSAGE_LENGTH - 2);

        errorResponseCode = encodedData[2];

        if (errorResponseCode == ERROR_RESPONSE_CODE_BAD_NONCE) {
            nonceSearchKey = ByteUtil.INSTANCE.makeUnsignedShort(encodedData[3], encodedData[4]);

            faultEventCode = null;
            podProgressStatus = null;
        } else {
            faultEventCode = FaultEventCode.fromByte(encodedData[3]);
            podProgressStatus = PodProgressStatus.fromByte(encodedData[4]);

            nonceSearchKey = null;
        }
    }

    @NonNull @Override
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
