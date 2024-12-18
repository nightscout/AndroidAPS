package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import org.joda.time.DateTime;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;

public class SetupPodCommand extends MessageBlock {

    private static final byte PACKET_TIMEOUT_LIMIT = 0x04;

    private final int lot;
    private final int tid;
    private final DateTime date;
    private final int address;

    public SetupPodCommand(int address, DateTime date, int lot, int tid) {
        this.address = address;
        this.lot = lot;
        this.tid = tid;
        this.date = date;
        encode();
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.SETUP_POD;
    }

    private void encode() {
        encodedData = new byte[0];
        encodedData = ByteUtil.INSTANCE.concat(encodedData, ByteUtil.INSTANCE.getBytesFromInt(address));
        encodedData = ByteUtil.INSTANCE.concat(encodedData, new byte[]{ //
                (byte) 0x14, // unknown
                PACKET_TIMEOUT_LIMIT, //
                (byte) date.monthOfYear().get(), //
                (byte) date.dayOfMonth().get(), //
                (byte) (date.year().get() - 2000), //
                (byte) date.hourOfDay().get(), //
                (byte) date.minuteOfHour().get() //
        });
        encodedData = ByteUtil.INSTANCE.concat(encodedData, ByteUtil.INSTANCE.getBytesFromInt(lot));
        encodedData = ByteUtil.INSTANCE.concat(encodedData, ByteUtil.INSTANCE.getBytesFromInt(tid));
    }

    @NonNull @Override
    public String toString() {
        return "SetupPodCommand{" +
                "lot=" + lot +
                ", tid=" + tid +
                ", date=" + date +
                ", address=" + address +
                '}';
    }
}
