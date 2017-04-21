package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

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

        DanaRPlugin.getDanaRPump().isTempBasalInProgress = isTempBasalInProgress;
        DanaRPlugin.getDanaRPump().tempBasalPercent = tempBasalPercent;
        DanaRPlugin.getDanaRPump().tempBasalRemainingMin = tempBasalRemainingMin;
        DanaRPlugin.getDanaRPump().tempBasalTotalSec = tempBasalTotalSec;
        DanaRPlugin.getDanaRPump().tempBasalStart = tempBasalStart;

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
        DanaRPlugin DanaRPlugin = (DanaRPlugin) MainApp.getSpecificPlugin(DanaRPlugin.class);
        DanaRPump danaRPump = DanaRPlugin.getDanaRPump();
        Date now = new Date();

        try {

            if (DanaRPlugin.isRealTempBasalInProgress()) {
                TempBasal tempBasal = DanaRPlugin.getRealTempBasal();
                if (danaRPump.isTempBasalInProgress) {
                    if (tempBasal.percent != danaRPump.tempBasalPercent) {
                        // Close current temp basal
                        tempBasal.timeEnd = now;
                        MainApp.getDbHelper().getDaoTempBasals().update(tempBasal);
                        // Create new
                        TempBasal newTempBasal = new TempBasal();
                        newTempBasal.timeStart = now;
                        newTempBasal.percent = danaRPump.tempBasalPercent;
                        newTempBasal.isAbsolute = false;
                        newTempBasal.duration = danaRPump.tempBasalTotalSec / 60;
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
                if (danaRPump.isTempBasalInProgress) {
                    // Create new
                    TempBasal newTempBasal = new TempBasal();
                    newTempBasal.timeStart = now;
                    newTempBasal.percent = danaRPump.tempBasalPercent;
                    newTempBasal.isAbsolute = false;
                    newTempBasal.duration = danaRPump.tempBasalTotalSec / 60;
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
