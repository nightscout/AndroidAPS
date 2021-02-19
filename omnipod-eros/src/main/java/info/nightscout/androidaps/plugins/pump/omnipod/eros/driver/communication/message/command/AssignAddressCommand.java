package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.MessageBlockType;

public class AssignAddressCommand extends MessageBlock {
    private final int address;

    public AssignAddressCommand(int address) {
        this.address = address;
        encodedData = ByteBuffer.allocate(4).putInt(this.address).array();
    }

    public int getAddress() {
        return address;
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.ASSIGN_ADDRESS;
    }

    @NonNull @Override
    public String toString() {
        return "AssignAddressCommand{" +
                "address=" + address +
                '}';
    }
}
