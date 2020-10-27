package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

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


    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());

        sb.append(" [txData=");
        sb.append(ByteUtil.shortHexString(getTxData()));
        sb.append("]");

        return sb.toString();
    }

}
