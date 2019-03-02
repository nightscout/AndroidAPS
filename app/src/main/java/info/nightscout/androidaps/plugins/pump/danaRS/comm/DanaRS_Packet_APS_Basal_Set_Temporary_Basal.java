package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_APS_Basal_Set_Temporary_Basal extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    int temporaryBasalRatio;
    int temporaryBasalDuration;
    public int error;

    final int PARAM30MIN = 160;
    final int PARAM15MIN = 150;

    DanaRS_Packet_APS_Basal_Set_Temporary_Basal() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL;
    }

    public DanaRS_Packet_APS_Basal_Set_Temporary_Basal(int percent) {
        this();
        setParams(percent);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message: percent: " + percent);
    }

    protected void setParams(int percent) {
        //HARDCODED LIMITS
        if (percent < 0) percent = 0;
        if (percent > 500) percent = 500;

        temporaryBasalRatio = percent;
        if (percent < 100) {
            temporaryBasalDuration = PARAM30MIN;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("APS Temp basal start percent: " + percent + " duration 30 min");
        } else {
            temporaryBasalDuration = PARAM15MIN;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("APS Temp basal start percent: " + percent + " duration 15 min");
        }
    }

    public DanaRS_Packet_APS_Basal_Set_Temporary_Basal(int percent, boolean fifteenMinutes, boolean thirtyMinutes) {
        this();
        setParams(percent, fifteenMinutes, thirtyMinutes);
    }

    protected void setParams(int percent, boolean fifteenMinutes, boolean thirtyMinutes) {
        //HARDCODED LIMITS
        if (percent < 0) percent = 0;
        if (percent > 500) percent = 500;

        temporaryBasalRatio = percent;
        if (thirtyMinutes && percent <= 200) { // 30 min is allowed up to 200%
            temporaryBasalDuration = PARAM30MIN;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("APS Temp basal start percent: " + percent + " duration 30 min");
        } else {
            temporaryBasalDuration = PARAM15MIN;
            if (L.isEnabled(L.PUMPCOMM))
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
        if (result != 0) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set APS temp basal start result: " + result + " FAILED!!!");
        } else {
            failed = false;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set APS temp basal start result: " + result);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__APS_SET_TEMPORARY_BASAL";
    }
}
