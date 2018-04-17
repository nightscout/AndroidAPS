package info.nightscout.androidaps.plugins.XDripStatusline;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by adrian on 17/11/16.
 */

public class StatuslinePlugin implements PluginBase {

    //broadcast related constants
    private static final String EXTRA_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline";
    private static final String ACTION_NEW_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.ExternalStatusline";
    private static final String RECEIVER_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_EXTERNAL_STATUSLINE";


    private boolean fragmentEnabled = false;
    private boolean lastLoopStatus;

    private final Context ctx;
    private SharedPreferences mPrefs;

    private static StatuslinePlugin statuslinePlugin;

    public static StatuslinePlugin getPlugin() {
        return statuslinePlugin;
    }

    public static StatuslinePlugin initPlugin(Context ctx) {

        if (statuslinePlugin == null) {
            statuslinePlugin = new StatuslinePlugin(ctx);
        }

        return statuslinePlugin;
    }

    private StatuslinePlugin(Context ctx) {
        this.ctx = ctx;
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return null;
    }

    @Override
    public String getName() {
        return ctx.getString(R.string.xdripstatus);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.xdripstatus_shortname);
        if (!name.trim().isEmpty()) {
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
        return !Config.NSCLIENT && !Config.G5UPLOADER;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) {
            this.fragmentEnabled = fragmentEnabled;

            if (fragmentEnabled) {
                try {
                    MainApp.bus().register(this);
                } catch (Exception e) {
                }
                sendStatus();
            } else {
                try {
                    MainApp.bus().unregister(this);
                } catch (Exception e) {
                }
                sendStatus();
            }
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        // do nothing, no gui
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_xdripstatus;
    }


    private void sendStatus() {


        String status = ""; // sent once on disable

        if (fragmentEnabled) {
            status = buildStatusString();
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
    private String buildStatusString() {
        String status = "";
        LoopPlugin activeloop = ConfigBuilderPlugin.getActiveLoop();

        if (activeloop != null && !activeloop.isEnabled(PluginBase.LOOP)) {
            status += ctx.getString(R.string.disabledloop) + "\n";
            lastLoopStatus = false;
        } else if (activeloop != null && activeloop.isEnabled(PluginBase.LOOP)) {
            lastLoopStatus = true;
        }

        //Temp basal
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();

        TemporaryBasal activeTemp = treatmentsInterface.getTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            status += activeTemp.toStringShort();
        }

        //IOB
        treatmentsInterface.updateTotalIOBTreatments();
        IobTotal bolusIob = treatmentsInterface.getLastCalculationTreatments().round();
        treatmentsInterface.updateTotalIOBTempBasals();
        IobTotal basalIob = treatmentsInterface.getLastCalculationTempBasals().round();
        status += DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob);


        if (mPrefs.getBoolean("xdripstatus_detailediob", true)) {
            status += "("
                    + DecimalFormatter.to2Decimal(bolusIob.iob) + "|"
                    + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
        }
        Profile profile = MainApp.getConfigBuilder().getProfile();

        if (profile == null)
            return status;

        if (!mPrefs.getBoolean("xdripstatus_showbgi", false)) {
            return status;
        }

        double bgi = -(bolusIob.activity + basalIob.activity) * 5 * profile.getIsf();

        status += " " + ((bgi >= 0) ? "+" : "") + DecimalFormatter.to2Decimal(bgi);

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
    public void onStatusEvent(final EventNewBG ev) {
        sendStatus();
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshOverview ev) {

        //Filter events where loop is (de)activated

        LoopPlugin activeloop = ConfigBuilderPlugin.getActiveLoop();
        if (activeloop == null) return;

        if ((lastLoopStatus != activeloop.isEnabled(PluginBase.LOOP))) {
            sendStatus();
        }
    }


    public boolean isEnabled() {
        return fragmentEnabled;
    }

}
