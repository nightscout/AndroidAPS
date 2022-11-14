package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodInfoType;
import info.nightscout.pump.core.utils.ByteUtil;

public class PodInfoResponse extends MessageBlock {
    private final PodInfoType subType;
    private final PodInfo podInfo;

    public PodInfoResponse(byte[] encodedData) {
        int bodyLength = ByteUtil.convertUnsignedByteToInt(encodedData[1]);

        this.encodedData = ByteUtil.substring(encodedData, 2, bodyLength);
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
