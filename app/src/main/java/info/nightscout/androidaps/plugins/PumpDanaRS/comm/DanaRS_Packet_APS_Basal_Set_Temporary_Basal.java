package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

public class DanaRS_Packet_APS_Basal_Set_Temporary_Basal extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_APS_Basal_Set_Temporary_Basal.class);

    private int temporaryBasalRatio;
    private int temporaryBasalDuration;
    public int error;

    public DanaRS_Packet_APS_Basal_Set_Temporary_Basal() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL;
    }

    public DanaRS_Packet_APS_Basal_Set_Temporary_Basal(int percent) {
        this();

        //HARDCODED LIMITS
        if (percent < 0) percent = 0;
        if (percent > 500) percent = 500;

        temporaryBasalRatio = percent;
        if (percent < 100) {
            temporaryBasalDuration = 160;
            if (Config.logDanaMessageDetail)
                log.debug("APS Temp basal start percent: " + percent + " duration 30 min");
        } else {
            temporaryBasalDuration = 150;
            if (Config.logDanaMessageDetail)
                log.debug("APS Temp basal start percent: " + percent + " duration 15 min");
        }

    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[3];
        request[0] = (byte) (temporaryBasalRatio & 0xff);
        request[1] = (byte) ((temporaryBasalRatio >>> 8) & 0xff);
        request[2] = (byte) (temporaryBasalDuration & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = byteArrayToInt(getBytes(data, DATA_START, 1));
        if (result != 1) {
            failed = true;
            log.debug("Set APS temp basal start result: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Set APS temp basal start result: " + result);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__APS_SET_TEMPORARY_BASAL";
    }
}
