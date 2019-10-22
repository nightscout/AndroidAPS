package info.nightscout.androidaps.plugins.general.wear;

import android.content.Context;
import android.content.Intent;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventBolusRequested;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.wear.wearintegration.WatchUpdaterService;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by adrian on 17/11/16.
 */

public class WearPlugin extends PluginBase {

    private static WatchUpdaterService watchUS;
    private final Context ctx;

    private static WearPlugin wearPlugin;
    private static String TAG = "WearPlugin";

    private CompositeDisposable disposable = new CompositeDisposable();

    public static WearPlugin getPlugin() {
        return wearPlugin;
    }

    public static WearPlugin initPlugin(Context ctx) {

        if (wearPlugin == null) {
            wearPlugin = new WearPlugin(ctx);
        }

        return wearPlugin;
    }

    WearPlugin(Context ctx) {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(WearFragment.class.getName())
                .pluginName(R.string.wear)
                .shortName(R.string.wear_shortname)
                .preferencesId(R.xml.pref_wear)
                .description(R.string.description_wear)
        );
        this.ctx = ctx;
    }

    @Override
    protected void onStart() {
        if (watchUS != null) {
            watchUS.setSettings();
        }
        super.onStart();

        disposable.add(RxBus.INSTANCE
                .toObservable(EventOpenAPSUpdateGui.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> sendDataToWatch(true, true, false),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventExtendedBolusChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> sendDataToWatch(true, true, false),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventTempBasalChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> sendDataToWatch(true, true, false),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventTreatmentChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> sendDataToWatch(true, true, false),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNewBasalProfile.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> sendDataToWatch(false, true, false),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAutosensCalculationFinished.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> sendDataToWatch(true, true, true),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                            // possibly new high or low mark
                            resendDataToWatch();
                            // status may be formated differently
                            sendDataToWatch(true, false, false);
                        }, FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventRefreshOverview.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                            if (WatchUpdaterService.shouldReportLoopStatus(LoopPlugin.getPlugin().isEnabled(PluginType.LOOP)))
                                sendDataToWatch(true, false, false);
                        }, FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventBolusRequested.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                            String status = String.format(MainApp.gs(R.string.bolusrequested), event.getAmount());
                            Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS);
                            intent.putExtra("progresspercent", 0);
                            intent.putExtra("progressstatus", status);
                            ctx.startService(intent);
                        }, FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventDismissBolusProgressIfRunning.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                            if (event.getResult() == null) return;
                            String status;
                            if (event.getResult().success) {
                                status = MainApp.gs(R.string.success);
                            } else {
                                status = MainApp.gs(R.string.nosuccess);
                            }
                            Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS);
                            intent.putExtra("progresspercent", 100);
                            intent.putExtra("progressstatus", status);
                            ctx.startService(intent);
                        }, FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventOverviewBolusProgress.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                            if (!event.isSMB() || SP.getBoolean("wear_notifySMB", true)) {
                                Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS);
                                intent.putExtra("progresspercent", event.getPercent());
                                intent.putExtra("progressstatus", event.getStatus());
                                ctx.startService(intent);
                            }
                        }, FabricPrivacy::logException
                ));
    }


    @Override
    protected void onStop() {
        disposable.clear();
        super.onStop();
    }

    private void sendDataToWatch(boolean status, boolean basals, boolean bgValue) {

        //Log.d(TAG, "WR: WearPlugin:sendDataToWatch (status=" + status + ",basals=" + basals + ",bgValue=" + bgValue + ")");

        if (isEnabled(getType())) { // only start service when this plugin is enabled

            if (bgValue) {
                ctx.startService(new Intent(ctx, WatchUpdaterService.class));
            }

            if (basals) {
                ctx.startService(new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_BASALS));
            }

            if (status) {
                ctx.startService(new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_STATUS));
            }
        }
    }

    void resendDataToWatch() {
        //Log.d(TAG, "WR: WearPlugin:resendDataToWatch");
        ctx.startService(new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_RESEND));
    }

    void openSettings() {
        //Log.d(TAG, "WR: WearPlugin:openSettings");
        ctx.startService(new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_OPEN_SETTINGS));
    }

    void requestNotificationCancel(String actionstring) {
        //Log.d(TAG, "WR: WearPlugin:requestNotificationCancel");

        Intent intent = new Intent(ctx, WatchUpdaterService.class)
                .setAction(WatchUpdaterService.ACTION_CANCEL_NOTIFICATION);
        intent.putExtra("actionstring", actionstring);
        ctx.startService(intent);
    }

    public void requestActionConfirmation(String title, String message, String actionstring) {

        Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_ACTIONCONFIRMATIONREQUEST);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        intent.putExtra("actionstring", actionstring);
        ctx.startService(intent);
    }

    public void requestChangeConfirmation(String title, String message, String actionstring) {

        Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_CHANGECONFIRMATIONREQUEST);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        intent.putExtra("actionstring", actionstring);
        ctx.startService(intent);
    }

    public static void registerWatchUpdaterService(WatchUpdaterService wus) {
        watchUS = wus;
    }

    public static void unRegisterWatchUpdaterService() {
        watchUS = null;
    }
}
