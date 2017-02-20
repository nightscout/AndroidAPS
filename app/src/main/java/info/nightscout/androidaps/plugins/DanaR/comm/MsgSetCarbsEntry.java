package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

import info.nightscout.androidaps.Config;

public class MsgSetCarbsEntry extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSetCarbsEntry.class);

    public MsgSetCarbsEntry() {
        SetCommand(0x0402);
    }

    public MsgSetCarbsEntry(Calendar time, int amount) {
        this();

        AddParamByte((byte) RecordTypes.RECORD_TYPE_CARBO);
        AddParamByte((byte) (time.get(Calendar.YEAR) % 100));
        AddParamByte((byte) (time.get(Calendar.MONTH) + 1));
        AddParamByte((byte) (time.get(Calendar.DAY_OF_MONTH)));
        AddParamByte((byte) (time.get(Calendar.HOUR_OF_DAY)));
        AddParamByte((byte) (time.get(Calendar.MINUTE)));
        AddParamByte((byte) (time.get(Calendar.SECOND)));
        AddParamByte((byte) 0x43); //??
        AddParamInt(amount);
        if (Config.logDanaMessageDetail)
            log.debug("Set carb entry: " + amount + " date " + time.getTime().toString());
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            log.debug("Set carb entry result: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Set carb entry result: " + result);
        }
    }
}
