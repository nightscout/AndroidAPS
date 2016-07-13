package info.nightscout.androidaps.plugins.MM640g;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.otto.Subscribe;

import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.client.data.NSProfile;

public class MM640gFragment extends Fragment implements PluginBase, PumpInterface, BgSourceInterface {


    boolean fragmentPumpEnabled = false;
    boolean fragmentProfileEnabled = false;
    boolean fragmentBgSourceEnabled = false;
    boolean fragmentPumpVisible = true;


    public MM640gFragment() {
        registerBus();
    }

    public static MM640gFragment newInstance() {
        MM640gFragment fragment = new MM640gFragment();
        return fragment;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.mm640g_fragment, container, false);
        return view;
    }

    /**
     * Plugin base interface
     */

    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.mm640g);
    }

    @Override
    public boolean isEnabled(int type) {
        if (type == PluginBase.PROFILE) return fragmentProfileEnabled;
        else if (type == PluginBase.BGSOURCE) return fragmentBgSourceEnabled;
        else if (type == PluginBase.PUMP) return fragmentPumpEnabled;
        else if (type == PluginBase.CONSTRAINTS) return fragmentPumpEnabled;
        return false;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        if (type == PluginBase.PROFILE || type == PluginBase.CONSTRAINTS) return false;
        else if (type == PluginBase.PUMP) return fragmentPumpVisible;
        return false;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PluginBase.PROFILE) this.fragmentProfileEnabled = fragmentEnabled;
        if (type == PluginBase.BGSOURCE) this.fragmentBgSourceEnabled = fragmentEnabled;
        else if (type == PluginBase.PUMP) this.fragmentPumpEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PluginBase.PUMP)
            this.fragmentPumpVisible = fragmentVisible;
    }

    /**
     *  Plugin communications
     */

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange s) {

    }

    /**
     * Pump Interface
     */

    @Override
    public boolean isTempBasalInProgress() {
        return false;
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return false;
    }

    @Override
    public void setNewBasalProfile(NSProfile profile) {

    }

    @Override
    public double getBaseBasalRate() {
        return 0;
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        return 0;
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        return 0;
    }

    @Override
    public TempBasal getTempBasal(Date time) {
        return null;
    }

    @Override
    public TempBasal getTempBasal() {
        return null;
    }

    @Override
    public TempBasal getExtendedBolus() {
        return null;
    }

    @Override
    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs) {
        return new PumpEnactResult();
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        return new PumpEnactResult();
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        return new PumpEnactResult();
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return new PumpEnactResult();
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        return new PumpEnactResult();
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        return new PumpEnactResult();
    }

    @Override
    public JSONObject getJSONStatus() {
        return new JSONObject();
    }

    @Override
    public String deviceID() {
        return "MM640G"; // TODO: probably serial goes here
    }
}
