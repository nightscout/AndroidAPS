package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_General_Set_History_Upload_Mode extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_General_Set_History_Upload_Mode.class);

    private int mode;

    public DanaRS_Packet_General_Set_History_Upload_Mode() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE;
    }

    public DanaRS_Packet_General_Set_History_Upload_Mode(int mode) {
        this();
        this.mode = mode;
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[1];
        request[0] = (byte) (mode & 0xff);
        return request;
    }


    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        int status = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (Config.logDanaMessageDetail) {
            log.debug("Result: " + status);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__SET_HISTORY_UPLOAD_MODE";
    }
}
