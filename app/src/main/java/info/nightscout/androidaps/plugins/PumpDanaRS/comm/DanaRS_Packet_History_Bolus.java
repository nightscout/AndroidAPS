package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;

public class DanaRS_Packet_History_Bolus extends DanaRS_Packet_History_ {
    private Logger log = LoggerFactory.getLogger(Constants.PUMPCOMM);

    public DanaRS_Packet_History_Bolus() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BOLUS;
    }

    public DanaRS_Packet_History_Bolus(Date from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BOLUS;
        if (Config.logPumpComm)
            log.debug("New message");
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__BOLUS";
    }
}
