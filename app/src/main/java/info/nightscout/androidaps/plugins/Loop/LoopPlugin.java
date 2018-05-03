package info.nightscout.androidaps.plugins.Loop;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

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
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopSetLastRunGui;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopUpdateGui;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class LoopPlugin extends PluginBase {
    private static Logger log = LoggerFactory.getLogger(LoopPlugin.class);

    public static final String CHANNEL_ID = "AndroidAPS-Openloop";

    long lastBgTriggeredRun = 0;

    protected static LoopPlugin loopPlugin;

    @NonNull
    public static LoopPlugin getPlugin() {
        if (loopPlugin == null) {
            loopPlugin = new LoopPlugin();
        }
        return loopPlugin;
    }

    private long loopSuspendedTill = 0L; // end of manual loop suspend
    private boolean isSuperBolus = false;
    private boolean isDisconnected = false;

    public class LastRun {
        public APSResult request = null;
        public APSResult constraintsProcessed = null;
        public PumpEnactResult tbrSetByPump = null;
        public PumpEnactResult smbSetByPump = null;
        public String source = null;
        public Date lastAPSRun = null;
        public Date lastEnact = null;
        public Date lastOpenModeAccept;
    }

    static public LastRun lastRun = null;

    public LoopPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.LOOP)
                .fragmentClass(LoopFragment.class.getName())
                .pluginName(R.string.loop)
                .shortName(R.string.loop_shortname)
                .preferencesId(R.xml.pref_closedmode)
        );
        loopSuspendedTill = SP.getLong("loopSuspendedTill", 0L);
        isSuperBolus = SP.getBoolean("isSuperBolus", false);
        isDisconnected = SP.getBoolean("isDisconnected", false);
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        createNotificationChannel();
        super.onStart();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager mNotificationManager =
                    (NotificationManager) MainApp.instance().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            @SuppressLint("WrongConstant") NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_ID,
                    NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        MainApp.bus().unregister(this);
    }

    @Override
    public boolean specialEnableCondition() {
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        return pump == null || pump.getPumpDescription().isTempBasalCapable;
    }
    
    /**
     * This method is triggered once autosens calculation has completed, so the LoopPlugin
     * has current data to work with. However, autosens calculation can be triggered by multiple
     * sources and currently only a new BG should trigger a loop run. Hence we return early if
     * the event causing the calculation is not EventNewBg.
     *
     *  Callers of {@link info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin#runCalculation(String, long, boolean, Event)}
     *  are sources triggering a calculation which triggers this method upon completion.
     */
    @Subscribe
    public void onStatusEvent(final EventAutosensCalculationFinished ev) {
        if (!(ev.cause instanceof EventNewBG)) {
            // Autosens calculation not triggered by a new BG
            return;
        }
        BgReading bgReading = DatabaseHelper.actualBg();
        if (bgReading == null) {
            // BG outdated
            return;
        }
        if (bgReading.date <= lastBgTriggeredRun) {
            // already looped with that value
            return;
        }

        lastBgTriggeredRun = bgReading.date;
        invoke("AutosenseCalculation for " + bgReading, true);
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

    public synchronized void invoke(String initiator, boolean allowNotification){
        invoke(initiator, allowNotification, false);
    }

    public synchronized void invoke(String initiator, boolean allowNotification, boolean tempBasalFallback) {
        try {
            if (Config.logFunctionCalls)
                log.debug("invoke from " + initiator);
            Constraint<Boolean> loopEnabled = MainApp.getConstraintChecker().isLoopInvokationAllowed();

            if (!loopEnabled.value()) {
                String message = MainApp.gs(R.string.loopdisabled) + "\n" + loopEnabled.getReasons();
                log.debug(message);
                MainApp.bus().post(new EventLoopSetLastRunGui(message));
                return;
            }
            final PumpInterface pump = ConfigBuilderPlugin.getActivePump();
            APSResult result = null;

            if (!isEnabled(PluginType.LOOP))
                return;

            Profile profile = MainApp.getConfigBuilder().getProfile();

            if (!MainApp.getConfigBuilder().isProfileValid("Loop")) {
                log.debug(MainApp.gs(R.string.noprofileselected));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.gs(R.string.noprofileselected)));
                return;
            }

            // Check if pump info is loaded
            if (pump.getBaseBasalRate() < 0.01d) return;

            APSInterface usedAPS = ConfigBuilderPlugin.getActiveAPS();
            if (usedAPS != null && ((PluginBase) usedAPS).isEnabled(PluginType.APS)) {
                usedAPS.invoke(initiator, tempBasalFallback);
                result = usedAPS.getLastAPSResult();
            }

            // Check if we have any result
            if (result == null) {
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.gs(R.string.noapsselected)));
                return;
            }

            // check rate for constrais
            final APSResult resultAfterConstraints = result.clone();
            resultAfterConstraints.rateConstraint = new Constraint<>(resultAfterConstraints.rate);
            resultAfterConstraints.rate = MainApp.getConstraintChecker().applyBasalConstraints(resultAfterConstraints.rateConstraint, profile).value();
            resultAfterConstraints.smbConstraint = new Constraint<>(resultAfterConstraints.smb);
            resultAfterConstraints.smb = MainApp.getConstraintChecker().applyBolusConstraints(resultAfterConstraints.smbConstraint).value();

            // safety check for multiple SMBs
            long lastBolusTime = TreatmentsPlugin.getPlugin().getLastBolusTime();
            if (lastBolusTime != 0 && lastBolusTime + 3 * 60 * 1000 > System.currentTimeMillis()) {
                log.debug("SMB requsted but still in 3 min interval");
                resultAfterConstraints.smb = 0;
            }

            if (lastRun == null) lastRun = new LastRun();
            lastRun.request = result;
            lastRun.constraintsProcessed = resultAfterConstraints;
            lastRun.lastAPSRun = new Date();
            lastRun.source = ((PluginBase) usedAPS).getName();
            lastRun.tbrSetByPump = null;
            lastRun.smbSetByPump = null;

            NSUpload.uploadDeviceStatus();

            if (isSuspended()) {
                log.debug(MainApp.gs(R.string.loopsuspended));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.gs(R.string.loopsuspended)));
                return;
            }

            if (pump.isSuspended()) {
                log.debug(MainApp.gs(R.string.pumpsuspended));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.gs(R.string.pumpsuspended)));
                return;
            }

            Constraint<Boolean> closedLoopEnabled = MainApp.getConstraintChecker().isClosedLoopAllowed();

            if (closedLoopEnabled.value()) {
                if (result.isChangeRequested()) {
                    final PumpEnactResult waiting = new PumpEnactResult();
                    waiting.queued = true;
                    if (resultAfterConstraints.tempBasalRequested)
                        lastRun.tbrSetByPump = waiting;
                    if (resultAfterConstraints.bolusRequested)
                        lastRun.smbSetByPump = waiting;
                    MainApp.bus().post(new EventLoopUpdateGui());
                    FabricPrivacy.getInstance().logCustom(new CustomEvent("APSRequest"));
                    MainApp.getConfigBuilder().applyTBRRequest(resultAfterConstraints, profile, new Callback() {
                        @Override
                        public void run() {
                            if (result.enacted || result.success) {
                                lastRun.tbrSetByPump = result;
                                lastRun.lastEnact = lastRun.lastAPSRun;
                                MainApp.getConfigBuilder().applySMBRequest(resultAfterConstraints, new Callback() {
                                    @Override
                                    public void run() {
                                        //Callback is only called if a bolus was acutally requested
                                        if (result.enacted || result.success) {
                                            lastRun.smbSetByPump = result;
                                            lastRun.lastEnact = lastRun.lastAPSRun;
                                        } else {
                                            new Thread(() -> {
                                                SystemClock.sleep(1000);
                                                LoopPlugin.getPlugin().invoke("tempBasalFallback", allowNotification, true);
                                            }).start();
                                            FabricPrivacy.getInstance().logCustom(new CustomEvent("Loop_Run_TempBasalFallback"));
                                        }
                                        MainApp.bus().post(new EventLoopUpdateGui());
                                    }
                                });
                            }
                            MainApp.bus().post(new EventLoopUpdateGui());
                        }
                    });
                } else {
                    lastRun.tbrSetByPump = null;
                    lastRun.smbSetByPump = null;
                }
            } else {
                if (result.isChangeRequested() && allowNotification) {
                    NotificationCompat.Builder builder =
                            new NotificationCompat.Builder(MainApp.instance().getApplicationContext(), CHANNEL_ID);
                    builder.setSmallIcon(R.drawable.notif_icon)
                            .setContentTitle(MainApp.gs(R.string.openloop_newsuggestion))
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
