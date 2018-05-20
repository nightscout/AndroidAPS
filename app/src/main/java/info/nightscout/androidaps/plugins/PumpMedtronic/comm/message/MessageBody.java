package info.nightscout.androidaps.plugins.PumpMedtronic.comm.message;

/**
 * Created by geoff on 5/29/16.
 */
public class MessageBody {
    public int getLength() {
        return 0;
    }

    public void init(byte[] rxData) {
    }

    public byte[] getTxData() {
        return new byte[]{};
    }
}
