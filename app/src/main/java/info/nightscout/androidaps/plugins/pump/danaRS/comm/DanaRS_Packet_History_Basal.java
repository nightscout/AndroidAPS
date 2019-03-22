package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_History_Basal extends DanaRS_Packet_History_ {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_History_Basal() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BASAL;
    }

    public DanaRS_Packet_History_Basal(long from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BASAL;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__BASAL";
    }
}
