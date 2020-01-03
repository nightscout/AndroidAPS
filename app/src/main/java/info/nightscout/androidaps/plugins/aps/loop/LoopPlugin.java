package info.nightscout.androidaps.plugins.aps.loop;

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

import androidx.core.app.NotificationCompat;

import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAcceptOpenLoopChange;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopSetLastRunGui;
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopUpdateGui;
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.wear.ActionStringHandler;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.Command;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class LoopPlugin extends PluginBase {
    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final SP sp;
    private final ConstraintChecker constraintChecker;
    private final ResourceHelper resourceHelper;
    private final ProfileFunction profileFunction;
    private final MainApp mainApp;
    private final ConfigBuilderPlugin configBuilderPlugin;
    private final TreatmentsPlugin treatmentsPlugin;
    private final VirtualPumpPlugin virtualPumpPlugin;
    private final Lazy<ActionStringHandler> actionStringHandler;

    private CompositeDisposable disposable = new CompositeDisposable();

    private static final String CHANNEL_ID = "AndroidAPS-Openloop";

    private long lastBgTriggeredRun = 0;

    private long loopSuspendedTill; // end of manual loop suspend
    private boolean isSuperBolus;
    private boolean isDisconnected;

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

    public LastRun lastRun = null;

    @Inject
    public LoopPlugin(
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            SP sp,
            ConstraintChecker constraintChecker,
            ResourceHelper resourceHelper,
            ProfileFunction profileFunction,
            MainApp mainApp,
            ConfigBuilderPlugin configBuilderPlugin,
            TreatmentsPlugin treatmentsPlugin,
            VirtualPumpPlugin virtualPumpPlugin,
            Lazy<ActionStringHandler> actionStringHandler // TODO Adrian use RxBus instead of Lazy
    ) {
        super(new PluginDescription()
                .mainType(PluginType.LOOP)
                .fragmentClass(LoopFragment.class.getName())
                .pluginName(R.string.loop)
                .shortName(R.string.loop_shortname)
                .preferencesId(R.xml.pref_loop)
                .description(R.string.description_loop)
        );
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.sp = sp;
        this.constraintChecker = constraintChecker;
        this.resourceHelper = resourceHelper;
        this.profileFunction = profileFunction;
        this.mainApp = mainApp;
        this.configBuilderPlugin = configBuilderPlugin;
        this.treatmentsPlugin = treatmentsPlugin;
        this.virtualPumpPlugin = virtualPumpPlugin;
        this.actionStringHandler = actionStringHandler;

        loopSuspendedTill = sp.getLong("loopSuspendedTill", 0L);
        isSuperBolus = sp.getBoolean("isSuperBolus", false);
        isDisconnected = sp.getBoolean("isDisconnected", false);
    }

    @Override
    protected void onStart() {
        createNotificationChannel();
        super.onStart();
        disposable.add(rxBus
                .toObservable(EventTempTargetChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> invoke("EventTempTargetChange", true), exception -> FabricPrivacy.getInstance().logException(exception))
        );
        /**
         * This method is triggered once autosens calculation has completed, so the LoopPlugin
         * has current data to work with. However, autosens calculation can be triggered by multiple
         * sources and currently only a new BG should trigger a loop run. Hence we return early if
         * the event causing the calculation is not EventNewBg.
         * <p>
         */
        disposable.add(rxBus
                .toObservable(EventAutosensCalculationFinished.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    // Autosens calculation not triggered by a new BG
                    if (!(event.getCause() instanceof EventNewBG)) return;

                    BgReading bgReading = DatabaseHelper.actualBg();
                    // BG outdated
                    if (bgReading == null) return;
                    // already looped with that value
                    if (bgReading.date <= lastBgTriggeredRun) return;

                    lastBgTriggeredRun = bgReading.date;
                    invoke("AutosenseCalculation for " + bgReading, true);
                }, exception -> FabricPrivacy.getInstance().logException(exception))
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager mNotificationManager =
                    (NotificationManager) mainApp.getSystemService(Context.NOTIFICATION_SERVICE);
            @SuppressLint("WrongConstant") NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_ID,
                    NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onStop() {
        disposable.clear();
        super.onStop();
    }

    @Override
    public boolean specialEnableCondition() {
        PumpInterface pump = configBuilderPlugin.getActivePump();
        return pump == null || pump.getPumpDescription().isTempBasalCapable;
    }

    public long suspendedTo() {
        return loopSuspendedTill;
    }

    public void suspendTo(long endTime) {
        loopSuspendedTill = endTime;
        isSuperBolus = false;
        isDisconnected = false;
        sp.putLong("loopSuspendedTill", loopSuspendedTill);
        sp.putBoolean("isSuperBolus", isSuperBolus);
        sp.putBoolean("isDisconnected", isDisconnected);
    }

    public void superBolusTo(long endTime) {
        loopSuspendedTill = endTime;
        isSuperBolus = true;
        isDisconnected = false;
        sp.putLong("loopSuspendedTill", loopSuspendedTill);
        sp.putBoolean("isSuperBolus", isSuperBolus);
        sp.putBoolean("isDisconnected", isDisconnected);
    }

    private void disconnectTo(long endTime) {
        loopSuspendedTill = endTime;
        isSuperBolus = false;
        isDisconnected = true;
        sp.putLong("loopSuspendedTill", loopSuspendedTill);
        sp.putBoolean("isSuperBolus", isSuperBolus);
        sp.putBoolean("isDisconnected", isDisconnected);
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

    public synchronized void invoke(String initiator, boolean allowNotification) {
        invoke(initiator, allowNotification, false);
    }

    public synchronized void invoke(String initiator, boolean allowNotification, boolean tempBasalFallback) {
        try {
            aapsLogger.debug(LTag.APS, "invoke from " + initiator);
            Constraint<Boolean> loopEnabled = constraintChecker.isLoopInvocationAllowed();

            if (!loopEnabled.value()) {
                String message = resourceHelper.gs(R.string.loopdisabled) + "\n" + loopEnabled.getReasons();
                aapsLogger.debug(LTag.APS, message);
                rxBus.send(new EventLoopSetLastRunGui(message));
                return;
            }
            final PumpInterface pump = configBuilderPlugin.getActivePump();
            if (pump == null)
                return;
            APSResult result = null;

            if (!isEnabled(PluginType.LOOP))
                return;

            Profile profile = profileFunction.getProfile();

            if (profile == null || !profileFunction.isProfileValid("Loop")) {
                if (L.isEnabled(L.APS))
                    aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.noprofileselected));
                rxBus.send(new EventLoopSetLastRunGui(resourceHelper.gs(R.string.noprofileselected)));
                return;
            }

            // Check if pump info is loaded
            if (pump.getBaseBasalRate() < 0.01d) return;

            APSInterface usedAPS = configBuilderPlugin.getActiveAPS();
            if (usedAPS != null && ((PluginBase) usedAPS).isEnabled(PluginType.APS)) {
                usedAPS.invoke(initiator, tempBasalFallback);
                result = usedAPS.getLastAPSResult();
            }

            // Check if we have any result
            if (result == null) {
                rxBus.send(new EventLoopSetLastRunGui(resourceHelper.gs(R.string.noapsselected)));
                return;
            }

            // Prepare for pumps using % basals
            if (pump.getPumpDescription().tempBasalStyle == PumpDescription.PERCENT) {
                result.usePercent = true;
            }
            result.percent = (int) (result.rate / profile.getBasal() * 100);

            // check rate for constrais
            final APSResult resultAfterConstraints = result.clone();
            resultAfterConstraints.rateConstraint = new Constraint<>(resultAfterConstraints.rate);
            resultAfterConstraints.rate = constraintChecker.applyBasalConstraints(resultAfterConstraints.rateConstraint, profile).value();

            resultAfterConstraints.percentConstraint = new Constraint<>(resultAfterConstraints.percent);
            resultAfterConstraints.percent = constraintChecker.applyBasalPercentConstraints(resultAfterConstraints.percentConstraint, profile).value();

            resultAfterConstraints.smbConstraint = new Constraint<>(resultAfterConstraints.smb);
            resultAfterConstraints.smb = constraintChecker.applyBolusConstraints(resultAfterConstraints.smbConstraint).value();

            // safety check for multiple SMBs
            long lastBolusTime = treatmentsPlugin.getLastBolusTime();
            if (lastBolusTime != 0 && lastBolusTime + T.mins(3).msecs() > System.currentTimeMillis()) {
                aapsLogger.debug(LTag.APS, "SMB requsted but still in 3 min interval");
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
                aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.loopsuspended));
                rxBus.send(new EventLoopSetLastRunGui(resourceHelper.gs(R.string.loopsuspended)));
                return;
            }

            if (pump.isSuspended()) {
                aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.pumpsuspended));
                rxBus.send(new EventLoopSetLastRunGui(resourceHelper.gs(R.string.pumpsuspended)));
                return;
            }

            Constraint<Boolean> closedLoopEnabled = constraintChecker.isClosedLoopAllowed();

            if (closedLoopEnabled.value()) {
                if (resultAfterConstraints.isChangeRequested()
                        && !configBuilderPlugin.getCommandQueue().bolusInQueue()
                        && !configBuilderPlugin.getCommandQueue().isRunning(Command.CommandType.BOLUS)) {
                    final PumpEnactResult waiting = new PumpEnactResult();
                    waiting.queued = true;
                    if (resultAfterConstraints.tempBasalRequested)
                        lastRun.tbrSetByPump = waiting;
                    if (resultAfterConstraints.bolusRequested)
                        lastRun.smbSetByPump = waiting;
                    rxBus.send(new EventLoopUpdateGui());
                    FabricPrivacy.getInstance().logCustom("APSRequest");
                    applyTBRRequest(resultAfterConstraints, profile, new Callback() {
                        @Override
                        public void run() {
                            if (result.enacted || result.success) {
                                lastRun.tbrSetByPump = result;
                                lastRun.lastEnact = lastRun.lastAPSRun;
                                applySMBRequest(resultAfterConstraints, new Callback() {
                                    @Override
                                    public void run() {
                                        //Callback is only called if a bolus was acutally requested
                                        if (result.enacted || result.success) {
                                            lastRun.smbSetByPump = result;
                                            lastRun.lastEnact = lastRun.lastAPSRun;
                                        } else {
                                            new Thread(() -> {
                                                SystemClock.sleep(1000);
                                                invoke("tempBasalFallback", allowNotification, true);
                                            }).start();
                                        }
                                        rxBus.send(new EventLoopUpdateGui());
                                    }
                                });
                            }
                            rxBus.send(new EventLoopUpdateGui());
                        }
                    });
                } else {
                    lastRun.tbrSetByPump = null;
                    lastRun.smbSetByPump = null;
                }
            } else {
                if (resultAfterConstraints.isChangeRequested() && allowNotification) {
                    NotificationCompat.Builder builder =
                            new NotificationCompat.Builder(mainApp, CHANNEL_ID);
                    builder.setSmallIcon(R.drawable.notif_icon)
                            .setContentTitle(resourceHelper.gs(R.string.openloop_newsuggestion))
                            .setContentText(resultAfterConstraints.toString())
                            .setAutoCancel(true)
                            .setPriority(Notification.PRIORITY_HIGH)
                            .setCategory(Notification.CATEGORY_ALARM)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                    if (sp.getBoolean("wearcontrol", false)) {
                        builder.setLocalOnly(true);
                    }

                    // Creates an explicit intent for an Activity in your app
                    Intent resultIntent = new Intent(mainApp, MainActivity.class);

                    // The stack builder object will contain an artificial back stack for the
                    // started Activity.
                    // This ensures that navigating backward from the Activity leads out of
                    // your application to the Home screen.
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(mainApp);
                    stackBuilder.addParentStack(MainActivity.class);
                    // Adds the Intent that starts the Activity to the top of the stack
                    stackBuilder.addNextIntent(resultIntent);
                    PendingIntent resultPendingIntent =
                            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.setContentIntent(resultPendingIntent);
                    builder.setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});
                    NotificationManager mNotificationManager =
                            (NotificationManager) mainApp.getSystemService(Context.NOTIFICATION_SERVICE);
                    // mId allows you to update the notification later on.
                    mNotificationManager.notify(Constants.notificationID, builder.build());
                    rxBus.send(new EventNewOpenLoopNotification());

                    // Send to Wear
                    actionStringHandler.get().handleInitiate("changeRequest");
                } else if (allowNotification) {
                    // dismiss notifications
                    NotificationManager notificationManager =
                            (NotificationManager) mainApp.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancel(Constants.notificationID);
                    actionStringHandler.get().handleInitiate("cancelChangeRequest");
                }
            }

            rxBus.send(new EventLoopUpdateGui());
        } finally {
            aapsLogger.debug(LTag.APS, "invoke end");
        }
    }

    public void acceptChangeRequest() {
        Profile profile = profileFunction.getProfile();

        applyTBRRequest(lastRun.constraintsProcessed, profile, new Callback() {
            @Override
            public void run() {
                if (result.enacted) {
                    lastRun.tbrSetByPump = result;
                    lastRun.lastEnact = new Date();
                    lastRun.lastOpenModeAccept = new Date();
                    NSUpload.uploadDeviceStatus();
                    sp.incInt(R.string.key_ObjectivesmanualEnacts);
                }
                rxBus.send(new EventAcceptOpenLoopChange());
            }
        });
        FabricPrivacy.getInstance().logCustom("AcceptTemp");
    }

    /**
     * expect absolute request and allow both absolute and percent response based on pump capabilities
     * TODO: update pump drivers to support APS request in %
     */

    private void applyTBRRequest(APSResult request, Profile profile, Callback callback) {
        boolean allowPercentage = virtualPumpPlugin.isEnabled(PluginType.PUMP);

        if (!request.tempBasalRequested) {
            if (callback != null) {
                callback.result(new PumpEnactResult().enacted(false).success(true).comment(resourceHelper.gs(R.string.nochangerequested))).run();
            }
            return;
        }

        PumpInterface pump = configBuilderPlugin.getActivePump();
        if (pump == null) {
            if (callback != null)
                callback.result(new PumpEnactResult().enacted(false).success(false).comment(resourceHelper.gs(R.string.nopumpselected))).run();
            return;
        }

        if (!pump.isInitialized()) {
            aapsLogger.debug(LTag.APS, "applyAPSRequest: " + resourceHelper.gs(R.string.pumpNotInitialized));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(resourceHelper.gs(R.string.pumpNotInitialized)).enacted(false).success(false)).run();
            }
            return;
        }

        if (pump.isSuspended()) {
            aapsLogger.debug(LTag.APS, "applyAPSRequest: " + resourceHelper.gs(R.string.pumpsuspended));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(resourceHelper.gs(R.string.pumpsuspended)).enacted(false).success(false)).run();
            }
            return;
        }

        aapsLogger.debug(LTag.APS, "applyAPSRequest: " + request.toString());

        long now = System.currentTimeMillis();
        TemporaryBasal activeTemp = treatmentsPlugin.getTempBasalFromHistory(now);
        if (request.usePercent && allowPercentage) {
            if (request.percent == 100 && request.duration == 0) {
                if (activeTemp != null) {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: cancelTempBasal()");
                    configBuilderPlugin.getCommandQueue().cancelTempBasal(false, callback);
                } else {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: Basal set correctly");
                    if (callback != null) {
                        callback.result(new PumpEnactResult().percent(request.percent).duration(0)
                                .enacted(false).success(true).comment(resourceHelper.gs(R.string.basal_set_correctly))).run();
                    }
                }
            } else if (activeTemp != null
                    && activeTemp.getPlannedRemainingMinutes() > 5
                    && request.duration - activeTemp.getPlannedRemainingMinutes() < 30
                    && request.percent == activeTemp.percentRate) {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: Temp basal set correctly");
                if (callback != null) {
                    callback.result(new PumpEnactResult().percent(request.percent)
                            .enacted(false).success(true).duration(activeTemp.getPlannedRemainingMinutes())
                            .comment(resourceHelper.gs(R.string.let_temp_basal_run))).run();
                }
            } else {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: tempBasalPercent()");
                configBuilderPlugin.getCommandQueue().tempBasalPercent(request.percent, request.duration, false, profile, callback);
            }
        } else {
            if ((request.rate == 0 && request.duration == 0) || Math.abs(request.rate - pump.getBaseBasalRate()) < pump.getPumpDescription().basalStep) {
                if (activeTemp != null) {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: cancelTempBasal()");
                    configBuilderPlugin.getCommandQueue().cancelTempBasal(false, callback);
                } else {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: Basal set correctly");
                    if (callback != null) {
                        callback.result(new PumpEnactResult().absolute(request.rate).duration(0)
                                .enacted(false).success(true).comment(resourceHelper.gs(R.string.basal_set_correctly))).run();
                    }
                }
            } else if (activeTemp != null
                    && activeTemp.getPlannedRemainingMinutes() > 5
                    && request.duration - activeTemp.getPlannedRemainingMinutes() < 30
                    && Math.abs(request.rate - activeTemp.tempBasalConvertedToAbsolute(now, profile)) < pump.getPumpDescription().basalStep) {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: Temp basal set correctly");
                if (callback != null) {
                    callback.result(new PumpEnactResult().absolute(activeTemp.tempBasalConvertedToAbsolute(now, profile))
                            .enacted(false).success(true).duration(activeTemp.getPlannedRemainingMinutes())
                            .comment(resourceHelper.gs(R.string.let_temp_basal_run))).run();
                }
            } else {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: setTempBasalAbsolute()");
                configBuilderPlugin.getCommandQueue().tempBasalAbsolute(request.rate, request.duration, false, profile, callback);
            }
        }
    }

    private void applySMBRequest(APSResult request, Callback callback) {
        if (!request.bolusRequested) {
            return;
        }

        PumpInterface pump = configBuilderPlugin.getActivePump();
        if (pump == null) {
            if (callback != null)
                callback.result(new PumpEnactResult().enacted(false).success(false).comment(resourceHelper.gs(R.string.nopumpselected))).run();
            return;
        }

        long lastBolusTime = treatmentsPlugin.getLastBolusTime();
        if (lastBolusTime != 0 && lastBolusTime + 3 * 60 * 1000 > System.currentTimeMillis()) {
            aapsLogger.debug(LTag.APS, "SMB requested but still in 3 min interval");
            if (callback != null) {
                callback.result(new PumpEnactResult()
                        .comment(resourceHelper.gs(R.string.smb_frequency_exceeded))
                        .enacted(false).success(false)).run();
            }
            return;
        }

        if (!pump.isInitialized()) {
            aapsLogger.debug(LTag.APS, "applySMBRequest: " + resourceHelper.gs(R.string.pumpNotInitialized));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(resourceHelper.gs(R.string.pumpNotInitialized)).enacted(false).success(false)).run();
            }
            return;
        }

        if (pump.isSuspended()) {
            aapsLogger.debug(LTag.APS, "applySMBRequest: " + resourceHelper.gs(R.string.pumpsuspended));
            if (callback != null) {
                callback.result(new PumpEnactResult().comment(resourceHelper.gs(R.string.pumpsuspended)).enacted(false).success(false)).run();
            }
            return;
        }

        aapsLogger.debug(LTag.APS, "applySMBRequest: " + request.toString());

        // deliver SMB
        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        detailedBolusInfo.lastKnownBolusTime = treatmentsPlugin.getLastBolusTime();
        detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
        detailedBolusInfo.insulin = request.smb;
        detailedBolusInfo.isSMB = true;
        detailedBolusInfo.source = Source.USER;
        detailedBolusInfo.deliverAt = request.deliverAt;
        aapsLogger.debug(LTag.APS, "applyAPSRequest: bolus()");
        configBuilderPlugin.getCommandQueue().bolus(detailedBolusInfo, callback);
    }

    public void disconnectPump(int durationInMinutes, Profile profile) {
        PumpInterface pump = configBuilderPlugin.getActivePump();
        if (pump == null)
            return;

        disconnectTo(System.currentTimeMillis() + durationInMinutes * 60 * 1000L);

        if (pump.getPumpDescription().tempBasalStyle == PumpDescription.ABSOLUTE) {
            configBuilderPlugin.getCommandQueue().tempBasalAbsolute(0, durationInMinutes, true, profile, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        Intent i = new Intent(mainApp, ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", result.comment);
                        i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mainApp.startActivity(i);
                    }
                }
            });
        } else {
            configBuilderPlugin.getCommandQueue().tempBasalPercent(0, durationInMinutes, true, profile, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        Intent i = new Intent(mainApp, ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", result.comment);
                        i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mainApp.startActivity(i);
                    }
                }
            });
        }

        if (pump.getPumpDescription().isExtendedBolusCapable && treatmentsPlugin.isInHistoryExtendedBoluslInProgress()) {
            configBuilderPlugin.getCommandQueue().cancelExtended(new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        Intent i = new Intent(mainApp, ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", result.comment);
                        i.putExtra("title", resourceHelper.gs(R.string.extendedbolusdeliveryerror));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mainApp.startActivity(i);
                    }
                }
            });
        }
        NSUpload.uploadOpenAPSOffline(durationInMinutes);
    }

    public void suspendLoop(int durationInMinutes) {
        suspendTo(System.currentTimeMillis() + durationInMinutes * 60 * 1000);
        configBuilderPlugin.getCommandQueue().cancelTempBasal(true, new Callback() {
            @Override
            public void run() {
                if (!result.success) {
                    Intent i = new Intent(mainApp, ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.boluserror);
                    i.putExtra("status", result.comment);
                    i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mainApp.startActivity(i);
                }
            }
        });
        NSUpload.uploadOpenAPSOffline(durationInMinutes);
    }

}
