package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;

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
