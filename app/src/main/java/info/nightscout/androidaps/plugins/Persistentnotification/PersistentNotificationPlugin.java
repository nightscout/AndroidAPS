package info.nightscout.androidaps.plugins.Persistentnotification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.NotificationCompat;

import com.squareup.otto.Subscribe;

import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by adrian on 23/12/16.
 */

public class PersistentNotificationPlugin implements PluginBase{

    private static final int ONGOING_NOTIFICATION_ID = 4711;
    static boolean fragmentEnabled = true;
    private final Context ctx;

    public PersistentNotificationPlugin(Context ctx) {
        this.ctx = ctx;
    }


    @Override
    public int getType() {
        return GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return PersistentNotificationFragment.class.getName();
    }

    @Override
    public String getName() {
        return ctx.getString(R.string.ongoingnotificaction);
    }

    @Override
    public String getNameShort() {
        // use long name as fallback (not visible in tabs)
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return false;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return false;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {

        if(getType() == type){
            this.fragmentEnabled = fragmentEnabled;
            checkBusRegistration();
            updateNotification();
        }

    }

    private void updateNotification() {

        if(!fragmentEnabled){
            NotificationManager mNotificationManager =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(ONGOING_NOTIFICATION_ID);
            return;
        }


        String line1 = ctx.getString(R.string.noprofile);
        if (MainApp.getConfigBuilder().getActiveProfile() == null) return;
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();


        BgReading lastBG = GlucoseStatus.lastBg();
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

        if(profile != null && lastBG != null) {
            line1 = lastBG.valueToUnitsToString(profile.getUnits());
            if (glucoseStatus != null) {
                line1 += "  Δ" + deltastring(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, profile.getUnits())
                        + " avgΔ" + deltastring(glucoseStatus.avgdelta, glucoseStatus.avgdelta * Constants.MGDL_TO_MMOLL, profile.getUnits());
            } else {
                line1 += " " +
                        ctx.getString(R.string.old_data) +
                        " ";
            }
        }

        PumpInterface pump = MainApp.getConfigBuilder();

        if (pump.isTempBasalInProgress()) {
            TempBasal activeTemp = pump.getTempBasal();
            line1 += "  " + activeTemp.toStringShort();
        }

        //IOB
        ConfigBuilderPlugin.getActiveTreatments().updateTotalIOB();
        IobTotal bolusIob = ConfigBuilderPlugin.getActiveTreatments().getLastCalculation().round();
        IobTotal basalIob = new IobTotal(new Date().getTime());
        if (ConfigBuilderPlugin.getActiveTempBasals() != null) {
            ConfigBuilderPlugin.getActiveTempBasals().updateTotalIOB();
            basalIob = ConfigBuilderPlugin.getActiveTempBasals().getLastCalculation().round();
        }
        String line2 = ctx.getString(R.string.treatments_iob_label_string) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                + ctx.getString(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                + ctx.getString(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U)";


        String line3 = DecimalFormatter.to2Decimal(pump.getBaseBasalRate()) + " U/h";


        if (profile != null && profile.getActiveProfile() != null)
            line3 += " - " + profile.getActiveProfile();
        

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx);
        builder.setOngoing(true);
        builder.setCategory(NotificationCompat.CATEGORY_STATUS);
        builder.setSmallIcon(R.drawable.ic_notification);
        Bitmap largeIcon = BitmapFactory.decodeResource(ctx.getResources(), R.mipmap.blueowl);
        builder.setLargeIcon(largeIcon);
        builder.setContentTitle(line1);
        builder.setContentText(line2);
        builder.setSubText(line3);

        Intent resultIntent = new Intent(ctx, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        builder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        android.app.Notification notification = builder.build();
        mNotificationManager.notify(ONGOING_NOTIFICATION_ID, notification);

    }

    private void checkBusRegistration() {
        if(fragmentEnabled){
            MainApp.bus().register(this);
        } else {
            try {
                MainApp.bus().unregister(this);
            } catch (Exception e) {}
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
            //no visible fragment
    }

    private String deltastring(double deltaMGDL, double deltaMMOL, String units) {
        String deltastring = "";
        if (deltaMGDL >=0){
            deltastring += "+";
        } else{
            deltastring += "-";

        }
        if (units.equals(Constants.MGDL)){
            deltastring += DecimalFormatter.to1Decimal(Math.abs(deltaMGDL));
        }
        else {
            deltastring += DecimalFormatter.to1Decimal(Math.abs(deltaMMOL));
        }
        return deltastring;
    }


    @Subscribe
    public void onStatusEvent(final EventPreferenceChange ev) {
        updateNotification();
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        updateNotification();
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        updateNotification();
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        updateNotification();
    }

    @Subscribe
    public void onStatusEvent(final EventNewBasalProfile ev) {
        updateNotification();
    }

    @Subscribe
    public void onStatusEvent(final EventInitializationChanged ev) {
        updateNotification();
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        updateNotification();
    }

}
