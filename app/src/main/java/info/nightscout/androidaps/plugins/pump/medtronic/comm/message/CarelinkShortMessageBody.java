package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

/**
 * Created by geoff on 5/29/16.
 */
// Andy: See comments in message body
public class CarelinkShortMessageBody extends MessageBody {

    byte[] body;


    public CarelinkShortMessageBody() {
        init(new byte[] { 0 });
    }


    public CarelinkShortMessageBody(byte[] data) {
        init(data);
    }


    @Override
    public int getLength() {
        return body.length;
    }


    @Override
    public void init(byte[] rxData) {
        body = rxData;
    }


    public byte[] getRxData() {
        return body;
    }


    public void setRxData(byte[] rxData) {
        init(rxData);
    }


    @Override
    public byte[] getTxData() {
        return body;
    }


    public void setTxData(byte[] txData) {
        init(txData);
    }
}
