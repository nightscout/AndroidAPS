package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class DanaRS_Packet_General_Get_Password extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_General_Get_Password.class);

    public DanaRS_Packet_General_Get_Password() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_PASSWORD;
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int pass = ((data[DATA_START + 1] & 0x000000FF) << 8) + (data[DATA_START + 0] & 0x000000FF);
        pass = pass ^ 3463;
        pump.rs_password = Integer.toHexString(pass);
        if (Config.logDanaMessageDetail) {
            log.debug("Pump password: " + pump.rs_password);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__GET_PASSWORD";
    }
}
