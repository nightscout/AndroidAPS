package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class DanaRS_Packet_General_Initial_Screen_Information extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Get_Step_Bolus_Information.class);

    public DanaRS_Packet_General_Initial_Screen_Information() {
        super();
        type = BleCommandUtil.DANAR_PACKET__TYPE_RESPONSE;
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION;
    }

    @Override
    public void handleMessage(byte[] data) {
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

        if (Config.logDanaMessageDetail) {
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
