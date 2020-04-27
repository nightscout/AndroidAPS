package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;

public class ErrorResponse extends MessageBlock {
    public static final byte ERROR_RESPONSE_CODE_BAD_NONCE = (byte) 0x14;

    private static final int MESSAGE_LENGTH = 5;

    private final byte errorResponseCode;
    private Integer nonceSearchKey; // only valid for BAD_NONCE
    private FaultEventCode faultEventCode; // valid for all but BAD_NONCE
    private PodProgressStatus podProgressStatus; // valid for all but BAD_NONCE

    public ErrorResponse(byte[] encodedData) {
        if (encodedData.length < MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }
        this.encodedData = ByteUtil.substring(encodedData, 2, MESSAGE_LENGTH - 2);

        errorResponseCode = encodedData[2];

        if (this.errorResponseCode == ERROR_RESPONSE_CODE_BAD_NONCE) {
            nonceSearchKey = ByteUtil.makeUnsignedShort(encodedData[3], encodedData[4]);
        } else {
            faultEventCode = FaultEventCode.fromByte(encodedData[3]);
            podProgressStatus = PodProgressStatus.fromByte(encodedData[4]);
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

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "errorResponseCode=" + errorResponseCode +
                ", nonceSearchKey=" + nonceSearchKey +
                ", faultEventCode=" + faultEventCode +
                ", podProgressStatus=" + podProgressStatus +
                '}';
    }
}
