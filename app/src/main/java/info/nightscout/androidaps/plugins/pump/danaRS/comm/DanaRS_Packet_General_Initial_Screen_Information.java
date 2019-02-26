package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_General_Initial_Screen_Information extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_General_Initial_Screen_Information() {
        super();
        type = BleCommandUtil.DANAR_PACKET__TYPE_RESPONSE;
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        if (data.length < 17) {
            failed = true;
            return;
        }
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        int status = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        pump.pumpSuspended = (status & 0x01) == 0x01;
        pump.isTempBasalInProgress = (status & 0x10) == 0x10;
        pump.isExtendedInProgress = (status & 0x04) == 0x04;
        pump.isDualBolusInProgress = (status & 0x08) == 0x08;

        dataIndex += dataSize;
        dataSize = 2;
        pump.dailyTotalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        pump.maxDailyTotalUnits = (int) (byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d);

        dataIndex += dataSize;
        dataSize = 2;
        pump.reservoirRemainingUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        pump.currentBasal = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 1;
        pump.tempBasalPercent = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.batteryRemaining = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 2;
        pump.extendedBolusAbsoluteRate = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        dataIndex += dataSize;
        dataSize = 2;
        pump.iob = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Pump suspended: " + pump.pumpSuspended);
            log.debug("Temp basal in progress: " + pump.isTempBasalInProgress);
            log.debug("Extended in progress: " + pump.isExtendedInProgress);
            log.debug("Dual in progress: " + pump.isDualBolusInProgress);
            log.debug("Daily units: " + pump.dailyTotalUnits);
            log.debug("Max daily units: " + pump.maxDailyTotalUnits);
            log.debug("Reservoir remaining units: " + pump.reservoirRemainingUnits);
            log.debug("Battery: " + pump.batteryRemaining);
            log.debug("Current basal: " + pump.currentBasal);
            log.debug("Temp basal percent: " + pump.tempBasalPercent);
            log.debug("Extended absolute rate: " + pump.extendedBolusAbsoluteRate);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__INITIAL_SCREEN_INFORMATION";
    }
}
