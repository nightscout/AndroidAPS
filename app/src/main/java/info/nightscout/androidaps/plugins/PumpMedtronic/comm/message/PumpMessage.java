package info.nightscout.androidaps.plugins.PumpMedtronic.comm.message;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;

/**
 * Created by geoff on 5/29/16.
 */
public class PumpMessage implements RLMessage {
    public PacketType packetType = PacketType.Carelink;
    public byte[] address = new byte[]{0, 0, 0};
    public MessageType messageType = MessageType.Invalid;
    public MessageBody messageBody = new MessageBody();

    public PumpMessage() {
    }

    public PumpMessage(byte[] rxData) {
        init(rxData);
    }

    public void init(PacketType packetType, byte[] address, MessageType messageType, MessageBody messageBody) {
        this.packetType = packetType;
        this.address = address;
        this.messageType = messageType;
        this.messageBody = messageBody;
    }

    public void init(byte[] rxData) {
        if (rxData == null) {
            return;
        }
        if (rxData.length > 0) {
            this.packetType = PacketType.getByValue(rxData[0]);
        }
        if (rxData.length > 3) {
            this.address = ByteUtil.substring(rxData, 1, 3);
        }
        if (rxData.length > 4) {
            this.messageType = MessageType.getByValue(rxData[4]);
        }
        if (rxData.length > 5) {
            this.messageBody = MessageType.constructMessageBody(messageType, ByteUtil.substring(rxData, 5, rxData.length - 5));
        }
    }

    @Override
    public byte[] getTxData() {
        byte[] rval = ByteUtil.concat(new byte[]{(byte) packetType.getValue()}, address);
        rval = ByteUtil.concat(rval, messageType.getValue());
        rval = ByteUtil.concat(rval, messageBody.getTxData());
        return rval;
    }

    public byte[] getContents() {
        return ByteUtil.concat(new byte[]{messageType.getValue()}, messageBody.getTxData());
    }

    public byte[] getRawContent() {
        byte[] arrayOut = new byte[messageBody.getTxData().length - 1];

        System.arraycopy(messageBody.getTxData(), 1, arrayOut, 0, arrayOut.length);

        return arrayOut;
    }

    public boolean isValid() {
        if (packetType == null) return false;
        if (address == null) return false;
        if (messageType == null) return false;
        if (messageBody == null) return false;
        return true;
    }

    public MessageBody getMessageBody() {
        return messageBody;
    }

}
