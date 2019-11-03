package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by geoff on 6/2/16.
 */
public class CarelinkLongMessageBody extends MessageBody {

    public static final int LONG_MESSAGE_BODY_LENGTH = 65;
    protected byte[] data;


    public CarelinkLongMessageBody() {
        init(new byte[0]);
    }


    public CarelinkLongMessageBody(byte[] payload) {
        init(payload);
    }


    public CarelinkLongMessageBody(List<Byte> payload) {
        init(MedtronicUtil.createByteArray(payload));
    }


    @Override
    public void init(byte[] rxData) {

        if (rxData != null && rxData.length == LONG_MESSAGE_BODY_LENGTH) {
            data = rxData;
        } else {
            data = new byte[LONG_MESSAGE_BODY_LENGTH];
            if (rxData != null) {
                int size = rxData.length < LONG_MESSAGE_BODY_LENGTH ? rxData.length : LONG_MESSAGE_BODY_LENGTH;
                for (int i = 0; i < size; i++) {
                    data[i] = rxData[i];
                }
            }
        }
    }


    @Override
    public int getLength() {
        return LONG_MESSAGE_BODY_LENGTH;
    }


    @Override
    public byte[] getTxData() {
        return data;
    }

}
