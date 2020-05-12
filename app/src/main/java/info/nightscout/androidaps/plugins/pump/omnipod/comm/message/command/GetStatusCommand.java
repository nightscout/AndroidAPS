package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;

public class GetStatusCommand extends MessageBlock {
    private final PodInfoType podInfoType;

    public GetStatusCommand(PodInfoType podInfoType) {
        this.podInfoType = podInfoType;
        encode();
    }

    private void encode() {
        encodedData = new byte[]{podInfoType.getValue()};
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.GET_STATUS;
    }

    @Override
    public String toString() {
        return "GetStatusCommand{" +
                "podInfoType=" + podInfoType +
                '}';
    }
}
