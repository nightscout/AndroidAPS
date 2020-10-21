package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

/**
 * Created by geoff on 6/2/16.
 */
public class GetHistoryPageCarelinkMessageBody extends CarelinkLongMessageBody {

    // public boolean wasLastFrame = false;
    // public int frameNumber = 0;
    // public byte[] frame = new byte[] {};

    public GetHistoryPageCarelinkMessageBody(byte[] frameData) {
        init(frameData);
    }


    public GetHistoryPageCarelinkMessageBody(int pageNum) {
        init(pageNum);
    }


    @Override
    public int getLength() {
        return data.length;
    }


    @Override
    public void init(byte[] rxData) {
        super.init(rxData);
    }


    public void init(int pageNum) {
        byte numArgs = 1;
        super.init(new byte[] { numArgs, (byte)pageNum });
    }


    public int getFrameNumber() {
        if (data.length > 0) {
            return data[0] & 0x7f;
        }
        return 255;
    }


    public boolean wasLastFrame() {
        return (data[0] & 0x80) != 0;
    }


    public byte[] getFrameData() {
        return ByteUtil.substring(data, 1, data.length - 1);
    }

}
