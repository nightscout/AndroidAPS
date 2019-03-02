package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

import info.nightscout.androidaps.logging.L;

public class MsgSetCarbsEntry extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSetCarbsEntry() {
        SetCommand(0x0402);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public MsgSetCarbsEntry(long time, int amount) {
        this();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        AddParamByte((byte) RecordTypes.RECORD_TYPE_CARBO);
        AddParamByte((byte) (calendar.get(Calendar.YEAR) % 100));
        AddParamByte((byte) (calendar.get(Calendar.MONTH) + 1));
        AddParamByte((byte) (calendar.get(Calendar.DAY_OF_MONTH)));
        AddParamByte((byte) (calendar.get(Calendar.HOUR_OF_DAY)));
        AddParamByte((byte) (calendar.get(Calendar.MINUTE)));
        AddParamByte((byte) (calendar.get(Calendar.SECOND)));
        AddParamByte((byte) 0x43); //??
        AddParamInt(amount);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Set carb entry: " + amount + " date " + calendar.getTime().toString());
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int result = intFromBuff(bytes, 0, 1);
        if (result != 1) {
            failed = true;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set carb entry result: " + result + " FAILED!!!");
        } else {
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Set carb entry result: " + result);
        }
    }
}
