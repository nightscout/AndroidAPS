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
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.data.Result;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.APSResult;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;

public class VirtualPumpFragment extends Fragment implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpFragment.class);

    Double defaultBasalValue = 0.2d;

    TempBasal tempBasal = null;
    TempBasal extendedBolus = null;
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

    void checkForExpiredTempsAndExtended() {
        long now = new Date().getTime();
        if (isTempBasalInProgress()) {
            long plannedTimeEnd = tempBasal.getPlannedTimeEnd().getTime();
            if (plannedTimeEnd < now) {
                tempBasal.timeEnd = new Date(plannedTimeEnd);
                try {
                    MainApp.instance().getDbHelper().getDaoTempBasals().update(tempBasal);
                } catch (SQLException e) {
                    e.printStackTrace();
                    log.error(e.getMessage());
                }
                if (Config.logPumpComm)
                    log.debug("Canceling expired temp: " + tempBasal);
                tempBasal = null;
                MainApp.bus().post(new EventTreatmentChange());
            }
        }
        if (isExtendedBoluslInProgress()) {
            long plannedTimeEnd = extendedBolus.getPlannedTimeEnd().getTime();
            if (plannedTimeEnd < now) {
                extendedBolus.timeEnd = new Date(plannedTimeEnd);
                try {
                    MainApp.instance().getDbHelper().getDaoTempBasals().update(extendedBolus);
                } catch (SQLException e) {
                    e.printStackTrace();
                    log.error(e.getMessage());
                }
                if (Config.logPumpComm)
                    log.debug("Canceling expired extended bolus: " + extendedBolus);
                extendedBolus = null;
            }
        }
    }

    @Override
    public boolean isTempBasalInProgress() {
        return tempBasal != null;
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return extendedBolus != null;
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
        // Do nothing here. we are using MainActivity.getConfigBuilder().getActiveProfile().getProfile();
    }

    @Override
    public double getBaseBasalRate() {
        NSProfile profile = MainActivity.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null)
            return defaultBasalValue;
        return profile.getBasal(profile.secondsFromMidnight());
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        if (!isTempBasalInProgress())
            return 0;
        if (tempBasal.isAbsolute) {
            return tempBasal.absolute;
        } else {
            NSProfile profile = MainActivity.getConfigBuilder().getActiveProfile().getProfile();
            if (profile == null)
                return defaultBasalValue;
            Double baseRate = profile.getBasal(profile.secondsFromMidnight());
            Double tempRate = baseRate * (tempBasal.percent / 100d);
            return baseRate + tempRate;
        }
    }

    @Override
    public TempBasal getTempBasal() {
        return tempBasal;
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        if (!isTempBasalInProgress())
            return 0;
        return tempBasal.getPlannedRemainingMinutes();
    }

    @Override
    public Result deliverTreatment(Double insulin, Double carbs) {
        Result result = new Result();
        result.success = true;
        result.bolusDelivered = insulin;
        result.comment = getString(R.string.virtualpump_resultok);

        if (Config.logPumpComm)
            log.debug("Delivering treatment insulin: " + insulin + "U carbs: " + carbs + "g " + result);
        updateGUI();
        return result;
    }

    @Override
    public Result setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        checkForExpiredTempsAndExtended();
        Result result = cancelTempBasal();
        if (!result.success)
            return result;
        tempBasal = new TempBasal();
        tempBasal.timeStart = new Date();
        tempBasal.isAbsolute = true;
        tempBasal.absolute = absoluteRate;
        tempBasal.duration = durationInMinutes;
        result.success = true;
        result.enacted = true;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = getString(R.string.virtualpump_resultok);
        try {
            MainApp.instance().getDbHelper().getDaoTempBasals().create(tempBasal);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.comment = getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + result);
        updateGUI();
        return result;
    }

    @Override
    public Result setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        checkForExpiredTempsAndExtended();
        Result result = new Result();
        if (isTempBasalInProgress()) {
            result = cancelTempBasal();
            if (!result.success)
                return result;
        }
        tempBasal = new TempBasal();
        tempBasal.timeStart = new Date();
        tempBasal.isAbsolute = false;
        tempBasal.percent = percent;
        tempBasal.duration = durationInMinutes;
        result.success = true;
        result.enacted = true;
        result.percent = percent;
        result.isPercent = true;
        result.duration = durationInMinutes;
        result.comment = getString(R.string.virtualpump_resultok);
        try {
            MainApp.instance().getDbHelper().getDaoTempBasals().create(tempBasal);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.comment = getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Settings temp basal percent: " + result);
        updateGUI();
        return result;
    }

    @Override
    public Result setExtendedBolus(Double insulin, Integer durationInMinutes) {
        checkForExpiredTempsAndExtended();
        Result result = cancelExtendedBolus();
        if (!result.success)
            return result;
        extendedBolus = new TempBasal();
        extendedBolus.timeStart = new Date();
        extendedBolus.isExtended = true;
        extendedBolus.absolute = insulin * 60d / durationInMinutes;
        extendedBolus.duration = durationInMinutes;
        result.success = true;
        result.enacted = true;
        result.comment = getString(R.string.virtualpump_resultok);
        try {
            MainApp.instance().getDbHelper().getDaoTempBasals().create(extendedBolus);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.comment = getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + result);
        updateGUI();
        return result;
    }

    @Override
    public Result cancelTempBasal() {
        checkForExpiredTempsAndExtended();
        Result result = new Result();
        result.success = true;
        result.comment = getString(R.string.virtualpump_resultok);
        if (isTempBasalInProgress()) {
            result.enacted = true;
            tempBasal.timeEnd = new Date();
            try {
                MainApp.instance().getDbHelper().getDaoTempBasals().update(tempBasal);
            } catch (SQLException e) {
                e.printStackTrace();
                result.success = false;
                result.enacted = false;
                result.comment = getString(R.string.virtualpump_sqlerror);
            }
        }
        tempBasal = null;
        if (Config.logPumpComm)
            log.debug("Canceling temp basal: " + result);
        updateGUI();
        return result;
    }

    @Override
    public Result cancelExtendedBolus() {
        checkForExpiredTempsAndExtended();
        Result result = new Result();
        if (isExtendedBoluslInProgress()) {
            extendedBolus.timeEnd = new Date();
            try {
                MainApp.instance().getDbHelper().getDaoTempBasals().update(tempBasal);
            } catch (SQLException e) {
                e.printStackTrace();
                result.success = false;
                result.comment = getString(R.string.virtualpump_sqlerror);
            }
        }
        result.success = true;
        result.enacted = true;
        result.comment = getString(R.string.virtualpump_resultok);
        extendedBolus = null;
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        updateGUI();
        return result;
    }

    @Override
    public Result applyAPSRequest(APSResult request) {
        if (isTempBasalInProgress()) {
            if (request.rate == getTempBasalAbsoluteRate()) {
                Result noChange = new Result();
                noChange.absolute = request.rate;
                noChange.duration = tempBasal.getPlannedRemainingMinutes();
                noChange.enacted = false;
                noChange.comment = "Temp basal set correctly";
                noChange.success = true;
                return noChange;
            } else {
                return setTempBasalAbsolute(request.rate, request.duration);
            }
        }
        if (request.rate == getBaseBasalRate()) {
            Result noChange = new Result();
            noChange.absolute = request.rate;
            noChange.duration = 0;
            noChange.enacted = false;
            noChange.comment = "Basal set correctly";
            noChange.success = true;
            return noChange;
        }

        return setTempBasalAbsolute(request.rate, request.duration);
    }

    @Override
    public JSONObject getJSONStatus() {
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        try {
            battery.put("percent", batteryPercent);
            status.put("status", "normal");
            //status.put("lastbolus", last_bolus_amount);
            //status.put("lastbolustime", DateUtil.toISOString(last_bolus_time));
            if (isTempBasalInProgress()) {
                status.put("tempbasalpct", tempBasal.percent);
                status.put("tempbasalstart", DateUtil.toISOString(tempBasal.timeStart));
                status.put("tempbasalremainmin", tempBasal.getPlannedRemainingMinutes());
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
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            visibleNow = true;
            updateGUI();
        } else
            visibleNow = false;

    }

    public void updateGUI() {
        checkForExpiredTempsAndExtended();
        Activity activity = getActivity();
        if (activity != null && visibleNow && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    basaBasalRateView.setText(getBaseBasalRate() + "U");
                    if (isTempBasalInProgress()) {
                        tempBasalView.setText(tempBasal.toString());
                    } else {
                        tempBasalView.setText("");
                    }
                    if (isExtendedBoluslInProgress()) {
                        extendedBolusView.setText(extendedBolus.toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    batteryView.setText(getBatteryPercent() + "%");
                    reservoirView.setText(getReservoirValue() + "U");
                }
            });
    }
}
