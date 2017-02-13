package info.nightscout.androidaps.plugins.Loop;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.NotificationCompat;

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
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopSetLastRunGui;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopUpdateGui;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;

/**
 * Created by mike on 05.08.2016.
 */
public class LoopPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(LoopPlugin.class);

    private static Handler sHandler;
    private static HandlerThread sHandlerThread;

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = true;

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
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(LoopPlugin.class.getSimpleName());
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());
        }
        MainApp.bus().register(this);
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
        if (!name.trim().isEmpty()){
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == LOOP && fragmentEnabled && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == LOOP && fragmentVisible && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean canBeHidden(int type) {
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

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        invoke("EventTreatmentChange", true);
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        invoke("EventNewBG", true);
    }

    public void invoke(String initiator, boolean allowNotification) {
        try {
            if (Config.logFunctionCalls)
                log.debug("invoke");
            ConstraintsInterface constraintsInterface = MainApp.getConfigBuilder();
            if (!constraintsInterface.isLoopEnabled()) {
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.loopdisabled)));
                return;
            }
            final ConfigBuilderPlugin configBuilder = MainApp.getConfigBuilder();
            APSResult result = null;

            if (configBuilder == null || !isEnabled(PluginBase.LOOP))
                return;

            // Check if pump info is loaded
            if (configBuilder.getBaseBasalRate() < 0.01d) return;

            APSInterface usedAPS = configBuilder.getActiveAPS();
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

            if (constraintsInterface.isClosedModeEnabled()) {
                if (result.changeRequested) {
                    final PumpEnactResult waiting = new PumpEnactResult();
                    final PumpEnactResult previousResult = lastRun.setByPump;
                    waiting.queued = true;
                    lastRun.setByPump = waiting;
                    MainApp.bus().post(new EventLoopUpdateGui());
                    sHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            final PumpEnactResult applyResult = configBuilder.applyAPSRequest(resultAfterConstraints);
                            if (applyResult.enacted || applyResult.success) {
                                lastRun.setByPump = applyResult;
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
                    builder.setSmallIcon(R.drawable.notification_icon)
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
            MainApp.getConfigBuilder().uploadDeviceStatus(120);
        } finally {
            if (Config.logFunctionCalls)
                log.debug("invoke end");
        }
    }

}
