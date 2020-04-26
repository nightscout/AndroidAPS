package info.nightscout.androidaps.plugins.pump.danaRv2.comm;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;

public class MsgStatusBolusExtended_v2 extends MessageBase {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgStatusBolusExtended_v2() {
        SetCommand(0x0207);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        boolean isExtendedInProgress = intFromBuff(bytes, 0, 1) == 1;
        int extendedBolusHalfHours = intFromBuff(bytes, 1, 1);
        int extendedBolusMinutes = extendedBolusHalfHours * 30;

        double extendedBolusAmount = intFromBuff(bytes, 2, 2) / 100d;
        int extendedBolusSoFarInSecs = intFromBuff(bytes, 4, 3);
// This is available only on korean, but not needed now
//        int extendedBolusDeliveryPulse = intFromBuff(bytes, 7, 2);
//        int isEasyUIUserSleep = intFromBuff(bytes, 9, 1);

        int extendedBolusSoFarInMinutes = extendedBolusSoFarInSecs / 60;
        double extendedBolusAbsoluteRate = isExtendedInProgress ? extendedBolusAmount / extendedBolusMinutes * 60 : 0d;
        long extendedBolusStart = isExtendedInProgress ? getDateFromSecAgo(extendedBolusSoFarInSecs) : 0;
        int extendedBolusRemainingMinutes = extendedBolusMinutes - extendedBolusSoFarInMinutes;

        DanaRPump pump = DanaRPump.getInstance();
        pump.isExtendedInProgress = isExtendedInProgress;
        pump.extendedBolusMinutes = extendedBolusMinutes;
        pump.extendedBolusAmount = extendedBolusAmount;
        pump.extendedBolusSoFarInMinutes = extendedBolusSoFarInMinutes;
        pump.extendedBolusAbsoluteRate = extendedBolusAbsoluteRate;
        pump.extendedBolusStart = extendedBolusStart;
        pump.extendedBolusRemainingMinutes = extendedBolusRemainingMinutes;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Is extended bolus running: " + isExtendedInProgress);
            log.debug("Extended bolus min: " + extendedBolusMinutes);
            log.debug("Extended bolus amount: " + extendedBolusAmount);
            log.debug("Extended bolus so far in minutes: " + extendedBolusSoFarInMinutes);
            log.debug("Extended bolus absolute rate: " + extendedBolusAbsoluteRate);
            log.debug("Extended bolus start: " + extendedBolusStart);
            log.debug("Extended bolus remaining minutes: " + extendedBolusRemainingMinutes);
        }
    }

    @NonNull
    private long getDateFromSecAgo(int tempBasalAgoSecs) {
        return (long) (Math.ceil(System.currentTimeMillis() / 1000d) - tempBasalAgoSecs) * 1000;
    }

}
