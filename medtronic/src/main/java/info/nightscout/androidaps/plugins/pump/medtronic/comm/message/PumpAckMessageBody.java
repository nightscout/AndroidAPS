package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

/**
 * Created by geoff on 5/29/16.
 */
public class PumpAckMessageBody extends CarelinkShortMessageBody {

    public PumpAckMessageBody() {
        init(new byte[] { 0 });
    }


    public PumpAckMessageBody(byte[] bodyData) {
        init(bodyData);
    }
}
