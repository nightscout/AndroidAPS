package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class DanaRS_Packet_Basal_Get_Temporary_Basal_State extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Basal_Get_Temporary_Basal_State.class);

    public DanaRS_Packet_Basal_Get_Temporary_Basal_State() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__TEMPORARY_BASAL_STATE;
        if (Config.logDanaMessageDetail) {
            log.debug("Requesting temporary basal status");
        }
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        int error = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.isTempBasalInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x01;
        boolean isAPSTempBasalInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x02;

        dataIndex += dataSize;
        dataSize = 1;
        pump.tempBasalPercent = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (pump.tempBasalPercent > 200) pump.tempBasalPercent = (pump.tempBasalPercent - 200) * 10;

        dataIndex += dataSize;
        dataSize = 1;
        int durationHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        if (durationHour == 150) pump.tempBasalTotalSec = 15 * 60;
        else if (durationHour == 160) pump.tempBasalTotalSec = 30 * 60;
        else pump.tempBasalTotalSec = durationHour * 60 * 60;

        dataIndex += dataSize;
        dataSize = 2;
        int runningMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));
        int tempBasalRemainingMin = (pump.tempBasalTotalSec - runningMin * 60) / 60;
        Date tempBasalStart = pump.isTempBasalInProgress ? getDateFromTempBasalSecAgo(runningMin * 60) : new Date(0);

        if (Config.logDanaMessageDetail) {
            log.debug("Error code: " + error);
            log.debug("Is temp basal running: " + pump.isTempBasalInProgress);
            log.debug("Is APS temp basal running: " + isAPSTempBasalInProgress);
            log.debug("Current temp basal percent: " + pump.tempBasalPercent);
            log.debug("Current temp basal remaining min: " + tempBasalRemainingMin);
            log.debug("Current temp basal total sec: " + pump.tempBasalTotalSec);
            log.debug("Current temp basal start: " + tempBasalStart);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__TEMPORARY_BASAL_STATE";
    }

    @NonNull
    private Date getDateFromTempBasalSecAgo(int tempBasalAgoSecs) {
        return new Date((long) (Math.ceil(System.currentTimeMillis() / 1000d) - tempBasalAgoSecs) * 1000);
    }

}
