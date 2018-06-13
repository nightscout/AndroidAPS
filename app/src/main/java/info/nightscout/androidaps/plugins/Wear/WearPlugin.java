package info.nightscout.androidaps.plugins.Wear;

import android.content.Context;
import android.content.Intent;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventBolusRequested;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissBolusprogressIfRunning;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Wear.wearintegration.WatchUpdaterService;
import info.nightscout.utils.SP;

/**
 * Created by adrian on 17/11/16.
 */

public class WearPlugin extends PluginBase {

    private static WatchUpdaterService watchUS;
    private final Context ctx;

    private static WearPlugin wearPlugin;

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
        );
        this.ctx = ctx;
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        if (watchUS != null) {
            watchUS.setSettings();
        }
        super.onStart();
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
    }

    private void sendDataToWatch(boolean status, boolean basals, boolean bgValue) {
        if (isEnabled(getType())) { //only start service when this plugin is enabled

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
        ctx.startService(new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_RESEND));
    }

    void openSettings() {
        ctx.startService(new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_OPEN_SETTINGS));
    }


    @Subscribe
    public void onStatusEvent(final EventPreferenceChange ev) {
        // possibly new high or low mark
        resendDataToWatch();
        // status may be formated differently
        sendDataToWatch(true, false, false);
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        sendDataToWatch(true, true, false);
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        sendDataToWatch(true, true, false);
    }

    @Subscribe
    public void onStatusEvent(final EventOpenAPSUpdateGui ev) {
        sendDataToWatch(true, true, false);
    }

    @Subscribe
    public void onStatusEvent(final EventExtendedBolusChange ev) {
        sendDataToWatch(true, true, false);
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        sendDataToWatch(true, true, true);
    }

    @Subscribe
    public void onStatusEvent(final EventNewBasalProfile ev) {
        sendDataToWatch(false, true, false);
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshOverview ev) {
        if (WatchUpdaterService.shouldReportLoopStatus(LoopPlugin.getPlugin().isEnabled(PluginType.LOOP))) {
            sendDataToWatch(true, false, false);
        }
    }


    @Subscribe
    public void onStatusEvent(final EventOverviewBolusProgress ev) {
        if (!ev.isSMB() || SP.getBoolean("wear_notifySMB", true)) {
            Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS);
            intent.putExtra("progresspercent", ev.percent);
            intent.putExtra("progressstatus", ev.status);
            ctx.startService(intent);
        }
    }

    @Subscribe
    public void onStatusEvent(final EventBolusRequested ev) {
        String status = String.format(MainApp.gs(R.string.bolusrequested), ev.getAmount());
        Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS);
        intent.putExtra("progresspercent", 0);
        intent.putExtra("progressstatus", status);
        ctx.startService(intent);

    }

    @Subscribe
    public void onStatusEvent(final EventDismissBolusprogressIfRunning ev) {
        if (ev.result == null) return;

        String status;
        if (ev.result.success) {
            status = MainApp.gs(R.string.success);
        } else {
            status = MainApp.gs(R.string.nosuccess);
        }
        Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS);
        intent.putExtra("progresspercent", 100);
        intent.putExtra("progressstatus", status);
        ctx.startService(intent);
    }

    public void requestActionConfirmation(String title, String message, String actionstring) {

        Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_ACTIONCONFIRMATIONREQUEST);
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
