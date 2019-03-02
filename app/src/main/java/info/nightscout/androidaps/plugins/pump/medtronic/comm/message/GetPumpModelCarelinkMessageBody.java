package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

/**
 * Created by geoff on 5/29/16.
 */
@Deprecated
public class GetPumpModelCarelinkMessageBody extends MessageBody {

    @Override
    public int getLength() {
        return 1;
    }


    @Override
    public void init(byte[] rxData) {

    }


    public byte[] getRxData() {
        return new byte[] { 0 };
    }


    public void setRxData(byte[] rxData) {

    }


    @Override
    public byte[] getTxData() {
        return new byte[] { 0 };
    }


    public void setTxData(byte[] txData) {

    }
}
