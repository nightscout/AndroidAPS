package com.gxwtech.roundtrip2.RoundtripService.medtronic;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages.MessageBody;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages.MessageType;
import com.gxwtech.roundtrip2.util.ByteUtil;

/**
 * Created by geoff on 5/29/16.
 */
public class PumpMessage {
    public PacketType packetType = new PacketType();
    public byte[] address = new byte[] {0,0,0};
    public MessageType messageType = new MessageType(MessageType.Invalid);
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
            this.packetType = new PacketType(rxData[0]);
        }
        if (rxData.length > 3) {
            this.address = ByteUtil.substring(rxData, 1, 3);
        }
        if (rxData.length > 4) {
            this.messageType = new MessageType(rxData[4]);
        }
        if (rxData.length > 5) {
            this.messageBody = MessageType.constructMessageBody(messageType, ByteUtil.substring(rxData, 5, rxData.length - 5));
        }
    }

    public byte[] getTxData() {
        byte[] rval = ByteUtil.concat(new byte[] {(byte)packetType.value},address);
        rval = ByteUtil.concat(rval,(byte)messageType.mtype);
        rval = ByteUtil.concat(rval,messageBody.getTxData());
        return rval;
    }

    public byte[] getContents() {
        return ByteUtil.concat(new byte[] {messageType.mtype}, messageBody.getTxData());
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
