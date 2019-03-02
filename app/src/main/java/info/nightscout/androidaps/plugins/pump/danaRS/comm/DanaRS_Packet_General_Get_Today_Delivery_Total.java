package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_General_Get_Today_Delivery_Total extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_General_Get_Today_Delivery_Total() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_TODAY_DELIVERY_TOTAL;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        if (data.length < 8){
            failed = true;
            return;
        }
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 2;
        pump.dailyTotalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        pump.dailyTotalBasalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        pump.dailyTotalBolusUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Daily total: " + pump.dailyTotalUnits + " U");
            log.debug("Daily total bolus: " + pump.dailyTotalBolusUnits + " U");
            log.debug("Daily total basal: " + pump.dailyTotalBasalUnits + " U");
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__GET_TODAY_DELIVERY_TOTAL";
    }
}
