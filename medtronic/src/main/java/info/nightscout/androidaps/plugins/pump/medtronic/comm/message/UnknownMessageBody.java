package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

/**
 * Created by geoff on 5/29/16.
 */
public class UnknownMessageBody extends MessageBody {

    public byte[] rxData;


    public UnknownMessageBody(byte[] data) {
        this.rxData = data;
    }


    @Override
    public int getLength() {
        return 0;
    }


    @Override
    public void init(byte[] rxData) {
    }


    public byte[] getRxData() {
        return rxData;
    }


    public void setRxData(byte[] rxData) {
        this.rxData = rxData;
    }


    @Override
    public byte[] getTxData() {
        return rxData;
    }


    public void setTxData(byte[] txData) {
        this.rxData = txData;
    }
}
