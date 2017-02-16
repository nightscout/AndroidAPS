package info.nightscout.androidaps.plugins.Wear;

import android.content.Context;
import android.content.Intent;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventBolusRequested;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Wear.wearintegration.WatchUpdaterService;
import info.nightscout.utils.ToastUtils;

/**
 * Created by adrian on 17/11/16.
 */

public class WearPlugin implements PluginBase {

    static boolean fragmentEnabled = true;
    static boolean fragmentVisible = true;
    private static WatchUpdaterService watchUS;
    private final Context ctx;

    WearPlugin(Context ctx) {
        this.ctx = ctx;
        MainApp.bus().register(this);
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return WearFragment.class.getName();
    }

    @Override
    public String getName() {
        return ctx.getString(R.string.wear);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.wear_shortname);
        if (!name.trim().isEmpty()){
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == GENERAL && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == GENERAL && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) {
            this.fragmentEnabled = fragmentEnabled;
            if (watchUS != null) {
                watchUS.setSettings();
            }
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == GENERAL) this.fragmentVisible = fragmentVisible;
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
        //possibly new high or low mark
        resendDataToWatch();
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
    public void onStatusEvent(final EventNewBG ev) {
        sendDataToWatch(true, true, true);
    }

    @Subscribe
    public void onStatusEvent(final EventNewBasalProfile ev) {
        sendDataToWatch(false, true, false);
    }


    @Subscribe
    public void onStatusEvent(final EventOverviewBolusProgress ev) {
        Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS);
        intent.putExtra("progresspercent", ev.percent);
        intent.putExtra("progressstatus", ev.status);
        ctx.startService(intent);
    }

    @Subscribe
    public void onStatusEvent(final EventBolusRequested ev) {
        String status = String.format(MainApp.sResources.getString(R.string.bolusrequested), ev.getAmount());
        Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS);
        intent.putExtra("progresspercent", 0);
        intent.putExtra("progressstatus", status);
        ctx.startService(intent);

    }

    public void requestActionConfirmation(String title, String message, String actionstring){

        Intent intent = new Intent(ctx, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_SEND_ACTIONCONFIRMATIONREQUEST);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        intent.putExtra("actionstring", actionstring);
        ctx.startService(intent);
    }

    public static boolean isEnabled() {
        return fragmentEnabled;
    }

    public static void registerWatchUpdaterService(WatchUpdaterService wus) {
        watchUS = wus;
    }

    public static void unRegisterWatchUpdaterService() {
        watchUS = null;
    }


}
