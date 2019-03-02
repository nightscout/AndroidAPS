package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

import info.nightscout.androidaps.plugins.pump.common.utils.HexDump;

/**
 * Created by geoff on 5/29/16.
 */

// Andy (4.6.2018): We probably need rewrite of this message body code. If there is no data sent, body is 00, which
// denotes,
// no parameters. If we have 3 parameters sent (1 2 3), the body would actually be length of 4, first byte being the
// length
// of the message, so the body would be 3 1 2 3. This is not done that way now.
public class MessageBody {

    public int getLength() {
        return 0;
    }


    public void init(byte[] rxData) {
    }


    public byte[] getTxData() {
        return new byte[] {};
    }


    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());

        sb.append(" [txData=");
        sb.append(HexDump.toHexStringDisplayable(getTxData()));
        sb.append("]");

        return sb.toString();
    }

}
