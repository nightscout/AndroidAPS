package info.nightscout.androidaps.plugins.XDripStatusline;

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
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by adrian on 17/11/16.
 */

public class StatuslinePlugin implements PluginBase {

    //broadcast related constants
    public static final String EXTRA_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline";
    public static final String ACTION_NEW_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.ExternalStatusline";
    public static final String RECEIVER_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_EXTERNAL_STATUSLINE";


    static boolean fragmentEnabled = false;
    private static boolean lastLoopStatus;

    private final Context ctx;
    SharedPreferences mPrefs;

    StatuslinePlugin(Context ctx) {
        this.ctx = ctx;
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return StatuslineFragment.class.getName();
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
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) {
            this.fragmentEnabled = fragmentEnabled;

            if (fragmentEnabled) {
                try {
                    MainApp.bus().register(this);
                } catch (Exception e) {}
                sendStatus();
            }
            else{
                try {
                    MainApp.bus().unregister(this);
                } catch (Exception e) {}
                sendStatus();
            }
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        // do nothing, no gui
    }


    private void sendStatus() {


        String status =  ""; // sent once on disable

        if(fragmentEnabled) {
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
        boolean shortString = true; // make setting?

        LoopPlugin activeloop = MainApp.getConfigBuilder().getActiveLoop();

        if (activeloop != null && !activeloop.isEnabled(PluginBase.LOOP)) {
            status += ctx.getString(R.string.disabledloop) + "\n";
            lastLoopStatus = false;
        } else if (activeloop != null && activeloop.isEnabled(PluginBase.LOOP)) {
            lastLoopStatus = true;
        }

        //Temp basal
        PumpInterface pump = MainApp.getConfigBuilder();

        if (pump.isTempBasalInProgress()) {
            TempBasal activeTemp = pump.getTempBasal();
            if (shortString) {
                status += activeTemp.toStringShort();
            } else {
                status += activeTemp.toStringMedium();
            }
        }

        //IOB
        MainApp.getConfigBuilder().getActiveTreatments().updateTotalIOB();
        IobTotal bolusIob = MainApp.getConfigBuilder().getActiveTreatments().getLastCalculation().round();
        MainApp.getConfigBuilder().getActiveTempBasals().updateTotalIOB();
        IobTotal basalIob = MainApp.getConfigBuilder().getActiveTempBasals().getLastCalculation().round();
        status += (shortString ? "" : (ctx.getString(R.string.treatments_iob_label_string) + " ")) + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob);


        if (mPrefs.getBoolean("xdripstatus_detailediob", true)) {
            status += "("
                    + DecimalFormatter.to2Decimal(bolusIob.iob) + "|"
                    + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
        }
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (!mPrefs.getBoolean("xdripstatus_showbgi", false) ||profile == null || profile.getIsf(NSProfile.secondsFromMidnight()) == null || profile.getIc(NSProfile.secondsFromMidnight()) == null) {
            return status;
        }

        double bgi = -(bolusIob.activity + basalIob.activity)*5*profile.getIsf(NSProfile.secondsFromMidnight());

        status += " " + ((bgi>=0)?"+":"") + DecimalFormatter.to2Decimal(bgi);

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
    public void onStatusEvent(final EventNewBG ev) {
        sendStatus();
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {

        //Filter events where loop is (de)activated

        LoopPlugin activeloop = MainApp.getConfigBuilder().getActiveLoop();
        if (activeloop == null) return;

        if ((lastLoopStatus != activeloop.isEnabled(PluginBase.LOOP))) {
            sendStatus();
        }
    }


    public static boolean isEnabled() {
        return fragmentEnabled;
    }

}
