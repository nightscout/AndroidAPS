package info.nightscout.androidaps.plugins.pump.danaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;

public class MsgSetAPSTempBasalStart_v2 extends MessageBase {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    protected final int PARAM30MIN = 160;
    protected final int PARAM15MIN = 150;

    public MsgSetAPSTempBasalStart_v2() {
        SetCommand(0xE002);
    }

    public MsgSetAPSTempBasalStart_v2(int percent) {
        this();
        setParams(percent);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message: percent: " + percent);
    }

    protected void setParams(int percent) {
        //HARDCODED LIMITS
        if (percent < 0) percent = 0;
        if (percent > 500) percent = 500;

        AddParamInt(percent);
        if (percent < 100) {
            AddParamByte((byte) PARAM30MIN);
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("APS Temp basal start percent: " + percent + " duration 30 min");
        } else {
            AddParamByte((byte) PARAM15MIN);
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("APS Temp basal start percent: " + percent + " duration 15 min");
        }
    }

    public MsgSetAPSTempBasalStart_v2(int percent, boolean fifteenMinutes, boolean thirtyMinutes) {
        this();
        setParams(percent, fifteenMinutes, thirtyMinutes);
    }

    protected void setParams(int percent, boolean fifteenMinutes, boolean thirtyMinutes) {
        //HARDCODED LIMITS
        if (percent < 0) percent = 0;
        if (percent > 500) percent = 500;

        AddParamInt(percent);
        if (thirtyMinutes && percent <= 200) { // 30 min is allowed up to 200%
            AddParamByte((byte) PARAM30MIN);
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("APS Temp basal start percent: " + percent + " duration 30 min");
        } else {
            AddParamByte((byte) PARAM15MIN);
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("APS Temp basal start percent: " + percent + " duration 15 min");
        }
    }

    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set APS temp basal start result: " + result + " FAILED!!!");
        } else {
            failed = false;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set APS temp basal start result: " + result);
        }
    }


}
