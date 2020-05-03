package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.ErrorResponseType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;

public class ErrorResponse extends MessageBlock {
    private static final int MESSAGE_LENGTH = 5;

    private final ErrorResponseType errorResponseType;
    private final int nonceSearchKey;

    public ErrorResponse(byte[] encodedData) {
        if (encodedData.length < MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }
        this.encodedData = ByteUtil.substring(encodedData, 2, MESSAGE_LENGTH - 2);

        ErrorResponseType errorResponseType = null;
        try {
            errorResponseType = ErrorResponseType.fromByte(encodedData[2]);
        } catch (IllegalArgumentException ex) {
        }

        this.errorResponseType = errorResponseType;
        this.nonceSearchKey = ByteUtil.makeUnsignedShort((int) encodedData[3], (int) encodedData[4]);
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.ERROR_RESPONSE;
    }

    public ErrorResponseType getErrorResponseType() {
        return errorResponseType;
    }

    public int getNonceSearchKey() {
        return nonceSearchKey;
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "errorResponseType=" + errorResponseType +
                ", nonceSearchKey=" + nonceSearchKey +
                '}';
    }
}
