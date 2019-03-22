package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_General_Set_History_Upload_Mode extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private int mode;

    public DanaRS_Packet_General_Set_History_Upload_Mode() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE;
    }

    public DanaRS_Packet_General_Set_History_Upload_Mode(int mode) {
        this();
        this.mode = mode;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message: mode: " + mode);
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[1];
        request[0] = (byte) (mode & 0xff);
        return request;
    }


    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (result != 0)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            if (result == 0)
                log.debug("Result OK");
            else
                log.error("Result Error: " + result);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__SET_HISTORY_UPLOAD_MODE";
    }
}
