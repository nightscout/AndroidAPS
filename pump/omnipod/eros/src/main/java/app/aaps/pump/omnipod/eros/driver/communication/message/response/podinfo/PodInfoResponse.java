package app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo;

import androidx.annotation.NonNull;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;
import app.aaps.pump.omnipod.eros.driver.definition.PodInfoType;

public class PodInfoResponse extends MessageBlock {
    private final PodInfoType subType;
    private final PodInfo podInfo;

    public PodInfoResponse(@NonNull byte[] encodedData) {
        int bodyLength = ByteUtil.INSTANCE.convertUnsignedByteToInt(encodedData[1]);

        this.encodedData = ByteUtil.INSTANCE.substring(encodedData, 2, bodyLength);
        subType = PodInfoType.fromByte(encodedData[2]);
        podInfo = subType.decode(this.encodedData, bodyLength);
    }

    public PodInfoType getSubType() {
        return subType;
    }

    public PodInfo getPodInfo() {
        return podInfo;
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.POD_INFO_RESPONSE;
    }

    @Override
    public String toString() {
        return "PodInfoResponse{" +
                "subType=" + subType.name() +
                ", podInfo=" + podInfo.toString() +
                '}';
    }
}
