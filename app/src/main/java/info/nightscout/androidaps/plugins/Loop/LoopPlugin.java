package info.nightscout.androidaps.plugins.Loop;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopSetLastRunGui;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopUpdateGui;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class LoopPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(LoopPlugin.class);

    private static LoopPlugin loopPlugin;

    public static LoopPlugin getPlugin() {
        if (loopPlugin == null) {
            loopPlugin = new LoopPlugin();
        }
        return loopPlugin;
    }

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private long loopSuspendedTill = 0L; // end of manual loop suspend
    private boolean isSuperBolus = false;
    private boolean isDisconnected = false;

    public class LastRun {
        public APSResult request = null;
        public APSResult constraintsProcessed = null;
        public PumpEnactResult setByPump = null;
        public String source = null;
        public Date lastAPSRun = null;
        public Date lastEnact = null;
        public Date lastOpenModeAccept;
    }

    static public LastRun lastRun = null;

    public LoopPlugin() {
        MainApp.bus().register(this);
        loopSuspendedTill = SP.getLong("loopSuspendedTill", 0L);
        isSuperBolus = SP.getBoolean("isSuperBolus", false);
        isDisconnected = SP.getBoolean("isDisconnected", false);
    }

    @Override
    public String getFragmentClass() {
        return LoopFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.LOOP;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.loop);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.loop_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        boolean pumpCapable = ConfigBuilderPlugin.getActivePump() == null || ConfigBuilderPlugin.getActivePump().getPumpDescription().isTempBasalCapable;
        return type == LOOP && fragmentEnabled && pumpCapable;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        boolean pumpCapable = ConfigBuilderPlugin.getActivePump() == null || ConfigBuilderPlugin.getActivePump().getPumpDescription().isTempBasalCapable;
        return type == LOOP && fragmentVisible && pumpCapable;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == LOOP) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == LOOP) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_closedmode;
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        invoke("EventTreatmentChange", true);
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        invoke("EventNewBG", true);
    }

    public long suspendedTo() {
        return loopSuspendedTill;
    }

    public void suspendTo(long endTime) {
        loopSuspendedTill = endTime;
        isSuperBolus = false;
        isDisconnected = false;
        SP.putLong("loopSuspendedTill", loopSuspendedTill);
        SP.putBoolean("isSuperBolus", isSuperBolus);
        SP.putBoolean("isDisconnected", isDisconnected);
    }

    public void superBolusTo(long endTime) {
        loopSuspendedTill = endTime;
        isSuperBolus = true;
        isDisconnected = false;
        SP.putLong("loopSuspendedTill", loopSuspendedTill);
        SP.putBoolean("isSuperBolus", isSuperBolus);
        SP.putBoolean("isDisconnected", isDisconnected);
    }

    public void disconnectTo(long endTime) {
        loopSuspendedTill = endTime;
        isSuperBolus = false;
        isDisconnected = true;
        SP.putLong("loopSuspendedTill", loopSuspendedTill);
        SP.putBoolean("isSuperBolus", isSuperBolus);
        SP.putBoolean("isDisconnected", isDisconnected);
    }

    public int minutesToEndOfSuspend() {
        if (loopSuspendedTill == 0)
            return 0;

        long now = System.currentTimeMillis();
        long msecDiff = loopSuspendedTill - now;

        if (loopSuspendedTill <= now) { // time exceeded
            suspendTo(0L);
            return 0;
        }

        return (int) (msecDiff / 60d / 1000d);
    }

    public boolean isSuspended() {
        if (loopSuspendedTill == 0)
            return false;

        long now = System.currentTimeMillis();

        if (loopSuspendedTill <= now) { // time exceeded
            suspendTo(0L);
            return false;
        }

        return true;
    }

    public boolean isSuperBolus() {
        if (loopSuspendedTill == 0)
            return false;

        long now = System.currentTimeMillis();

        if (loopSuspendedTill <= now) { // time exceeded
            suspendTo(0L);
            return false;
        }

        return isSuperBolus;
    }

    public boolean isDisconnected() {
        if (loopSuspendedTill == 0)
            return false;

        long now = System.currentTimeMillis();

        if (loopSuspendedTill <= now) { // time exceeded
            suspendTo(0L);
            return false;
        }
        return isDisconnected;
    }

    public void invoke(String initiator, boolean allowNotification) {
        try {
            if (Config.logFunctionCalls)
                log.debug("invoke from " + initiator);
            ConstraintsInterface constraintsInterface = MainApp.getConfigBuilder();
            if (!constraintsInterface.isLoopEnabled()) {
                log.debug(MainApp.sResources.getString(R.string.loopdisabled));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.loopdisabled)));
                return;
            }
            final PumpInterface pump = ConfigBuilderPlugin.getActivePump();
            APSResult result = null;

            if (!isEnabled(PluginBase.LOOP))
                return;

            if (MainApp.getConfigBuilder().getProfile() == null) {
                log.debug(MainApp.sResources.getString(R.string.noprofileselected));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.noprofileselected)));
                return;
            }

            // Check if pump info is loaded
            if (pump.getBaseBasalRate() < 0.01d) return;

            APSInterface usedAPS = ConfigBuilderPlugin.getActiveAPS();
            if (usedAPS != null && ((PluginBase) usedAPS).isEnabled(PluginBase.APS)) {
                usedAPS.invoke(initiator);
                result = usedAPS.getLastAPSResult();
            }

            // Check if we have any result
            if (result == null) {
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.noapsselected)));
                return;
            }

            // check rate for constrais
            final APSResult resultAfterConstraints = result.clone();
            resultAfterConstraints.rate = constraintsInterface.applyBasalConstraints(resultAfterConstraints.rate);

            if (lastRun == null) lastRun = new LastRun();
            lastRun.request = result;
            lastRun.constraintsProcessed = resultAfterConstraints;
            lastRun.lastAPSRun = new Date();
            lastRun.source = ((PluginBase) usedAPS).getName();
            lastRun.setByPump = null;

            NSUpload.uploadDeviceStatus();

            if (isSuspended()) {
                log.debug(MainApp.sResources.getString(R.string.loopsuspended));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.loopsuspended)));
                return;
            }

            if (pump.isSuspended()) {
                log.debug(MainApp.sResources.getString(R.string.pumpsuspended));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.pumpsuspended)));
                return;
            }

            if (constraintsInterface.isClosedModeEnabled()) {
                if (result.changeRequested) {
                    final PumpEnactResult waiting = new PumpEnactResult();
                    final PumpEnactResult previousResult = lastRun.setByPump;
                    waiting.queued = true;
                    lastRun.setByPump = waiting;
                    MainApp.bus().post(new EventLoopUpdateGui());
                    MainApp.getConfigBuilder().applyAPSRequest(resultAfterConstraints, new Callback() {
                        @Override
                        public void run() {
                            Answers.getInstance().logCustom(new CustomEvent("APSRequest"));
                            if (result.enacted || result.success) {
                                lastRun.setByPump = result;
                                lastRun.lastEnact = lastRun.lastAPSRun;
                            } else {
                                lastRun.setByPump = previousResult;
                            }
                            MainApp.bus().post(new EventLoopUpdateGui());
                        }
                    });
                } else {
                    lastRun.setByPump = null;
                    lastRun.source = null;
                }
            } else {
                if (result.changeRequested && allowNotification) {
                    NotificationCompat.Builder builder =
                            new NotificationCompat.Builder(MainApp.instance().getApplicationContext());
                    builder.setSmallIcon(R.drawable.notif_icon)
                            .setContentTitle(MainApp.sResources.getString(R.string.openloop_newsuggestion))
                            .setContentText(resultAfterConstraints.toString())
                            .setAutoCancel(true)
                            .setPriority(Notification.PRIORITY_HIGH)
                            .setCategory(Notification.CATEGORY_ALARM)
                            .setVisibility(Notification.VISIBILITY_PUBLIC);

                    // Creates an explicit intent for an Activity in your app
                    Intent resultIntent = new Intent(MainApp.instance().getApplicationContext(), MainActivity.class);

                    // The stack builder object will contain an artificial back stack for the
                    // started Activity.
                    // This ensures that navigating backward from the Activity leads out of
                    // your application to the Home screen.
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(MainApp.instance().getApplicationContext());
                    stackBuilder.addParentStack(MainActivity.class);
                    // Adds the Intent that starts the Activity to the top of the stack
                    stackBuilder.addNextIntent(resultIntent);
                    PendingIntent resultPendingIntent =
                            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.setContentIntent(resultPendingIntent);
                    builder.setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});
                    NotificationManager mNotificationManager =
                            (NotificationManager) MainApp.instance().getSystemService(Context.NOTIFICATION_SERVICE);
                    // mId allows you to update the notification later on.
                    mNotificationManager.notify(Constants.notificationID, builder.build());
                    MainApp.bus().post(new EventNewOpenLoopNotification());
                }
            }

            MainApp.bus().post(new EventLoopUpdateGui());
        } finally {
            if (Config.logFunctionCalls)
                log.debug("invoke end");
        }
    }

}
