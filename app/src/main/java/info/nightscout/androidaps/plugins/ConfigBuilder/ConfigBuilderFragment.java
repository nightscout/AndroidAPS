package info.nightscout.androidaps.plugins.ConfigBuilder;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.DeviceStatus;
import info.nightscout.androidaps.plugins.Loop.LoopFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.DetermineBasalResult;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewExtendedBolusDialog;
import info.nightscout.client.data.DbLogger;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;

public class ConfigBuilderFragment extends Fragment implements PluginBase, PumpInterface, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(ConfigBuilderFragment.class);

    ListView bgsourceListView;
    ListView pumpListView;
    ListView loopListView;
    TextView loopLabel;
    ListView treatmentsListView;
    ListView tempsListView;
    ListView profileListView;
    ListView apsListView;
    TextView apsLabel;
    ListView constraintsListView;
    ListView generalListView;
    TextView nsclientVerView;
    TextView nightscoutVerView;

    PluginCustomAdapter bgsourceDataAdapter = null;
    PluginCustomAdapter pumpDataAdapter = null;
    PluginCustomAdapter loopDataAdapter = null;
    PluginCustomAdapter treatmentsDataAdapter = null;
    PluginCustomAdapter tempsDataAdapter = null;
    PluginCustomAdapter profileDataAdapter = null;
    PluginCustomAdapter apsDataAdapter = null;
    PluginCustomAdapter constraintsDataAdapter = null;
    PluginCustomAdapter generalDataAdapter = null;


    BgSourceInterface activeBgSource;
    PumpInterface activePump;
    ProfileInterface activeProfile;
    TreatmentsInterface activeTreatments;
    TempBasalsInterface activeTempBasals;
    LoopFragment activeLoop;

    public String nightscoutVersionName = "";
    public Integer nightscoutVersionCode = 0;
    public String nsClientVersionName = "";
    public Integer nsClientVersionCode = 0;

    ArrayList<PluginBase> pluginList;

    Date lastDeviceStatusUpload = new Date(0);

    // TODO: sorting
    // TODO: Toast and sound when command failed

    public ConfigBuilderFragment() {
        super();
        registerBus();
    }

    public void initialize() {
        pluginList = MainApp.getPluginsList();
        loadSettings();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        View view = inflater.inflate(R.layout.configbuilder_fragment, container, false);
        bgsourceListView = (ListView) view.findViewById(R.id.configbuilder_bgsourcelistview);
        pumpListView = (ListView) view.findViewById(R.id.configbuilder_pumplistview);
        loopListView = (ListView) view.findViewById(R.id.configbuilder_looplistview);
        loopLabel = (TextView) view.findViewById(R.id.configbuilder_looplabel);
        treatmentsListView = (ListView) view.findViewById(R.id.configbuilder_treatmentslistview);
        tempsListView = (ListView) view.findViewById(R.id.configbuilder_tempslistview);
        profileListView = (ListView) view.findViewById(R.id.configbuilder_profilelistview);
        apsListView = (ListView) view.findViewById(R.id.configbuilder_apslistview);
        apsLabel = (TextView) view.findViewById(R.id.configbuilder_apslabel);
        constraintsListView = (ListView) view.findViewById(R.id.configbuilder_constraintslistview);
        generalListView = (ListView) view.findViewById(R.id.configbuilder_generallistview);
        nsclientVerView = (TextView) view.findViewById(R.id.configbuilder_nsclientversion);
        nightscoutVerView = (TextView) view.findViewById(R.id.configbuilder_nightscoutversion);

        nsclientVerView.setText(nsClientVersionName);
        nightscoutVerView.setText(nightscoutVersionName);
        if (nsClientVersionCode < 117) nsclientVerView.setTextColor(Color.RED);
        if (nightscoutVersionCode < 900) nightscoutVerView.setTextColor(Color.RED);
        setViews();
        return view;
    }

    void setViews() {
        bgsourceDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsListByInterface(BgSourceInterface.class), PluginBase.BGSOURCE);
        bgsourceListView.setAdapter(bgsourceDataAdapter);
        setListViewHeightBasedOnChildren(bgsourceListView);
        pumpDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.PUMP), PluginBase.PUMP);
        pumpListView.setAdapter(pumpDataAdapter);
        setListViewHeightBasedOnChildren(pumpListView);
        loopDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.LOOP), PluginBase.LOOP);
        loopListView.setAdapter(loopDataAdapter);
        setListViewHeightBasedOnChildren(loopListView);
        if (MainApp.getSpecificPluginsList(PluginBase.LOOP).size() == 0)
            loopLabel.setVisibility(View.GONE);
        treatmentsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.TREATMENT), PluginBase.TREATMENT);
        treatmentsListView.setAdapter(treatmentsDataAdapter);
        setListViewHeightBasedOnChildren(treatmentsListView);
        tempsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.TEMPBASAL), PluginBase.TEMPBASAL);
        tempsListView.setAdapter(tempsDataAdapter);
        setListViewHeightBasedOnChildren(tempsListView);
        profileDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsListByInterface(ProfileInterface.class), PluginBase.PROFILE);
        profileListView.setAdapter(profileDataAdapter);
        setListViewHeightBasedOnChildren(profileListView);
        apsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.APS), PluginBase.APS);
        apsListView.setAdapter(apsDataAdapter);
        setListViewHeightBasedOnChildren(apsListView);
        if (MainApp.getSpecificPluginsList(PluginBase.APS).size() == 0)
            apsLabel.setVisibility(View.GONE);
        constraintsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class), PluginBase.CONSTRAINTS);
        constraintsListView.setAdapter(constraintsDataAdapter);
        setListViewHeightBasedOnChildren(constraintsListView);
        generalDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.GENERAL), PluginBase.GENERAL);
        generalListView.setAdapter(generalDataAdapter);
        setListViewHeightBasedOnChildren(generalListView);

    }

    /*
     * PluginBase interface
     */
    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.configbuilder);
    }

    @Override
    public boolean isEnabled(int type) {
        return true;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return true;
    }

    @Override
    public boolean canBeHidden(int type) {
        return false;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        // Always enabled
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        // Always visible
    }

    public static ConfigBuilderFragment newInstance() {
        return new ConfigBuilderFragment();
    }


    /*
     * Pump interface
     *
     * Config builder return itself as a pump and check constraints before it passes command to pump driver
     */
    @Override
    public boolean isTempBasalInProgress() {
        return activePump.isTempBasalInProgress();
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return activePump.isExtendedBoluslInProgress();
    }

    @Override
    public void setNewBasalProfile(NSProfile profile) {
        activePump.setNewBasalProfile(profile);
    }

    @Override
    public double getBaseBasalRate() {
        return activePump.getBaseBasalRate();
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        return activePump.getTempBasalAbsoluteRate();
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        return activePump.getTempBasalRemainingMinutes();
    }

    @Override
    public TempBasal getTempBasal(Date time) {
        return activePump.getTempBasal(time);
    }

    @Override
    public TempBasal getTempBasal() {
        return activePump.getTempBasal();
    }

    @Override
    public TempBasal getExtendedBolus() {
        return activePump.getExtendedBolus();
    }

