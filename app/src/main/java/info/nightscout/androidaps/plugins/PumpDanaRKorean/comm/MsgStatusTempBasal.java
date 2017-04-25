package info.nightscout.androidaps.plugins.PumpDanaRKorean.comm;

import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPump;

public class MsgStatusTempBasal extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatusTempBasal.class);

    public MsgStatusTempBasal() {
        SetCommand(0x0205);
    }

    public void handleMessage(byte[] bytes) {
        boolean isTempBasalInProgress = intFromBuff(bytes, 0, 1) == 1;
        int tempBasalPercent = intFromBuff(bytes, 1, 1);
        int tempBasalTotalSec = intFromBuff(bytes, 2, 1) * 60 * 60;
        int tempBasalRunningSeconds = intFromBuff(bytes, 3, 3);
        int tempBasalRemainingMin = (tempBasalTotalSec - tempBasalRunningSeconds) / 60;
        Date tempBasalStart = isTempBasalInProgress ? getDateFromTempBasalSecAgo(tempBasalRunningSeconds) : new Date(0);

        DanaRKoreanPlugin.getDanaRPump().isTempBasalInProgress = isTempBasalInProgress;
        DanaRKoreanPlugin.getDanaRPump().tempBasalPercent = tempBasalPercent;
        DanaRKoreanPlugin.getDanaRPump().tempBasalRemainingMin = tempBasalRemainingMin;
        DanaRKoreanPlugin.getDanaRPump().tempBasalTotalSec = tempBasalTotalSec;
        DanaRKoreanPlugin.getDanaRPump().tempBasalStart = tempBasalStart;

        updateTempBasalInDB();

        if (Config.logDanaMessageDetail) {
            log.debug("Is temp basal running: " + isTempBasalInProgress);
            log.debug("Current temp basal percent: " + tempBasalPercent);
            log.debug("Current temp basal remaining min: " + tempBasalRemainingMin);
            log.debug("Current temp basal total sec: " + tempBasalTotalSec);
            log.debug("Current temp basal start: " + tempBasalStart);
        }
    }

    @NonNull
    private Date getDateFromTempBasalSecAgo(int tempBasalAgoSecs) {
        return new Date((long) (Math.ceil(new Date().getTime() / 1000d) - tempBasalAgoSecs) * 1000);
    }

    public static void updateTempBasalInDB() {
        DanaRKoreanPlugin DanaRKoreanPlugin = (DanaRKoreanPlugin) MainApp.getSpecificPlugin(DanaRKoreanPlugin.class);
        DanaRKoreanPump danaRKoreanPump = DanaRKoreanPlugin.getDanaRPump();
        Date now = new Date();

        try {

            if (DanaRKoreanPlugin.isRealTempBasalInProgress()) {
                TempBasal tempBasal = DanaRKoreanPlugin.getRealTempBasal();
                if (danaRKoreanPump.isTempBasalInProgress) {
                    if (tempBasal.percent != danaRKoreanPump.tempBasalPercent) {
                        // Close current temp basal
                        tempBasal.timeEnd = now;
                        MainApp.getDbHelper().getDaoTempBasals().update(tempBasal);
                        // Create new
                        TempBasal newTempBasal = new TempBasal();
                        newTempBasal.timeStart = now;
                        newTempBasal.percent = danaRKoreanPump.tempBasalPercent;
                        newTempBasal.isAbsolute = false;
                        newTempBasal.duration = danaRKoreanPump.tempBasalTotalSec / 60;
                        newTempBasal.isExtended = false;
                        MainApp.getDbHelper().getDaoTempBasals().create(newTempBasal);
                        MainApp.bus().post(new EventTempBasalChange());
                    }
                } else {
                    // Close current temp basal
                    tempBasal.timeEnd = now;
                    MainApp.getDbHelper().getDaoTempBasals().update(tempBasal);
                    MainApp.bus().post(new EventTempBasalChange());
                }
            } else {
                if (danaRKoreanPump.isTempBasalInProgress) {
                    // Create new
                    TempBasal newTempBasal = new TempBasal();
                    newTempBasal.timeStart = now;
                    newTempBasal.percent = danaRKoreanPump.tempBasalPercent;
                    newTempBasal.isAbsolute = false;
                    newTempBasal.duration = danaRKoreanPump.tempBasalTotalSec / 60;
                    newTempBasal.isExtended = false;
                    MainApp.getDbHelper().getDaoTempBasals().create(newTempBasal);
                    MainApp.bus().post(new EventTempBasalChange());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
