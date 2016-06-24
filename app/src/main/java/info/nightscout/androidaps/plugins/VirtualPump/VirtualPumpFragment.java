package info.nightscout.androidaps.plugins.VirtualPump;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.APSResult;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;

public class VirtualPumpFragment extends Fragment implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpFragment.class);

    Double defaultBasalValue = 0.2d;

    Integer batteryPercent = 50;
    Integer resevoirInUnits = 50;

    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView extendedBolusView;
    TextView batteryView;
    TextView reservoirView;

    Handler loopHandler = new Handler();
    Runnable refreshLoop = null;

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;
    boolean visibleNow = false;

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.virtualpump);
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

    public void setFragmentEnabled(boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    public static VirtualPumpFragment newInstance() {
        VirtualPumpFragment fragment = new VirtualPumpFragment();
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (refreshLoop == null) {
            refreshLoop = new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                    loopHandler.postDelayed(refreshLoop, 60 * 1000l);
                }
            };
            loopHandler.postDelayed(refreshLoop, 60 * 1000l);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.vitualpump_fragment, container, false);
        basaBasalRateView = (TextView) view.findViewById(R.id.virtualpump_basabasalrate);
        tempBasalView = (TextView) view.findViewById(R.id.virtualpump_tempbasal);
        extendedBolusView = (TextView) view.findViewById(R.id.virtualpump_extendedbolus);
        batteryView = (TextView) view.findViewById(R.id.virtualpump_battery);
        reservoirView = (TextView) view.findViewById(R.id.virtualpump_reservoir);

        updateGUI();
        return view;
    }

    @Override
    public boolean isTempBasalInProgress() {
        return getTempBasal() != null;
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return getExtendedBolus() != null;
    }

    @Override
    public Integer getBatteryPercent() {
        return batteryPercent;
    }

    @Override
    public Integer getReservoirValue() {
        return resevoirInUnits;
    }

    @Override
    public void setNewBasalProfile(NSProfile profile) {
        // Do nothing here. we are using MainApp.getConfigBuilder().getActiveProfile().getProfile();
    }

    @Override
    public double getBaseBasalRate() {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null)
            return defaultBasalValue;
        return profile.getBasal(profile.secondsFromMidnight());
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        if (!isTempBasalInProgress())
            return 0;
        if (getTempBasal().isAbsolute) {
            return getTempBasal().absolute;
        } else {
            NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
            if (profile == null)
                return defaultBasalValue;
            Double baseRate = profile.getBasal(profile.secondsFromMidnight());
            Double tempRate = baseRate * (getTempBasal().percent / 100d);
            return baseRate + tempRate;
        }
    }

    @Override
    public TempBasal getTempBasal() {
        return MainApp.getConfigBuilder().getActiveTempBasals().getTempBasal(new Date());
    }

    @Override
    public TempBasal getExtendedBolus() {
        return MainApp.getConfigBuilder().getActiveTempBasals().getExtendedBolus(new Date());
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        if (!isTempBasalInProgress())
            return 0;
        return getTempBasal().getPlannedRemainingMinutes();
    }

    @Override
    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.bolusDelivered = insulin;
        result.carbsDelivered = carbs;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);

        if (Config.logPumpComm)
            log.debug("Delivering treatment insulin: " + insulin + "U carbs: " + carbs + "g " + result);
        updateGUI();
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        PumpEnactResult result = cancelTempBasal();
        if (!result.success)
            return result;
        TempBasal tempBasal = new TempBasal();
        tempBasal.timeStart = new Date();
        tempBasal.isAbsolute = true;
        tempBasal.absolute = absoluteRate;
        tempBasal.duration = durationInMinutes;
        result.success = true;
        result.enacted = true;
        result.isTempCancel = false;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        try {
            MainApp.instance().getDbHelper().getDaoTempBasals().create(tempBasal);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + result);
        updateGUI();
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();
        if (isTempBasalInProgress()) {
            result = cancelTempBasal();
            if (!result.success)
                return result;
        }
        TempBasal tempBasal = new TempBasal();
        tempBasal.timeStart = new Date();
        tempBasal.isAbsolute = false;
        tempBasal.percent = percent;
        tempBasal.duration = durationInMinutes;
        result.success = true;
        result.enacted = true;
        result.percent = percent;
        result.isPercent = true;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        try {
            MainApp.instance().getDbHelper().getDaoTempBasals().create(tempBasal);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Settings temp basal percent: " + result);
        updateGUI();
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = cancelExtendedBolus();
        if (!result.success)
            return result;
        TempBasal extendedBolus = new TempBasal();
        extendedBolus.timeStart = new Date();
        extendedBolus.isExtended = true;
        extendedBolus.absolute = insulin * 60d / durationInMinutes;
        extendedBolus.duration = durationInMinutes;
        extendedBolus.isAbsolute = true;
        result.success = true;
        result.enacted = true;
        result.bolusDelivered = insulin;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        try {
            MainApp.instance().getDbHelper().getDaoTempBasals().create(extendedBolus);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.enacted = false;
            result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + result);
        updateGUI();
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (isTempBasalInProgress()) {
            result.enacted = true;
            TempBasal tb = getTempBasal();
            tb.timeEnd = new Date();
            try {
                MainApp.instance().getDbHelper().getDaoTempBasals().update(tb);
                //tempBasal = null;
                if (Config.logPumpComm)
                    log.debug("Canceling temp basal: " + result);
                updateGUI();
            } catch (SQLException e) {
                e.printStackTrace();
                result.success = false;
                result.enacted = false;
                result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
            }
        }
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult();
        if (isExtendedBoluslInProgress()) {
            TempBasal extendedBolus = getExtendedBolus();
            extendedBolus.timeEnd = new Date();
            try {
                MainApp.instance().getDbHelper().getDaoTempBasals().update(extendedBolus);
            } catch (SQLException e) {
                e.printStackTrace();
                result.success = false;
                result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
            }
        }
        result.success = true;
        result.enacted = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        updateGUI();
        return result;
    }

    @Override
    public PumpEnactResult applyAPSRequest(APSResult request) {
        // This should be implemented only on ConfigBuilder
        return null;
    }

    @Override
    public JSONObject getJSONStatus() {
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        try {
            battery.put("percent", batteryPercent);
            status.put("status", "normal");
            if (isTempBasalInProgress()) {
                status.put("tempbasalpct", getTempBasal().percent);
                status.put("tempbasalstart", DateUtil.toISOString(getTempBasal().timeStart));
                status.put("tempbasalremainmin", getTempBasal().getPlannedRemainingMinutes());
            }
            status.put("timestamp", DateUtil.toISOString(new Date()));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("reservoir", getReservoirValue());
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
        }
        return pump;
    }

    @Override
    public String deviceID() {
        return "VirtualPump";
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            visibleNow = true;
            updateGUI();
        } else
            visibleNow = false;

    }

    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && visibleNow && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    basaBasalRateView.setText(getBaseBasalRate() + "U");
                    if (isTempBasalInProgress()) {
                        //tempBasalView.setText(tempBasal.toString());
                        tempBasalView.setText(getTempBasal().toString());
                    } else {
                        tempBasalView.setText("");
                    }
                    if (isExtendedBoluslInProgress()) {
                        extendedBolusView.setText(getExtendedBolus().toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    batteryView.setText(getBatteryPercent() + "%");
                    reservoirView.setText(getReservoirValue() + "U");
                }
            });
    }
}
