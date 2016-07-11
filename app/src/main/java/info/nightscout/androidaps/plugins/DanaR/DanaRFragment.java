package info.nightscout.androidaps.plugins.DanaR;


import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.DanaR.Dialogs.ProfileViewDialog;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRNewStatus;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.Round;
import info.nightscout.utils.SetWarnColor;

public class DanaRFragment extends Fragment implements PluginBase, PumpInterface, ConstraintsInterface, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(DanaRFragment.class);

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    private static DanaConnection sDanaConnection = null;
    private static DanaRPump sDanaRPump = new DanaRPump();
    private static boolean useExtendedBoluses = false;

    boolean fragmentPumpEnabled = true;
    boolean fragmentProfileEnabled = true;
    boolean fragmentPumpVisible = true;
    boolean visibleNow = false;

    Handler loopHandler = new Handler();
    Runnable refreshLoop = null;

    NSProfile convertedProfile = null;

    TextView lastConnectionView;
    TextView btConnectionView;
    TextView lastBolusView;
    TextView dailyUnitsView;
    TextView basaBasalRateView;
    TextView tempBasalView;
    TextView extendedBolusView;
    TextView batteryView;
    TextView reservoirView;
    TextView iobView;
    Button viewProfileButton;

    public static DanaConnection getDanaConnection() {
        return sDanaConnection;
    }

    public static void setDanaConnection(DanaConnection con) {
        sDanaConnection = con;
    }

    public static DanaRPump getDanaRPump() {
        return sDanaRPump;
    }

    public DanaRFragment() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        useExtendedBoluses = sharedPreferences.getBoolean("danar_useextended", false);
        mHandlerThread = new HandlerThread(DanaRFragment.class.getSimpleName());
        mHandlerThread.start();

        this.mHandler = new Handler(mHandlerThread.getLooper());
        registerBus();
    }

    public static DanaRFragment newInstance() {
        DanaRFragment fragment = new DanaRFragment();
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (refreshLoop == null) {
            refreshLoop = new Runnable() {
                @Override
                public void run() {
                    if (visibleNow) {
                        Activity activity = getActivity();
                        if (activity != null)
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateGUI();
                                }
                            });
                    }
                    loopHandler.postDelayed(refreshLoop, 60 * 1000l);
                }
            };
            loopHandler.postDelayed(refreshLoop, 60 * 1000l);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.danar_fragment, container, false);
        btConnectionView = (TextView) view.findViewById(R.id.danar_btconnection);
        lastConnectionView = (TextView) view.findViewById(R.id.danar_lastconnection);
        lastBolusView = (TextView) view.findViewById(R.id.danar_lastbolus);
        dailyUnitsView = (TextView) view.findViewById(R.id.danar_dailyunits);
        basaBasalRateView = (TextView) view.findViewById(R.id.danar_basabasalrate);
        tempBasalView = (TextView) view.findViewById(R.id.danar_tempbasal);
        extendedBolusView = (TextView) view.findViewById(R.id.danar_extendedbolus);
        batteryView = (TextView) view.findViewById(R.id.danar_battery);
        reservoirView = (TextView) view.findViewById(R.id.danar_reservoir);
        iobView = (TextView) view.findViewById(R.id.danar_iob);
        viewProfileButton = (Button) view.findViewById(R.id.danar_viewprofile);

        viewProfileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager manager = getFragmentManager();
                ProfileViewDialog profileViewDialog = new ProfileViewDialog();
                profileViewDialog.show(manager, "ProfileViewDialog");
            }
        });

        btConnectionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                                  @Override
                                  public void run() {
                                      if (getDanaConnection() != null)
                                          getDanaConnection().connectIfNotConnected("Connect request from GUI");
                                      else
                                          log.error("Connect req from GUI: getDanaConnection() is null");
                                  }
                              }
                );
            }
        });

        updateGUI();
        return view;
    }

    @Subscribe
    public void onStatusEvent(final EventDanaRConnectionStatus c) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                                       @Override
                                       public void run() {
                                           if (c.sConnecting) {
                                               btConnectionView.setText("{fa-bluetooth-b spin} " + c.sConnectionAttemptNo);
                                           } else {
                                               if (c.sConnected) {
                                                   btConnectionView.setText("{fa-bluetooth}");
                                               } else {
                                                   btConnectionView.setText("{fa-bluetooth-b}");
                                               }
                                           }
                                       }
                                   }
            );
        }
    }

    @Subscribe
    public void onStatusEvent(final EventDanaRNewStatus s) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange s) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange s) {
        boolean previousValue = useExtendedBoluses;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        useExtendedBoluses = sharedPreferences.getBoolean("danar_useextended", false);
        if (useExtendedBoluses != previousValue && isExtendedBoluslInProgress()) {
            getDanaConnection().connectIfNotConnected("EventPreferenceChange");
            getDanaConnection().extendedBolusStop();
        }
        updateGUI();
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
    public boolean isEnabled(int type) {
        if (type == PluginBase.PROFILE) return fragmentProfileEnabled;
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
        else if (type == PluginBase.PUMP) this.fragmentPumpEnabled = fragmentEnabled;
     }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PluginBase.PUMP)
            this.fragmentPumpVisible = fragmentVisible;
    }

    // Pump interface
    @Override
    public boolean isTempBasalInProgress() {
        if (getRealTempBasal() != null) return true;
        if (getExtendedBolus() != null && useExtendedBoluses) return true;
        return false;
    }

    public boolean isRealTempBasalInProgress() {
        return getRealTempBasal() != null; //TODO:  crosscheck here
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return getExtendedBolus() != null; //TODO:  crosscheck here
    }

    @Override
    public void setNewBasalProfile(NSProfile profile) {
        if (getDanaConnection() != null) {
            getDanaConnection().connectIfNotConnected("setNewBasalProfile");
            getDanaConnection().updateBasalsInPump(profile);
        }
    }

    @Override
    public double getBaseBasalRate() {
        return getDanaRPump().currentBasal;
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        if (isRealTempBasalInProgress()) {
            if (getRealTempBasal().isAbsolute) {
                return getRealTempBasal().absolute;
            } else {
                Double baseRate = getBaseBasalRate();
                Double tempRate = baseRate * (getRealTempBasal().percent / 100d);
                return baseRate + tempRate;
            }
        }
        if (isExtendedBoluslInProgress() && useExtendedBoluses) {
            return getBaseBasalRate() + getExtendedBolus().absolute;
        }
        return 0;
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        if (isRealTempBasalInProgress())
            return getRealTempBasal().getPlannedRemainingMinutes();
        if (isExtendedBoluslInProgress() && useExtendedBoluses)
            return getExtendedBolus().getPlannedRemainingMinutes();
        return 0;
    }

    @Override
    public TempBasal getTempBasal() {
        if (isRealTempBasalInProgress())
            return getRealTempBasal();
        if (isExtendedBoluslInProgress() && useExtendedBoluses)
            return getExtendedBolus();
        return null;
    }

    public TempBasal getRealTempBasal() {
        return MainApp.getConfigBuilder().getActiveTempBasals().getTempBasal(new Date());
    }

    @Override
    public TempBasal getExtendedBolus() {
        return MainApp.getConfigBuilder().getActiveTempBasals().getExtendedBolus(new Date());
    }

    @Override
    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs) {
        ConfigBuilderFragment configBuilderFragment = MainApp.getConfigBuilder();
        insulin = configBuilderFragment.applyBolusConstraints(insulin);
        if (insulin > 0 || carbs > 0) {
            getDanaConnection().connectIfNotConnected("deliverTreatment");
            Treatment t = new Treatment();
            t.insulin = insulin;
            if (insulin > 0) getDanaConnection().bolus(insulin, t);
            if (carbs > 0) getDanaConnection().carbsEntry(carbs);
            PumpEnactResult result = new PumpEnactResult();
            result.success = true;
            result.bolusDelivered = t.insulin;
            result.carbsDelivered = carbs;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            if (Config.logPumpActions)
                log.debug("deliverTreatment: OK. Asked: " + insulin + " Delivered: " + result.bolusDelivered);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.bolusDelivered = 0d;
            result.carbsDelivered = 0;
            result.comment = MainApp.instance().getString(R.string.danar_invalidinput);
            log.error("deliverTreatment: Invalid input");
            return result;
        }
    }

    // This is called from APS
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();

        ConfigBuilderFragment configBuilderFragment = MainApp.getConfigBuilder();
        absoluteRate = configBuilderFragment.applyBasalConstraints(absoluteRate);

        final boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate();
        final boolean doHighTemp = absoluteRate > getBaseBasalRate() && !useExtendedBoluses;
        final boolean doExtendedTemp = absoluteRate > getBaseBasalRate() && useExtendedBoluses;

        if (doTempOff) {
            // If extended in progress
            if (isExtendedBoluslInProgress() && useExtendedBoluses) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping extended bolus (doTempOff)");
                return cancelExtendedBolus();
            }
            // If temp in progress
            if (isRealTempBasalInProgress()) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelRealTempBasal();
            }
            result.success = true;
            result.enacted = false;
            result.percent = 100;
            result.isPercent = true;
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: doTempOff OK");
            return result;
        }

        if (doLowTemp || doHighTemp) {
            Integer percentRate = new Double(absoluteRate / getBaseBasalRate() * 100).intValue();
            if (percentRate < 100) percentRate = Round.ceilTo((double) percentRate, 10d).intValue();
            else percentRate = Round.floorTo((double) percentRate, 10d).intValue();
            if (percentRate > 200) {
                percentRate = 200;
            }
            // If extended in progress
            if (isExtendedBoluslInProgress() && useExtendedBoluses) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)");
                result = cancelExtendedBolus();
                if (!result.success) {
                    log.error("setTempBasalAbsolute: Failed to stop previous extended bolus (doLowTemp || doHighTemp)");
                    return result;
                }
            }
            // Check if some temp is already in progress
            if (isRealTempBasalInProgress()) {
                // Correct basal already set ?
                if (getRealTempBasal().percent == percentRate) {
                    result.success = true;
                    result.percent = percentRate;
                    result.absolute = getTempBasalAbsoluteRate();
                    result.enacted = false;
                    result.duration = ((Double) getTempBasalRemainingMinutes()).intValue();
                    result.isPercent = true;
                    if (Config.logPumpActions)
                        log.debug("setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)");
                    return result;
                } else {
                    if (Config.logPumpActions)
                        log.debug("setTempBasalAbsolute: Stopping temp basal (doLowTemp || doHighTemp)");
                    result = cancelRealTempBasal();
                    // Check for proper result
                    if (!result.success) {
                        log.error("setTempBasalAbsolute: Failed to stop previous temp basal (doLowTemp || doHighTemp)");
                        return result;
                    }
                }
            }
            // Convert duration from minutes to hours
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: Setting temp basal " + percentRate + "% for " + durationInMinutes + " mins (doLowTemp || doHighTemp)");
            return setTempBasalPercent(percentRate, durationInMinutes);
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (isRealTempBasalInProgress()) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping temp basal (doExtendedTemp)");
                result = cancelRealTempBasal();
                // Check for proper result
                if (!result.success) {
                    log.error("setTempBasalAbsolute: Failed to stop previous temp basal (doExtendedTemp)");
                    return result;
                }
            }

            // Calculate # of halfHours from minutes
            Integer durationInHalfHours = Math.max(durationInMinutes / 30, 1);
            // We keep current basal running so need to sub current basal
            Double extendedRateToSet = absoluteRate - getBaseBasalRate();
            extendedRateToSet = configBuilderFragment.applyBasalConstraints(extendedRateToSet);
            // needs to be rounded to 0.1
            extendedRateToSet = Round.roundTo(extendedRateToSet, 0.1d);

            // What is current rate of extended bolusing in u/h?
            if (Config.logPumpActions) {
                log.debug("setTempBasalAbsolute: Extended bolus in progress: " + isExtendedBoluslInProgress() + " rate: " + getDanaRPump().extendedBolusAbsoluteRate + "U/h duration remaining: " + getDanaRPump().extendedBolusRemainingMinutes + "min");
                log.debug("setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h");
            }

            // Compare with extended rate in progress
            if (Math.abs(getDanaRPump().extendedBolusAbsoluteRate - extendedRateToSet) < 0.02D) { // Allow some rounding diff
                // correct extended already set
                result.success = true;
                result.absolute = getDanaRPump().extendedBolusAbsoluteRate;
                result.enacted = false;
                result.duration = getDanaRPump().extendedBolusRemainingMinutes;
                result.isPercent = false;
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Correct extended already set");
                return result;
            }

            // Now set new extended, no need to to stop previous (if running) because it's replaced
            Double extendedAmount = extendedRateToSet / 2 * durationInHalfHours;
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: Setting extended: " + extendedAmount + "U  halfhours: " + durationInHalfHours);
            result = setExtendedBolus(extendedAmount, durationInMinutes);
            if (!result.success) {
                log.error("setTempBasalAbsolute: Failed to set extended bolus");
                return result;
            }
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: Extended bolus set ok");
            return result;
        }
        // We should never end here
        log.error("setTempBasalAbsolute: Internal error");
        result.success = false;
        result.comment = "Internal error";
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();
        ConfigBuilderFragment configBuilderFragment = MainApp.getConfigBuilder();
        percent = configBuilderFragment.applyBasalConstraints(percent);
        if (percent < 0) {
            result.enacted = false;
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.danar_invalidinput);
            log.error("setTempBasalPercent: Invalid input");
            return result;
        }
        if (percent > 200) percent = 200;
        if (getDanaRPump().isTempBasalInProgress && getDanaRPump().tempBasalPercent == percent) {
            result.enacted = false;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.duration = getDanaRPump().tempBasalRemainingMin;
            result.percent = getDanaRPump().tempBasalPercent;
            result.isPercent = true;
            if (Config.logPumpActions)
                log.debug("setTempBasalPercent: Correct value already set");
            return result;
        }
        getDanaConnection().connectIfNotConnected("setTempBasalPercent");
        int durationInHours = Math.max(durationInMinutes / 60, 1);
        getDanaConnection().tempBasal(percent, durationInHours);
        if (getDanaRPump().isTempBasalInProgress && getDanaRPump().tempBasalPercent == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.duration = getDanaRPump().tempBasalRemainingMin;
            result.percent = getDanaRPump().tempBasalPercent;
            result.isPercent = true;
            if (Config.logPumpActions)
                log.debug("setTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.danar_valuenotsetproperly);
        log.error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        ConfigBuilderFragment configBuilderFragment = MainApp.getConfigBuilder();
        insulin = configBuilderFragment.applyBolusConstraints(insulin);
        // needs to be rounded to 0.1
        insulin = Round.roundTo(insulin, 0.1d);

        PumpEnactResult result = new PumpEnactResult();
        if (getDanaRPump().isExtendedInProgress && getDanaRPump().extendedBolusAmount - insulin == 0d) {
            result.enacted = false;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.duration = getDanaRPump().extendedBolusRemainingMinutes;
            result.absolute = getDanaRPump().extendedBolusAbsoluteRate;
            result.isPercent = false;
            if (Config.logPumpActions)
                log.debug("setExtendedBolus: Correct extended bolus already set");
            return result;
        }
        getDanaConnection().connectIfNotConnected("setExtendedBolus");
        int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
        getDanaConnection().extendedBolus(insulin, durationInHalfHours);
        if (getDanaRPump().isExtendedInProgress && getDanaRPump().extendedBolusAmount - insulin == 0) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.duration = getDanaRPump().extendedBolusRemainingMinutes;
            result.absolute = getDanaRPump().extendedBolusAbsoluteRate;
            result.bolusDelivered = getDanaRPump().extendedBolusAmount;
            result.isPercent = false;
            if (Config.logPumpActions)
                log.debug("setExtendedBolus: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.danar_valuenotsetproperly);
        log.error("setExtendedBolus: Failed to extended bolus");
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        if (isRealTempBasalInProgress())
            return cancelRealTempBasal();
        if (isExtendedBoluslInProgress())
            return cancelExtendedBolus();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = false;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        return result;
    }

    public PumpEnactResult cancelRealTempBasal() {
        PumpEnactResult result = new PumpEnactResult();
        getDanaConnection().connectIfNotConnected("cancelRealTempBasal");
        if (getDanaRPump().isTempBasalInProgress) {
            getDanaConnection().tempBasalStop();
            result.enacted = true;
        }
        if (!getDanaRPump().isTempBasalInProgress) {
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            if (Config.logPumpActions)
                log.debug("cancelRealTempBasal: OK");
            return result;
        } else {
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.danar_valuenotsetproperly);
            log.error("cancelRealTempBasal: Failed to cancel temp basal");
            return result;
        }
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult();
        getDanaConnection().connectIfNotConnected("cancelExtendedBolus");
        if (getDanaRPump().isExtendedInProgress) {
            getDanaConnection().extendedBolusStop();
            result.enacted = true;
        }
        if (!getDanaRPump().isExtendedInProgress) {
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            if (Config.logPumpActions)
                log.debug("cancelExtendedBolus: OK");
            return result;
        } else {
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.danar_valuenotsetproperly);
            log.error("cancelExtendedBolus: Failed to cancel extended bolus");
            return result;
        }
    }

    @Override
    public JSONObject getJSONStatus() {
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", getDanaRPump().batteryRemaining);
            status.put("status", "normal");
            status.put("timestamp", DateUtil.toISOString(new Date()));
            if (isTempBasalInProgress()) {
                extended.put("TempBasalAbsoluteRate", getTempBasalAbsoluteRate());
                extended.put("TempBasalStart", DateUtil.toISOString(getTempBasal().timeStart));
                extended.put("TempBasalRemaining", getTempBasal().getPlannedRemainingMinutes());
                extended.put("IsExtended", getTempBasal().isExtended);
            }
            extended.put("PumpIOB", getDanaRPump().iob);
            extended.put("LastBolus", DateUtil.toISOString(getDanaRPump().lastBolusTime));
            extended.put("LastBolusAmount", getDanaRPump().lastBolusAmount);

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", (int) getDanaRPump().reservoirRemainingUnits);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
        }
        return pump;
    }

    @Override
    public String deviceID() {
        return getDanaRPump().serialNumber;
    }


    // GUI functions
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            visibleNow = true;
            updateGUI();
        } else
            visibleNow = false;

    }

    private void updateGUI() {
        final DecimalFormat formatNumber0decimalplaces = new DecimalFormat("0");
        final DecimalFormat formatNumber1decimalplaces = new DecimalFormat("0.0");
        final DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
        final DateFormat formatTime = DateFormat.getTimeInstance(DateFormat.SHORT);

        Activity activity = getActivity();
        if (activity != null && visibleNow && basaBasalRateView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if (getDanaRPump().lastConnection.getTime() != 0) {
                        Long agoMsec = new Date().getTime() - getDanaRPump().lastConnection.getTime();
                        int agoMin = (int) (agoMsec / 60d / 1000d);
                        lastConnectionView.setText(formatTime.format(getDanaRPump().lastConnection) + " (" + agoMin + " " + getString(R.string.minago) + ")");
                        SetWarnColor.setColor(lastConnectionView, agoMin, 16d, 31d);
                    }
                    if (getDanaRPump().lastBolusTime.getTime() != 0) {
                        Long agoMsec = new Date().getTime() - getDanaRPump().lastBolusTime.getTime();
                        int agoHours = (int) (agoMsec / 60d / 60d / 1000d);
                        if (agoHours < 6) // max 6h back
                            lastBolusView.setText(formatTime.format(getDanaRPump().lastBolusTime) + " (" + formatNumber1decimalplaces.format(agoHours) + " " + getString(R.string.hoursago) + ") " + formatNumber2decimalplaces.format(getDanaRPump().lastBolusAmount) + " U");
                        else lastBolusView.setText("");
                    }

                    dailyUnitsView.setText(formatNumber0decimalplaces.format(getDanaRPump().dailyTotalUnits) + " / " + getDanaRPump().maxDailyTotalUnits + " U");
                    SetWarnColor.setColor(dailyUnitsView, getDanaRPump().dailyTotalUnits, getDanaRPump().maxDailyTotalUnits * 0.75d, getDanaRPump().maxDailyTotalUnits * 0.9d);
                    basaBasalRateView.setText("( " + (getDanaRPump().activeProfile + 1) + " )  " + formatNumber2decimalplaces.format(getBaseBasalRate()) + " U/h");
                    if (isRealTempBasalInProgress()) {
                        tempBasalView.setText(getRealTempBasal().toString());
                    } else {
                        tempBasalView.setText("");
                    }
                    if (isExtendedBoluslInProgress()) {
                        extendedBolusView.setText(getExtendedBolus().toString());
                    } else {
                        extendedBolusView.setText("");
                    }
                    reservoirView.setText(formatNumber0decimalplaces.format(getDanaRPump().reservoirRemainingUnits) + " / 300 U");
                    SetWarnColor.setColorInverse(reservoirView, getDanaRPump().reservoirRemainingUnits, 50d, 20d);
                    batteryView.setText("{fa-battery-" + (getDanaRPump().batteryRemaining / 25) + "}");
                    SetWarnColor.setColorInverse(reservoirView, getDanaRPump().batteryRemaining, 51d, 26d);
                    iobView.setText(getDanaRPump().iob + " U");
                }
            });

    }

    /**
     * Constraint interface
     */

    @Override
    public boolean isLoopEnabled() {
        return true;
    }

    @Override
    public boolean isClosedModeEnabled() {
        return true;
    }

    @Override
    public boolean isAutosensModeEnabled() {
        return true;
    }

    @Override
    public boolean isAMAModeEnabled() {
        return true;
    }

    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        double origAbsoluteRate = absoluteRate;
        if (getDanaRPump() != null) {
            if (absoluteRate > getDanaRPump().maxBasal) {
                absoluteRate = getDanaRPump().maxBasal;
                if (Config.logConstraintsChanges && origAbsoluteRate != Constants.basalAbsoluteOnlyForCheckLimit)
                    log.debug("Limiting rate " + origAbsoluteRate + "U/h by pump constraint to " + absoluteRate + "U/h");
            }
        }
        return absoluteRate;
    }

    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        Integer origPercentRate = percentRate;
        if (percentRate < 0) percentRate = 0;
        if (percentRate > 200) percentRate = 200;
        if (percentRate != origPercentRate && Config.logConstraintsChanges && origPercentRate != Constants.basalPercentOnlyForCheckLimit)
            log.debug("Limiting percent rate " + origPercentRate + "% to " + percentRate + "%");
        return percentRate;
    }

    @Override
    public Double applyBolusConstraints(Double insulin) {
        double origInsulin = insulin;
        if (getDanaRPump() != null) {
            if (insulin > getDanaRPump().maxBolus) {
                insulin = getDanaRPump().maxBolus;
                if (Config.logConstraintsChanges && origInsulin != Constants.bolusOnlyForCheckLimit)
                    log.debug("Limiting bolus " + origInsulin + "U by pump constraint to " + insulin + "U");
            }
        }
        return insulin;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        return carbs;
    }

    @Override
    public Double applyMaxIOBConstraints(Double maxIob) {
        return maxIob;
    }

    @Nullable
    @Override
    public NSProfile getProfile() {
        DanaRPump pump = getDanaRPump();
        if (pump.lastSettingsRead.getTime() == 0)
            return null; // no info now
        return pump.convertedProfile;
    }

    // TODO: daily total constraint
}
