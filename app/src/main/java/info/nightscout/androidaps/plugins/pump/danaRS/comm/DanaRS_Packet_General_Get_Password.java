package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_General_Get_Password extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_General_Get_Password() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_PASSWORD;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        if (data.length < 2){
            // returned data size is too small
            failed = true;
            return;
        }
        DanaRPump pump = DanaRPump.getInstance();

        int pass = ((data[DATA_START + 1] & 0x000000FF) << 8) + (data[DATA_START + 0] & 0x000000FF);
        pass = pass ^ 3463;
        pump.rs_password = Integer.toHexString(pass);
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Pump password: " + pump.rs_password);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__GET_PASSWORD";
    }
}
