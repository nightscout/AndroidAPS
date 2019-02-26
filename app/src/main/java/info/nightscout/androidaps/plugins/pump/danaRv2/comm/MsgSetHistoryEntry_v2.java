package info.nightscout.androidaps.plugins.pump.danaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;

public class MsgSetHistoryEntry_v2 extends MessageBase {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSetHistoryEntry_v2() {
        SetCommand(0xE004);
    }

    public MsgSetHistoryEntry_v2(int type, long time, int param1, int param2) {
        this();

        AddParamByte((byte) type);
        GregorianCalendar gtime = new GregorianCalendar();
        gtime.setTimeInMillis(time);
        AddParamDateTime(gtime);
        AddParamInt(param1);
        AddParamInt(param2);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Set history entry: type: " + type + " date: " + new Date(time).toString() + " param1: " + param1 + " param2: " + param2);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set history entry result: " + result + " FAILED!!!");
        } else {
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set history entry result: " + result);
        }
    }
}
