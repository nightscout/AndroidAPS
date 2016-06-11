package info.nightscout.androidaps.plugins.VirtualPump;


import android.os.Bundle;
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
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Pump;
import info.nightscout.androidaps.data.Result;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.PluginBase;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;

public class VirtualPumpFragment extends Fragment implements PluginBase, Pump {
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

    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public boolean isFragmentVisible() {
        return true;
    }

    public static VirtualPumpFragment newInstance() {
        VirtualPumpFragment fragment = new VirtualPumpFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        updateView();
        return view;
    }

    public void updateView() {
        DateFormat formatDateToJustTime = new SimpleDateFormat("HH:mm");
        DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");

        checkForExpiredTempsAndExtended();
        
        basaBasalRateView.setText(getBaseBasalRate() + "U");
        if (isTempBasalInProgress()) {
            if (tempBasal.isAbsolute) {
                tempBasalView.setText(formatNumber2decimalplaces.format(tempBasal.absolute) + "U/h @" +
                        formatDateToJustTime.format(tempBasal.timeStart) +
                        " " + tempBasal.getRemainingMinutes() + "/" + tempBasal.duration + "min");
            } else { // percent
                tempBasalView.setText(tempBasal.percent + "% @" +
                        formatDateToJustTime.format(tempBasal.timeStart) +
                        " " + tempBasal.getRemainingMinutes() + "/" + tempBasal.duration + "min");
            }
        } else {
            tempBasalView.setText("");
        }
        if (isExtendedBoluslInProgress()) {
            extendedBolusView.setText(formatNumber2decimalplaces.format(extendedBolus.absolute) + "U/h @" +
                    formatDateToJustTime.format(extendedBolus.timeStart) +
                    " " + extendedBolus.getRemainingMinutes() + "/" + extendedBolus.duration + "min");
        } else {
            extendedBolusView.setText("");
        }
        batteryView.setText(getBatteryPercent() + "%");
        reservoirView.setText(getReservoirValue() + "U");
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
        // Do nothing here. we are using MainApp.getNSProfile();
    }

    @Override
    public double getBaseBasalRate() {
        NSProfile profile = MainApp.getNSProfile();
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
            NSProfile profile = MainApp.getNSProfile();
            if (profile == null)
                return defaultBasalValue;
            Double baseRate = profile.getBasal(profile.secondsFromMidnight());
            Double tempRate = baseRate * (tempBasal.percent / 100d);
            return baseRate + tempRate;
        }
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        if (!isTempBasalInProgress())
            return 0;
        return tempBasal.getRemainingMinutes();
    }

    @Override
    public Result deliverTreatment(Double insulin, Double carbs) {
        Result result = new Result();
        result.success = true;
        result.bolusDelivered = insulin;
        result.comment = getString(R.string.virtualpump_resultok);

        Treatment t = new Treatment();
        t.insulin = insulin;
        t.carbs = carbs;
        t.created_at = new Date();
        try {
            MainApp.instance().getDbHelper().getDaoTreatments().create(t);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.comment = getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Delivering treatment: " + t + " " + result);
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
        return result;
    }

    @Override
    public Result setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        checkForExpiredTempsAndExtended();
        Result result = cancelTempBasal();
        if (!result.success)
            return result;
        tempBasal = new TempBasal();
        tempBasal.timeStart = new Date();
        tempBasal.isAbsolute = false;
        tempBasal.percent = percent;
        tempBasal.duration = durationInMinutes;
        result.success = true;
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
        return result;
    }

    @Override
    public Result cancelTempBasal() {
        checkForExpiredTempsAndExtended();
        Result result = new Result();
        if (isTempBasalInProgress()) {
            tempBasal.timeEnd = new Date();
            try {
                MainApp.instance().getDbHelper().getDaoTempBasals().update(tempBasal);
            } catch (SQLException e) {
                e.printStackTrace();
                result.success = false;
                result.comment = getString(R.string.virtualpump_sqlerror);
            }
        }
        result.success = true;
        result.comment = getString(R.string.virtualpump_resultok);
        tempBasal = null;
        if (Config.logPumpComm)
            log.debug("Canceling temp basal: " + result);
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
        result.comment = getString(R.string.virtualpump_resultok);
        extendedBolus = null;
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        return result;
    }

    @Override
    public JSONObject getJSONStatus(){
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
                status.put("tempbasalremainmin", tempBasal.getRemainingMinutes());
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

}
