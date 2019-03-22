package info.nightscout.androidaps.plugins.constraints.dstHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.utils.T;

/**
 * Created by Rumen on 31.10.2018.
 */
public class DstHelperPlugin extends PluginBase implements ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(L.CONSTRAINTS);
    private int minutesToChange = 0;

    static DstHelperPlugin plugin = null;

    public static DstHelperPlugin getPlugin() {
        if (plugin == null)
            plugin = new DstHelperPlugin();
        return plugin;
    }

    public DstHelperPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.CONSTRAINTS)
                .neverVisible(true)
                .alwaysEnabled(true)
                .showInList(false)
                .pluginName(R.string.dst_plugin_name)
        );
    }

    public int dstTest(Calendar c) throws Exception {
//        c = Calendar.getInstance(TimeZone.getDefault());
//        c = Calendar.getInstance(TimeZone.getTimeZone("Australia/Lord_Howe"));
//        c.setTimeInMillis(DateUtil.fromISODateString("2018-10-07T01:00:00Z").getTime());
        long zoneOffset = c.get(Calendar.ZONE_OFFSET);
        long d1 = c.getTimeInMillis() - zoneOffset;
        c.setTimeInMillis(d1);
        int offset1 = c.get(Calendar.DST_OFFSET);

        c.add(Calendar.DATE, 1);
        long d2 = c.getTimeInMillis();

        int diffInHours = (int) ((d1 - d2) / -T.hours(1).msecs());
        long offsetDetectedTime = 0;
        // comparing millis because change can be < 1 hour
//        log.debug("Starting from: "+startTimeString + " to "+endTimeString);
//        log.debug("start "+offset1+" end "+c.get(Calendar.DST_OFFSET));
        if (offset1 != c.get(Calendar.DST_OFFSET)) {
            //we have a time change in next 24 hours, but when exactly
//            log.debug("Daylight saving time detected between " + startTimeString + " and " + endTimeString);
//            log.debug("Diff in hours is: "+diffInHours);
            c.setTimeInMillis(d1 - zoneOffset);
            offset1 = c.get(Calendar.DST_OFFSET);
            for (int i = 0; i <= diffInHours * 4; i++) {

                if (offset1 != c.get(Calendar.DST_OFFSET)) {
                    log.debug("Detected offset in " + ((i / 4) - zoneOffset / T.hours(1).msecs()) + " hours value is " + (offset1 - c.get(Calendar.DST_OFFSET)) / T.mins(1).msecs() + " minutes");
                    offsetDetectedTime = c.getTimeInMillis() - d1;
                    break;
                }
                c.add(Calendar.MINUTE, 15);

            }
        }
        int minutesLeft = (int) ((offsetDetectedTime / T.mins(1).msecs()));
        /*log.debug("zoneoffset(minutes):"+zoneOffset/T.mins(1).msecs());
        log.debug("Start offset: "+offset1/T.mins(1).msecs());
        log.debug("End offset :" + c.get(Calendar.DST_OFFSET)/T.mins(1).msecs());
        log.debug("Now is:"+startTimeString);
        log.debug("Detected in(min): "+(offsetDetectedTime/T.mins(1).msecs()));
        log.debug("Returning value of: " + minutesLeft); */
        minutesToChange = minutesLeft;
        return minutesLeft;

    }

    //Return false if time to DST change is less than 91 and positive
    @Override
    public Constraint<Boolean> isLoopInvocationAllowed(Constraint<Boolean> value) {
        try {
            this.dstTest(Calendar.getInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (this.minutesToChange <= 90 && minutesToChange > 0 && value.value()) {
            try {
                LoopPlugin loopPlugin = LoopPlugin.getPlugin();
                if (loopPlugin.suspendedTo() == 0L) {
//                    loopPlugin.suspendTo(System.currentTimeMillis() + minutesToChange * T.mins(1).msecs());
                    warnUser(Notification.DST_LOOP_DISABLED, MainApp.gs(R.string.dst_loop_disabled_warning));
                } else
                    log.debug("Loop already suspended");

            } catch (Exception e) {
                e.printStackTrace();
            }
            value.set(false, "DST in 90 minutes or less", this);
        } else if (minutesToChange <= 24 * T.hours(1).mins() && minutesToChange > 0) {
            warnUser(Notification.DST_IN_24H, MainApp.gs(R.string.dst_in_24h_warning));
        }
        return value;
    }

    // display warning
    void warnUser(int id, String warningText) {
        Notification notification = new Notification(id, warningText, Notification.LOW);
        MainApp.bus().post(new EventNewNotification(notification));
    }

}