package info.nightscout.androidaps.plugins.pump.danaR.comm;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;

public class MsgStatusBolusExtended extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgStatusBolusExtended() {
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
        updateExtendedBolusInDB();

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Is extended bolus running: " + isExtendedInProgress);
            log.debug("Extended bolus min: " + extendedBolusMinutes);
            log.debug("Extended bolus amount: " + extendedBolusAmount);
            log.debug("Extended bolus so far in minutes: " + extendedBolusSoFarInMinutes);
            log.debug("Extended bolus absolute rate: " + extendedBolusAbsoluteRate);
            log.debug("Extended bolus start: " + DateUtil.dateAndTimeFullString(extendedBolusStart));
            log.debug("Extended bolus remaining minutes: " + extendedBolusRemainingMinutes);
        }
    }

    @NonNull
    private long getDateFromSecAgo(int tempBasalAgoSecs) {
        return (long) (Math.ceil(System.currentTimeMillis() / 1000d) - tempBasalAgoSecs) * 1000;
    }

    public static void updateExtendedBolusInDB() {
        TreatmentsInterface treatmentsInterface = TreatmentsPlugin.getPlugin();
        DanaRPump pump = DanaRPump.getInstance();
        long now = System.currentTimeMillis();

        ExtendedBolus extendedBolus = treatmentsInterface.getExtendedBolusFromHistory(System.currentTimeMillis());
        if (extendedBolus != null) {
            if (pump.isExtendedInProgress) {
                if (extendedBolus.absoluteRate() != pump.extendedBolusAbsoluteRate) {
                    // Close current extended
                    ExtendedBolus exStop = new ExtendedBolus(pump.extendedBolusStart - 1000);
                    exStop.source = Source.USER;
                    treatmentsInterface.addToHistoryExtendedBolus(exStop);
                    // Create new
                    ExtendedBolus newExtended = new ExtendedBolus()
                            .date(pump.extendedBolusStart)
                            .insulin(pump.extendedBolusAmount)
                            .durationInMinutes(pump.extendedBolusMinutes)
                            .source(Source.USER);
                    treatmentsInterface.addToHistoryExtendedBolus(newExtended);
                }
            } else {
                // Close curent temp basal
                ExtendedBolus exStop = new ExtendedBolus(now)
                        .source(Source.USER);
                treatmentsInterface.addToHistoryExtendedBolus(exStop);
            }
        } else {
            if (pump.isExtendedInProgress) {
                // Create new
                ExtendedBolus newExtended = new ExtendedBolus()
                        .date(pump.extendedBolusStart)
                        .insulin(pump.extendedBolusAmount)
                        .durationInMinutes(pump.extendedBolusMinutes)
                        .source(Source.USER);
                treatmentsInterface.addToHistoryExtendedBolus(newExtended);
            }
        }
    }
}
