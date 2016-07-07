package info.nightscout.androidaps.plugins.DanaR;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.client.data.NSProfile;

public class DanaRFragment extends Fragment implements PluginBase, PumpInterface {

    private static DanaConnection sDanaConnection = null;

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;
    boolean visibleNow = false;

    public static DanaConnection getDanaConnection() {
        return sDanaConnection;
    }

    public static void setDanaConnection(DanaConnection con) {
        sDanaConnection = con;
    }

    public DanaRFragment() {
    }

    public static DanaRFragment newInstance() {
        DanaRFragment fragment = new DanaRFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.danar_fragment, container, false);
        return view;
    }

    // Plugin base interface
    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.danarpump);
    }

    @Override
    public boolean isEnabled() {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs() {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden() {
        return true;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }

    // Pump interface
    @Override
    public boolean isTempBasalInProgress() {
        return false;
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return false;
    }

    @Override
    public Integer getBatteryPercent() {
        return null;
    }

    @Override
    public Integer getReservoirValue() {
        return null;
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
    public TempBasal getTempBasal() {
        return null;
    }

    @Override
    public TempBasal getExtendedBolus() {
        return null;
    }

    @Override
    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs) {
        return null;
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        return null;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        return null;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return null;
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        return null;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        return null;
    }

    @Override
    public JSONObject getJSONStatus() {
        return null;
    }

    @Override
    public String deviceID() {
        return null;
    }
}