/*
{
    "_id": {
        "$oid": "5789fea07ef0c37deb388240"
    },
    "boluscalc": {
        "profile": "Posunuta snidane",
        "eventTime": "2016-07-16T09:30:14.139Z",
        "targetBGLow": "5.6",
        "targetBGHigh": "5.6",
        "isf": "17",
        "ic": "26",
        "iob": "0.89",
        "cob": "0",
        "insulincob": "0",
        "bg": "3.6",
        "insulinbg": "-0.12",
        "bgdiff": "-2",
        "carbs": "42",
        "gi": "2",
        "insulincarbs": "1.62",
        "othercorrection": "0",
        "insulin": "0.6000000000000001",
        "roundingcorrection": "-0.009999999999999898",
        "carbsneeded": "0"
    },
    "enteredBy": "",
    "eventType": "Bolus Wizard",
    "glucose": 3.6,
    "glucoseType": "Sensor",
    "units": "mmol",
    "carbs": 42,
    "insulin": 0.6,
    "created_at": "2016-07-16T09:30:12.783Z"
}
 */

    public PumpEnactResult deliverTreatmentFromBolusWizard(Double insulin, Integer carbs, Double glucose, String glucoseType, int carbTime, JSONObject boluscalc) {
        insulin = applyBolusConstraints(insulin);
        carbs = applyCarbsConstraints(carbs);

        PumpEnactResult result = activePump.deliverTreatment(insulin, carbs);

        if (Config.logCongigBuilderActions)
            log.debug("deliverTreatmentFromBolusWizard insulin: " + insulin + " carbs: " + carbs + " success: " + result.success + " enacted: " + result.enacted + " bolusDelivered: " + result.bolusDelivered);

        if (result.success) {
            Treatment t = new Treatment();
            t.insulin = result.bolusDelivered;
            t.carbs = (double) result.carbsDelivered;
            t.created_at = new Date();
            try {
                MainApp.getDbHelper().getDaoTreatments().create(t);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            t.setTimeIndex(t.getTimeIndex());
            uploadBolusWizardRecord(t, glucose, glucoseType, carbTime, boluscalc);
            MainApp.bus().post(new EventTreatmentChange());
        }
        return result;
    }

    @Override
    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs) {
        insulin = applyBolusConstraints(insulin);
        carbs = applyCarbsConstraints(carbs);

        PumpEnactResult result = activePump.deliverTreatment(insulin, carbs);

        if (Config.logCongigBuilderActions)
            log.debug("deliverTreatment insulin: " + insulin + " carbs: " + carbs + " success: " + result.success + " enacted: " + result.enacted + " bolusDelivered: " + result.bolusDelivered);

        if (result.success) {
            Treatment t = new Treatment();
            t.insulin = result.bolusDelivered;
            t.carbs = (double) result.carbsDelivered;
            t.created_at = new Date();
            try {
                MainApp.getDbHelper().getDaoTreatments().create(t);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            t.setTimeIndex(t.getTimeIndex());
            t.sendToNSClient();
            MainApp.bus().post(new EventTreatmentChange());
        }
        return result;
    }

    /**
     * apply constraints, set temp based on absolute valus and expecting absolute result
     *
     * @param absoluteRate
     * @param durationInMinutes
     * @return
     */
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        Double rateAfterConstraints = applyBasalConstraints(absoluteRate);
        PumpEnactResult result = activePump.setTempBasalAbsolute(rateAfterConstraints, durationInMinutes);
        if (Config.logCongigBuilderActions)
            log.debug("setTempBasalAbsolute rate: " + rateAfterConstraints + " durationInMinutes: " + durationInMinutes + " success: " + result.success + " enacted: " + result.enacted);
        if (result.enacted && result.success) {
            if (result.isPercent) {
                uploadTempBasalStartPercent(result.percent, result.duration);
            } else {
                uploadTempBasalStartAbsolute(result.absolute, result.duration);
            }
            MainApp.bus().post(new EventTempBasalChange());
        }
        return result;
    }

    /**
     * apply constraints, set temp based on percent and expecting result in percent
     *
     * @param percent           0 ... 100 ...
     * @param durationInMinutes
     * @return result
     */
    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        Integer percentAfterConstraints = applyBasalConstraints(percent);
        PumpEnactResult result = activePump.setTempBasalPercent(percentAfterConstraints, durationInMinutes);
        if (Config.logCongigBuilderActions)
            log.debug("setTempBasalPercent percent: " + percentAfterConstraints + " durationInMinutes: " + durationInMinutes + " success: " + result.success + " enacted: " + result.enacted);
        if (result.enacted && result.success) {
            uploadTempBasalStartPercent(result.percent, result.duration);
            MainApp.bus().post(new EventTempBasalChange());
        }
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        Double rateAfterConstraints = applyBolusConstraints(insulin);
        PumpEnactResult result = activePump.setExtendedBolus(rateAfterConstraints, durationInMinutes);
        if (Config.logCongigBuilderActions)
            log.debug("setExtendedBolus rate: " + rateAfterConstraints + " durationInMinutes: " + durationInMinutes + " success: " + result.success + " enacted: " + result.enacted);
        if (result.enacted && result.success) {
            uploadExtendedBolus(result.bolusDelivered, result.duration);
            MainApp.bus().post(new EventTreatmentChange());
        }
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        PumpEnactResult result = activePump.cancelTempBasal();
        if (Config.logCongigBuilderActions)
            log.debug("cancelTempBasal success: " + result.success + " enacted: " + result.enacted);
        if (result.enacted && result.success) {
            uploadTempBasalEnd();
            MainApp.bus().post(new EventTempBasalChange());
        }
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = activePump.cancelExtendedBolus();
        if (Config.logCongigBuilderActions)
            log.debug("cancelExtendedBolus success: " + result.success + " enacted: " + result.enacted);
        return result;
    }

    /**
     * expect absolute request and allow both absolute and percent response based on pump capabilities
     *
     * @param request
     * @return
     */
    public PumpEnactResult applyAPSRequest(APSResult request) {
        request.rate = applyBasalConstraints(request.rate);
        PumpEnactResult result;

        if (Config.logCongigBuilderActions)
            log.debug("applyAPSRequest: " + request.toString());
        if ((request.rate == 0 && request.duration == 0) || Math.abs(request.rate - getBaseBasalRate()) < 0.1) {
            if (isTempBasalInProgress()) {
                if (Config.logCongigBuilderActions)
                    log.debug("applyAPSRequest: cancelTempBasal()");
                result = cancelTempBasal();
            } else {
                result = new PumpEnactResult();
                result.absolute = request.rate;
                result.duration = 0;
                result.enacted = false;
                result.comment = "Basal set correctly";
                result.success = true;
                if (Config.logCongigBuilderActions)
                    log.debug("applyAPSRequest: Basal set correctly");
            }
        } else if (isTempBasalInProgress() && Math.abs(request.rate - getTempBasalAbsoluteRate()) < 0.1) {
            result = new PumpEnactResult();
            result.absolute = getTempBasalAbsoluteRate();
            result.duration = activePump.getTempBasal().getPlannedRemainingMinutes();
            result.enacted = false;
            result.comment = "Temp basal set correctly";
            result.success = true;
            if (Config.logCongigBuilderActions)
                log.debug("applyAPSRequest: Temp basal set correctly");
        } else {
            if (Config.logCongigBuilderActions)
                log.debug("applyAPSRequest: setTempBasalAbsolute()");
            result = setTempBasalAbsolute(request.rate, request.duration);
        }
        return result;
    }

    @Nullable
    @Override
    public JSONObject getJSONStatus() {
        if (activePump != null)
            return activePump.getJSONStatus();
        else return null;
    }

    @Override
    public String deviceID() {
        if (activePump != null)
            return activePump.deviceID();
        else return "Unknown";
    }

    /*
     * ConfigBuilderFragment code
     */

    private class PluginCustomAdapter extends ArrayAdapter<PluginBase> {

        private ArrayList<PluginBase> pluginList;
        final private int type;

        public PluginCustomAdapter(Context context, int textViewResourceId,
                                   ArrayList<PluginBase> pluginList, int type) {
            super(context, textViewResourceId, pluginList);
            this.pluginList = new ArrayList<PluginBase>();
            this.pluginList.addAll(pluginList);
            this.type = type;
        }

        private class PluginViewHolder {
            TextView name;
            CheckBox checkboxEnabled;
            CheckBox checkboxVisible;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            PluginViewHolder holder = null;

            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.configbuilder_simpleitem, null);

                holder = new PluginViewHolder();
                holder.name = (TextView) convertView.findViewById(R.id.configbuilder_simpleitem_name);
                holder.checkboxEnabled = (CheckBox) convertView.findViewById(R.id.configbuilder_simpleitem_checkboxenabled);
                holder.checkboxVisible = (CheckBox) convertView.findViewById(R.id.configbuilder_simpleitem_checkboxvisible);
                convertView.setTag(holder);

                holder.checkboxEnabled.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        CheckBox cb = (CheckBox) v;
                        PluginBase plugin = (PluginBase) cb.getTag();
                        plugin.setFragmentEnabled(type, cb.isChecked());
                        plugin.setFragmentVisible(type, cb.isChecked());
                        onEnabledCategoryChanged(plugin, type);
                        storeSettings();
                        MainApp.bus().post(new EventRefreshGui());
                    }
                });

                holder.checkboxVisible.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        CheckBox cb = (CheckBox) v;
                        PluginBase plugin = (PluginBase) cb.getTag();
                        plugin.setFragmentVisible(type, cb.isChecked());
                        storeSettings();
                        MainApp.bus().post(new EventRefreshGui());
                    }
                });
            } else {
                holder = (PluginViewHolder) convertView.getTag();
            }

            PluginBase plugin = pluginList.get(position);
            holder.name.setText(plugin.getName());
            holder.checkboxEnabled.setChecked(plugin.isEnabled(type));
            holder.checkboxVisible.setChecked(plugin.isVisibleInTabs(type));
            holder.name.setTag(plugin);
            holder.checkboxEnabled.setTag(plugin);
            holder.checkboxVisible.setTag(plugin);

            if (!plugin.canBeHidden(type)) {
                holder.checkboxEnabled.setEnabled(false);
                holder.checkboxVisible.setEnabled(false);
            }

            int type = plugin.getType();
            // Force enabled if there is only one plugin
            if (type == PluginBase.PUMP || type == PluginBase.TREATMENT || type == PluginBase.TEMPBASAL || type == PluginBase.PROFILE)
                if (pluginList.size() < 2)
                    holder.checkboxEnabled.setEnabled(false);

            // Constraints cannot be disabled
            if (type == PluginBase.CONSTRAINTS)
                holder.checkboxEnabled.setEnabled(false);

            // Hide disabled profiles by default
            if (type == PluginBase.PROFILE) {
                if (!plugin.isEnabled(type)) {
                    holder.checkboxVisible.setEnabled(false);
                    holder.checkboxVisible.setChecked(false);
                } else {
                    holder.checkboxVisible.setEnabled(true);
                }
            }

            return convertView;

        }

    }

    public BgSourceInterface getActiveBgSource() {
        return activeBgSource;
    }

    public PumpInterface getActivePump() {
        return this;
    }

    @Nullable
    public ProfileInterface getActiveProfile() {
        return activeProfile;
    }

    public TreatmentsInterface getActiveTreatments() {
        return activeTreatments;
    }

    public TempBasalsInterface getActiveTempBasals() {
        return activeTempBasals;
    }

    public LoopFragment getActiveLoop() {
        return activeLoop;
    }

    void onEnabledCategoryChanged(PluginBase changedPlugin, int type) {
        int category = changedPlugin.getType();
        ArrayList<PluginBase> pluginsInCategory = null;
        switch (category) {
            // Multiple selection allowed
            case PluginBase.APS:
            case PluginBase.GENERAL:
            case PluginBase.CONSTRAINTS:
            case PluginBase.LOOP:
                break;
            // Single selection allowed
            case PluginBase.PROFILE:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(ProfileInterface.class);
                break;
            case PluginBase.BGSOURCE:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(BgSourceInterface.class);
                break;
            case PluginBase.TEMPBASAL:
            case PluginBase.TREATMENT:
            case PluginBase.PUMP:
                pluginsInCategory = MainApp.getSpecificPluginsList(category);
                break;
        }
        if (pluginsInCategory != null) {
            boolean newSelection = changedPlugin.isEnabled(type);
            if (newSelection) { // new plugin selected -> disable others
                for (PluginBase p : pluginsInCategory) {
                    if (p.getName().equals(changedPlugin.getName())) {
                        // this is new selected
                    } else {
                        p.setFragmentEnabled(type, false);
                        p.setFragmentVisible(type, false);
                    }
                }
            } else { // enable first plugin in list
                pluginsInCategory.get(0).setFragmentEnabled(type, true);
            }
            setViews();
        }
    }

    private void verifySelectionInCategories() {
        ArrayList<PluginBase> pluginsInCategory;

        // PluginBase.PROFILE
        pluginsInCategory = MainApp.getSpecificPluginsListByInterface(ProfileInterface.class);
        activeProfile = (ProfileInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.PROFILE);
        if (Config.logConfigBuilder)
            log.debug("Selected profile interface: " + ((PluginBase) activeProfile).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeProfile).getName())) {
                p.setFragmentVisible(PluginBase.PROFILE, false);
            }
        }

        // PluginBase.BGSOURCE
        pluginsInCategory = MainApp.getSpecificPluginsListByInterface(BgSourceInterface.class);
        activeBgSource = (BgSourceInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.BGSOURCE);
        if (Config.logConfigBuilder)
            log.debug("Selected bgSource interface: " + ((PluginBase) activeBgSource).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeBgSource).getName())) {
                p.setFragmentVisible(PluginBase.BGSOURCE, false);
            }
        }

        // PluginBase.PUMP
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginBase.PUMP);
        activePump = (PumpInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.PUMP);
        if (Config.logConfigBuilder)
            log.debug("Selected pump interface: " + ((PluginBase) activePump).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activePump).getName())) {
                p.setFragmentVisible(PluginBase.PUMP, false);
            }
        }

        // PluginBase.LOOP
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginBase.LOOP);
        activeLoop = (LoopFragment) getTheOneEnabledInArray(pluginsInCategory, PluginBase.LOOP);
        if (activeLoop != null) {
            if (Config.logConfigBuilder)
                log.debug("Selected loop interface: " + activeLoop.getName());
            for (PluginBase p : pluginsInCategory) {
                if (!p.getName().equals(activeLoop.getName())) {
                    p.setFragmentVisible(PluginBase.LOOP, false);
                }
            }
        }

        // PluginBase.TEMPBASAL
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginBase.TEMPBASAL);
        activeTempBasals = (TempBasalsInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.TEMPBASAL);
        if (Config.logConfigBuilder)
            log.debug("Selected tempbasal interface: " + ((PluginBase) activeTempBasals).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeTempBasals).getName())) {
                p.setFragmentVisible(PluginBase.TEMPBASAL, false);
            }
        }

        // PluginBase.TREATMENT
        pluginsInCategory = MainApp.getSpecificPluginsList(PluginBase.TREATMENT);
        activeTreatments = (TreatmentsInterface) getTheOneEnabledInArray(pluginsInCategory, PluginBase.TREATMENT);
        if (Config.logConfigBuilder)
            log.debug("Selected treatment interface: " + ((PluginBase) activeTreatments).getName());
        for (PluginBase p : pluginsInCategory) {
            if (!p.getName().equals(((PluginBase) activeTreatments).getName())) {
                p.setFragmentVisible(PluginBase.TREATMENT, false);
            }
        }
    }

    @Nullable
    private PluginBase getTheOneEnabledInArray(ArrayList<PluginBase> pluginsInCategory, int type) {
        PluginBase found = null;
        for (PluginBase p : pluginsInCategory) {
            if (p.isEnabled(type) && found == null) {
                found = p;
            } else if (p.isEnabled(type)) {
                // set others disabled
                p.setFragmentEnabled(type, false);
            }
        }
        // If none enabled, enable first one
        if (found == null && pluginsInCategory.size() > 0)
            found = pluginsInCategory.get(0);
        return found;
    }

    private void storeSettings() {
        if (pluginList != null) {
            if (Config.logPrefsChange)
                log.debug("Storing settings");
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            SharedPreferences.Editor editor = settings.edit();

            for (int type = 1; type < PluginBase.LAST; type++) {
                for (PluginBase p : pluginList) {
                    String settingEnabled = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Enabled";
                    String settingVisible = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Visible";
                    editor.putBoolean(settingEnabled, p.isEnabled(type));
                    editor.putBoolean(settingVisible, p.isVisibleInTabs(type));
                }
            }
            editor.apply();
            verifySelectionInCategories();
        }
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        for (int type = 1; type < PluginBase.LAST; type++) {
            for (PluginBase p : pluginList) {
                String settingEnabled = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Enabled";
                String settingVisible = "ConfigBuilder_" + type + "_" + p.getClass().getSimpleName() + "_Visible";
                if (SP.contains(settingEnabled))
                    p.setFragmentEnabled(type, SP.getBoolean(settingEnabled, true));
                if (SP.contains(settingVisible))
                    p.setFragmentVisible(type, SP.getBoolean(settingVisible, true));
            }
        }
        verifySelectionInCategories();
    }

    /****
     * Method for Setting the Height of the ListView dynamically.
     * *** Hack to fix the issue of not showing all the items of the ListView
     * *** when placed inside a ScrollView
     ****/
    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null)
            return;

        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.UNSPECIFIED);
        int totalHeight = 0;
        View view = null;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            view = listAdapter.getView(i, view, listView);
            if (i == 0)
                view.setLayoutParams(new ViewGroup.LayoutParams(desiredWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

            view.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += view.getMeasuredHeight();
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
    }

    /**
     * Constraints interface
     **/
    @Override
    public boolean isLoopEnabled() {
        boolean result = true;

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isLoopEnabled();
        }
        return result;
    }

    @Override
    public boolean isClosedModeEnabled() {
        boolean result = true;

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isClosedModeEnabled();
        }
        return result;
    }

    @Override
    public boolean isAutosensModeEnabled() {
        boolean result = true;

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isAutosensModeEnabled();
        }
        return result;
    }

    @Override
    public boolean isAMAModeEnabled() {
        boolean result = true;

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isAMAModeEnabled();
        }
        return result;
    }

    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        Double rateAfterConstrain = absoluteRate;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            rateAfterConstrain = Math.min(constrain.applyBasalConstraints(absoluteRate), rateAfterConstrain);
        }
        return rateAfterConstrain;
    }

    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        Integer rateAfterConstrain = percentRate;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            rateAfterConstrain = Math.min(constrain.applyBasalConstraints(percentRate), rateAfterConstrain);
        }
        return rateAfterConstrain;
    }

    @Override
    public Double applyBolusConstraints(Double insulin) {
        Double insulinAfterConstrain = insulin;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            insulinAfterConstrain = Math.min(constrain.applyBolusConstraints(insulin), insulinAfterConstrain);
        }
        return insulinAfterConstrain;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        Integer carbsAfterConstrain = carbs;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            carbsAfterConstrain = Math.min(constrain.applyCarbsConstraints(carbs), carbsAfterConstrain);
        }
        return carbsAfterConstrain;
    }

    @Override
    public Double applyMaxIOBConstraints(Double maxIob) {
        Double maxIobAfterConstrain = maxIob;
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            maxIobAfterConstrain = Math.min(constrain.applyMaxIOBConstraints(maxIob), maxIobAfterConstrain);
        }
        return maxIobAfterConstrain;
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        // Give some time to Loop
        try {
            Thread.sleep(120 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // if status not uploaded, upload pump status only
        if (new Date().getTime() - lastDeviceStatusUpload.getTime() > 120 * 1000L) {
            uploadDeviceStatus();
        }
    }

    public static void uploadTempBasalStartAbsolute(Double absolute, double durationInMinutes) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", "Temp Basal");
            data.put("duration", durationInMinutes);
            data.put("absolute", absolute);
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString(), ConfigBuilderFragment.class);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void uploadTempBasalStartPercent(Integer percent, double durationInMinutes) {
        try {
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            boolean useAbsolute = SP.getBoolean("ns_sync_use_absolute", false);
            if (useAbsolute) {
                double absolute = MainApp.getConfigBuilder().getActivePump().getBaseBasalRate() * percent / 100d;
                uploadTempBasalStartAbsolute(absolute, durationInMinutes);
            } else {
                Context context = MainApp.instance().getApplicationContext();
                JSONObject data = new JSONObject();
                data.put("eventType", "Temp Basal");
                data.put("duration", durationInMinutes);
                data.put("percent", percent - 100);
                data.put("created_at", DateUtil.toISOString(new Date()));
                data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
                Bundle bundle = new Bundle();
                bundle.putString("action", "dbAdd");
                bundle.putString("collection", "treatments");
                bundle.putString("data", data.toString());
                Intent intent = new Intent(Intents.ACTION_DATABASE);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
                DbLogger.dbAdd(intent, data.toString(), ConfigBuilderFragment.class);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void uploadTempBasalEnd() {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", "Temp Basal");
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString(), ConfigBuilderFragment.class);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void uploadExtendedBolus(Double insulin, double durationInMinutes) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", "Combo Bolus");
            data.put("duration", durationInMinutes);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", insulin);
            data.put("relative", insulin);
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString(), ConfigBuilderFragment.class);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void uploadDeviceStatus() {
        DeviceStatus deviceStatus = new DeviceStatus();
        try {
            LoopFragment.LastRun lastRun = LoopFragment.lastRun;
            if (lastRun != null && lastRun.lastAPSRun.getTime() > new Date().getTime() - 60 * 1000L) {
                // do not send if result is older than 1 min
                APSResult apsResult = lastRun.request;
                apsResult.json().put("timestamp", DateUtil.toISOString(lastRun.lastAPSRun));
                deviceStatus.suggested = apsResult.json();

                if (lastRun.request instanceof DetermineBasalResult) {
                    DetermineBasalResult result = (DetermineBasalResult) lastRun.request;
                    deviceStatus.iob = result.iob.json();
                    deviceStatus.iob.put("time", DateUtil.toISOString(lastRun.lastAPSRun));
                }

                if (lastRun.setByPump != null && lastRun.setByPump.enacted) { // enacted
                    deviceStatus.enacted = lastRun.request.json();
                    deviceStatus.enacted.put("rate", lastRun.setByPump.json().get("rate"));
                    deviceStatus.enacted.put("duration", lastRun.setByPump.json().get("duration"));
                    deviceStatus.enacted.put("recieved", true);
                    JSONObject requested = new JSONObject();
                    requested.put("duration", lastRun.request.duration);
                    requested.put("rate", lastRun.request.rate);
                    requested.put("temp", "absolute");
                    deviceStatus.enacted.put("requested", requested);
                }
            }
            if (getActivePump() != null) {
                deviceStatus.device = "openaps://" + getActivePump().deviceID();
                deviceStatus.pump = getActivePump().getJSONStatus();

                deviceStatus.created_at = DateUtil.toISOString(new Date());

                deviceStatus.sendToNSClient();
                lastDeviceStatusUpload = new Date();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void uploadBolusWizardRecord(Treatment t, double glucose, String glucoseType, int carbTime, JSONObject boluscalc) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Bolus Wizard");
            if (t.insulin != 0d) data.put("insulin", t.insulin);
            if (t.carbs != 0d) data.put("carbs", t.carbs.intValue());
            data.put("created_at", DateUtil.toISOString(t.created_at));
            data.put("timeIndex", t.timeIndex);
            if (glucose != 0d) data.put("glucose", glucose);
            data.put("glucoseType", glucoseType);
            data.put("boluscalc", boluscalc);
            if (carbTime != 0) data.put("preBolus", carbTime);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        uploadCareportalEntryToNS(data);
    }

    public static void uploadCareportalEntryToNS(JSONObject data) {
        try {
            if (data.has("preBolus") && data.has("carbs")) {
                JSONObject prebolus = new JSONObject();
                prebolus.put("carbs", data.get("carbs"));
                data.remove("carbs");
                prebolus.put("eventType", data.get("eventType"));
                if (data.has("enteredBy")) prebolus.put("enteredBy", data.get("enteredBy"));
                if (data.has("notes")) prebolus.put("notes", data.get("notes"));
                long mills = DateUtil.fromISODateString(data.getString("created_at")).getTime();
                Date preBolusDate = new Date(mills + data.getInt("preBolus") * 60000L);
                prebolus.put("created_at", DateUtil.toISOString(preBolusDate));
                uploadCareportalEntryToNS(prebolus);
            }
            Context context = MainApp.instance().getApplicationContext();
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString(), NewExtendedBolusDialog.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
