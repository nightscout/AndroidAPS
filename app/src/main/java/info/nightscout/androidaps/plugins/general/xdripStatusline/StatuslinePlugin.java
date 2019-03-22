package info.nightscout.androidaps.plugins.general.xdripStatusline;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DecimalFormatter;

/**
 * Created by adrian on 17/11/16.
 */

public class StatuslinePlugin extends PluginBase {

    private static StatuslinePlugin statuslinePlugin;

    public static StatuslinePlugin getPlugin() {
        return statuslinePlugin;
    }

    //broadcast related constants
    private static final String EXTRA_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline";
    private static final String ACTION_NEW_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.ExternalStatusline";
    private static final String RECEIVER_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_EXTERNAL_STATUSLINE";


    private boolean lastLoopStatus;

    private final Context ctx;
    private SharedPreferences mPrefs;


    public static StatuslinePlugin initPlugin(Context ctx) {
        if (statuslinePlugin == null) {
            statuslinePlugin = new StatuslinePlugin(ctx);
        }

        return statuslinePlugin;
    }

    public StatuslinePlugin(Context ctx) {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .pluginName(R.string.xdripstatus)
                .shortName(R.string.xdripstatus_shortname)
                .neverVisible(true)
                .preferencesId(R.xml.pref_xdripstatus)
                .description(R.string.description_xdrip_status_line)
        );
        this.ctx = ctx;
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MainApp.bus().unregister(this);
        sendStatus();
    }

    private void sendStatus() {
        String status = ""; // sent once on disable

        Profile profile = ProfileFunctions.getInstance().getProfile();

        if (isEnabled(PluginType.GENERAL) && profile != null) {
            status = buildStatusString(profile);
        }

        //sendData
        final Bundle bundle = new Bundle();
        bundle.putString(EXTRA_STATUSLINE, status);
        Intent intent = new Intent(ACTION_NEW_EXTERNAL_STATUSLINE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        ctx.sendBroadcast(intent, null);
    }

    @NonNull
    private String buildStatusString(Profile profile) {
        String status = "";

        if (ConfigBuilderPlugin.getPlugin().getActivePump() == null)
            return "";

        LoopPlugin loopPlugin = LoopPlugin.getPlugin();

        if (!loopPlugin.isEnabled(PluginType.LOOP)) {
            status += MainApp.gs(R.string.disabledloop) + "\n";
            lastLoopStatus = false;
        } else if (loopPlugin.isEnabled(PluginType.LOOP)) {
            lastLoopStatus = true;
        }

        //Temp basal
        TreatmentsInterface treatmentsInterface = TreatmentsPlugin.getPlugin();

        TemporaryBasal activeTemp = treatmentsInterface.getTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            status += activeTemp.toStringShort() + " ";
        }

        //IOB
        treatmentsInterface.updateTotalIOBTreatments();
        IobTotal bolusIob = treatmentsInterface.getLastCalculationTreatments().round();
        treatmentsInterface.updateTotalIOBTempBasals();
        IobTotal basalIob = treatmentsInterface.getLastCalculationTempBasals().round();
        status += DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U";


        if (mPrefs.getBoolean("xdripstatus_detailediob", true)) {
            status += "("
                    + DecimalFormatter.to2Decimal(bolusIob.iob) + "|"
                    + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
        }

        if (!mPrefs.getBoolean("xdripstatus_showbgi", false)) {
            return status;
        }

        double bgi = -(bolusIob.activity + basalIob.activity) * 5 * profile.getIsf();

        status += " " + ((bgi >= 0) ? "+" : "") + DecimalFormatter.to2Decimal(bgi);
        status += " " + IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "StatuslinePlugin").generateCOBString();

        return status;
    }


    @Subscribe
    public void onStatusEvent(final EventPreferenceChange ev) {
        // status may be formated differently
        sendStatus();
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        sendStatus();
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        sendStatus();
    }

    @Subscribe
    public void onStatusEvent(final EventExtendedBolusChange ev) {
        sendStatus();
    }

    @Subscribe
    public void onStatusEvent(final EventAutosensCalculationFinished ev) {
        sendStatus();
    }

    @Subscribe
    public void onStatusEvent(final EventAppInitialized ev) {
        sendStatus();
    }

    @Subscribe
    public void onStatusEvent(final EventConfigBuilderChange ev) {
        sendStatus();
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshOverview ev) {
        //Filter events where loop is (de)activated
        if ((lastLoopStatus != LoopPlugin.getPlugin().isEnabled(PluginType.LOOP))) {
            sendStatus();
        }
    }

}
