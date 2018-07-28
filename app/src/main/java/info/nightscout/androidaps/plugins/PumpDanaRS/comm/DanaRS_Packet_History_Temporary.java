package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;

public class DanaRS_Packet_History_Temporary extends DanaRS_Packet_History_ {
    private Logger log = LoggerFactory.getLogger(Constants.PUMPCOMM);

    public DanaRS_Packet_History_Temporary() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__TEMPORARY;
    }

    public DanaRS_Packet_History_Temporary(Date from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__TEMPORARY;
        if (Config.logPumpComm)
            log.debug("New message");
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__TEMPORARY";
    }
}
