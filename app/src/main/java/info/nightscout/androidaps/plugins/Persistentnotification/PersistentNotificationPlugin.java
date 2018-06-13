package info.nightscout.androidaps.plugins.Persistentnotification;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by adrian on 23/12/16.
 */

public class PersistentNotificationPlugin extends PluginBase {

    public static final String CHANNEL_ID = "AndroidAPS-Ongoing";

    private static final int ONGOING_NOTIFICATION_ID = 4711;
    private final Context ctx;

    public PersistentNotificationPlugin(Context ctx) {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .neverVisible(true)
                .pluginName(R.string.ongoingnotificaction)
                .enableByDefault(true)
        );
        this.ctx = ctx;
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        createNotificationChannel();
        updateNotification();
        super.onStart();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager mNotificationManager =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            @SuppressLint("WrongConstant") NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_ID,
                    NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
        NotificationManager mNotificationManager =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(ONGOING_NOTIFICATION_ID);
    }

    private void updateNotification() {
        if (!isEnabled(PluginType.GENERAL)) {
            return;
        }

        String line1 = "";

        if (MainApp.getConfigBuilder().getActiveProfileInterface() == null || !MainApp.getConfigBuilder().isProfileValid("Notificiation"))
            return;
        String units = MainApp.getConfigBuilder().getProfileUnits();


        BgReading lastBG = DatabaseHelper.lastBg();
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

        if (lastBG != null) {
            line1 = lastBG.valueToUnitsToString(units);
            if (glucoseStatus != null) {
                line1 += "  Δ" + deltastring(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                        + " avgΔ" + deltastring(glucoseStatus.avgdelta, glucoseStatus.avgdelta * Constants.MGDL_TO_MMOLL, units);
            } else {
                line1 += " " +
                        MainApp.gs(R.string.old_data) +
                        " ";
            }
        } else {
            line1 = MainApp.gs(R.string.missed_bg_readings);
        }

        TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            line1 += "  " + activeTemp.toStringShort();
        }

        //IOB
        TreatmentsPlugin.getPlugin().updateTotalIOBTreatments();
        TreatmentsPlugin.getPlugin().updateTotalIOBTempBasals();
        IobTotal bolusIob = TreatmentsPlugin.getPlugin().getLastCalculationTreatments().round();
        IobTotal basalIob = TreatmentsPlugin.getPlugin().getLastCalculationTempBasals().round();


        String line2 = MainApp.gs(R.string.treatments_iob_label_string) + " " +  DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U " + MainApp.gs(R.string.cob)+": " + IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "PersistentNotificationPlugin").generateCOBString();;
        
        String line3 = DecimalFormatter.to2Decimal(ConfigBuilderPlugin.getActivePump().getBaseBasalRate()) + " U/h";


        line3 += " - " + MainApp.getConfigBuilder().getProfileName();


        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID);
        builder.setOngoing(true);
        builder.setOnlyAlertOnce(true);
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

    private String deltastring(double deltaMGDL, double deltaMMOL, String units) {
        String deltastring = "";
        if (deltaMGDL >= 0) {
            deltastring += "+";
        } else {
            deltastring += "-";

        }
        if (units.equals(Constants.MGDL)) {
            deltastring += DecimalFormatter.to1Decimal(Math.abs(deltaMGDL));
        } else {
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
    public void onStatusEvent(final EventExtendedBolusChange ev) {
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
    public void onStatusEvent(final EventRefreshOverview ev) {
        updateNotification();
    }

}
