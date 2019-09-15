package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.utils.DateUtil;

public class MsgSettingPumpTime extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSettingPumpTime() {
        SetCommand(0x320A);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        long time =
                new Date(
                        100 + intFromBuff(bytes, 5, 1),
                        intFromBuff(bytes, 4, 1) - 1,
                        intFromBuff(bytes, 3, 1),
                        intFromBuff(bytes, 2, 1),
                        intFromBuff(bytes, 1, 1),
                        intFromBuff(bytes, 0, 1)
                ).getTime();

        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Pump time: " + DateUtil.dateAndTimeFullString(time) + " Phone time: " + new Date());

        DanaRPump.getInstance().pumpTime = time;
    }

    @Override
    public void handleMessageNotReceived() {
        DanaRPump.getInstance().pumpTime = 0;
    }

}
