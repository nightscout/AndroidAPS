package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

/**
 * Created by geoff on 6/2/16.
 */
public class CarelinkLongMessageBody extends MessageBody {

    private static final int LONG_MESSAGE_BODY_LENGTH = 65;
    protected byte[] data;


    CarelinkLongMessageBody() {
        init(new byte[0]);
    }


    public CarelinkLongMessageBody(byte[] payload) {
        init(payload);
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
