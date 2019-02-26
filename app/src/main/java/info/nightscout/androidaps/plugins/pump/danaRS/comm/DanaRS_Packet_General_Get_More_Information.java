package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_General_Get_More_Information extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_General_Get_More_Information() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_MORE_INFORMATION;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        if (data.length < 15){
            failed = true;
            return;
        }
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 2;
        pump.iob = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.dailyTotalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 1;
        pump.isExtendedInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x01;

        dataIndex += dataSize;
        dataSize = 2;
        pump.extendedBolusRemainingMinutes = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        double remainRate = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        Date lastBolusTime = new Date(); // it doesn't provide day only hour+min, workaround: expecting today
        dataIndex += dataSize;
        dataSize = 1;
        lastBolusTime.setHours(byteArrayToInt(getBytes(data, dataIndex, dataSize)));

        dataIndex += dataSize;
        dataSize = 1;
        lastBolusTime.setMinutes(byteArrayToInt(getBytes(data, dataIndex, dataSize)));

        dataIndex += dataSize;
        dataSize = 2;
        pump.lastBolusAmount = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        // On DanaRS DailyUnits can't be more than 160
        if(pump.dailyTotalUnits > 160)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Daily total units: " + pump.dailyTotalUnits + " U");
            log.debug("Is extended in progress: " + pump.isExtendedInProgress);
            log.debug("Extended bolus remaining minutes: " + pump.extendedBolusRemainingMinutes);
            log.debug("Last bolus time: " + lastBolusTime.toLocaleString());
            log.debug("Last bolus amount: " + pump.lastBolusAmount);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__GET_MORE_INFORMATION";
    }
}
