package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodInfoType;

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

    @Override @NonNull
    public String toString() {
        return "GetStatusCommand{" +
                "podInfoType=" + podInfoType +
                '}';
    }
}
