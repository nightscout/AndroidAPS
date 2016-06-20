package info.nightscout.androidaps.plugins.ConfigBuilder;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
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

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.Result;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.db.Treatment;
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
import info.nightscout.androidaps.plugins.APSResult;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;

public class ConfigBuilderFragment extends Fragment implements PluginBase, PumpInterface, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(ConfigBuilderFragment.class);

    private static final String PREFS_NAME = "Settings";

    ListView bgsourceListView;
    ListView pumpListView;
    ListView loopListView;
    ListView treatmentsListView;
    ListView tempsListView;
    ListView profileListView;
    ListView apsListView;
    ListView constraintsListView;
    ListView generalListView;

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

    ArrayList<PluginBase> pluginList;

    public ConfigBuilderFragment() {
        super();
        registerBus();
    }

    public void initialize() {
        pluginList = MainActivity.getPluginsList();
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
        treatmentsListView = (ListView) view.findViewById(R.id.configbuilder_treatmentslistview);
        tempsListView = (ListView) view.findViewById(R.id.configbuilder_tempslistview);
        profileListView = (ListView) view.findViewById(R.id.configbuilder_profilelistview);
        apsListView = (ListView) view.findViewById(R.id.configbuilder_apslistview);
        constraintsListView = (ListView) view.findViewById(R.id.configbuilder_constraintslistview);
        generalListView = (ListView) view.findViewById(R.id.configbuilder_generallistview);

        setViews();
        return view;
    }

    void setViews() {
        bgsourceDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getSpecificPluginsList(PluginBase.BGSOURCE));
        bgsourceListView.setAdapter(bgsourceDataAdapter);
        setListViewHeightBasedOnChildren(bgsourceListView);
        pumpDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getSpecificPluginsList(PluginBase.PUMP));
        pumpListView.setAdapter(pumpDataAdapter);
        setListViewHeightBasedOnChildren(pumpListView);
        loopDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getSpecificPluginsList(PluginBase.LOOP));
        loopListView.setAdapter(loopDataAdapter);
        setListViewHeightBasedOnChildren(loopListView);
        treatmentsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getSpecificPluginsList(PluginBase.TREATMENT));
        treatmentsListView.setAdapter(treatmentsDataAdapter);
        setListViewHeightBasedOnChildren(treatmentsListView);
        tempsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getSpecificPluginsList(PluginBase.TEMPBASAL));
        tempsListView.setAdapter(tempsDataAdapter);
        setListViewHeightBasedOnChildren(tempsListView);
        profileDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getSpecificPluginsList(PluginBase.PROFILE));
        profileListView.setAdapter(profileDataAdapter);
        setListViewHeightBasedOnChildren(profileListView);
        apsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getSpecificPluginsList(PluginBase.APS));
        apsListView.setAdapter(apsDataAdapter);
        setListViewHeightBasedOnChildren(apsListView);
        constraintsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getSpecificPluginsList(PluginBase.CONSTRAINTS));
        constraintsListView.setAdapter(constraintsDataAdapter);
        setListViewHeightBasedOnChildren(constraintsListView);
        generalDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getSpecificPluginsList(PluginBase.GENERAL));
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
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isVisibleInTabs() {
        return true;
    }

    @Override
    public boolean canBeHidden() {
        return false;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {
        // Always enabled
    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {
        // Always visible
    }

    public static ConfigBuilderFragment newInstance() {
        ConfigBuilderFragment fragment = new ConfigBuilderFragment();
        return fragment;
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
    public Integer getBatteryPercent() {
        return activePump.getBatteryPercent();
    }

    @Override
    public Integer getReservoirValue() {
        return activePump.getReservoirValue();
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
    public TempBasal getTempBasal() {
        return activePump.getTempBasal();
    }

    @Override
    public Result deliverTreatment(Double insulin, Integer carbs) {
        insulin = applyBolusConstraints(insulin);
        carbs = applyCarbsConstraints(carbs);

        Result result = activePump.deliverTreatment(insulin, carbs);

        if (result.success) {
            Treatment t = new Treatment();
            t.insulin = result.bolusDelivered;
            t.carbs = (double) result.carbsDelivered;
            t.created_at = new Date();
            try {
                MainApp.instance().getDbHelper().getDaoTreatments().create(t);
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
    public Result setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        Double rateAfterConstraints = applyBasalConstraints(absoluteRate);
        Result result = activePump.setTempBasalAbsolute(rateAfterConstraints, durationInMinutes);
        if (result.enacted) {
            uploadTempBasalStartAbsolute(result.absolute, result.duration);
            MainApp.bus().post(new EventTempBasalChange());
        }
        return result;
    }

    /**
     * apply constraints, set temp based on percent and expecting result in percent
     *
     * @param percent 0 ... 100 ...
     * @param durationInMinutes
     * @return result
     */
    @Override
    public Result setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        Integer percentAfterConstraints = applyBasalConstraints(percent);
        Result result = activePump.setTempBasalPercent(percentAfterConstraints, durationInMinutes);
        if (result.enacted) {
            uploadTempBasalStartPercent(result.percent, result.duration);
            MainApp.bus().post(new EventTempBasalChange());
        }
        return result;
    }

    @Override
    public Result setExtendedBolus(Double insulin, Integer durationInMinutes) {
        Double rateAfterConstraints = applyBasalConstraints(insulin);
        Result result = activePump.setExtendedBolus(rateAfterConstraints, durationInMinutes);
        if (result.enacted) {
            uploadExtendedBolus(result.bolusDelivered, result.duration);
            MainApp.bus().post(new EventTreatmentChange());
        }
        return result;
    }

    @Override
    public Result cancelTempBasal() {
        Result result = activePump.cancelTempBasal();
        if (result.enacted) {
            uploadTempBasalEnd();
            MainApp.bus().post(new EventTempBasalChange());
        }
        return result;
    }

    @Override
    public Result cancelExtendedBolus() {
        return activePump.cancelExtendedBolus();
    }

    /**
     *  expect absolute request and allow both absolute and percent response based on pump capabilities
     * @param request
     * @return
     */
    @Override
    public Result applyAPSRequest(APSResult request) {
        Double rateAfterConstraints = applyBasalConstraints(request.rate);
        request.rate = rateAfterConstraints;
        Result result = activePump.applyAPSRequest(request);
        if (result.enacted) {
            if (result.isPercent) {
                if (result.percent == 0) {
                    uploadTempBasalEnd();
                } else {
                    uploadTempBasalStartPercent(result.percent, result.duration);
                }
            } else {
                if (result.absolute == 0d) {
                    uploadTempBasalEnd();
                } else {
                    uploadTempBasalStartAbsolute(result.absolute, result.duration);
                }
            }
            MainApp.bus().post(new EventTempBasalChange());
        }
        return result;
    }

    @Override
    public JSONObject getJSONStatus() {
        return activePump.getJSONStatus();
    }

    /*
     * ConfigBuilderFragment code
     */

    private class PluginCustomAdapter extends ArrayAdapter<PluginBase> {

        private ArrayList<PluginBase> pluginList;

        public PluginCustomAdapter(Context context, int textViewResourceId,
                                   ArrayList<PluginBase> pluginList) {
            super(context, textViewResourceId, pluginList);
            this.pluginList = new ArrayList<PluginBase>();
            this.pluginList.addAll(pluginList);
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
                        plugin.setFragmentEnabled(cb.isChecked());
                        if (cb.isChecked()) plugin.setFragmentVisible(true);
                        onEnabledCategoryChanged(plugin);
                        storeSettings();
                    }
                });

                holder.checkboxVisible.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        CheckBox cb = (CheckBox) v;
                        PluginBase plugin = (PluginBase) cb.getTag();
                        plugin.setFragmentVisible(cb.isChecked());
                        storeSettings();
                        MainApp.bus().post(new EventRefreshGui());
                    }
                });
            } else {
                holder = (PluginViewHolder) convertView.getTag();
            }

            PluginBase plugin = pluginList.get(position);
            holder.name.setText(plugin.getName());
            holder.checkboxEnabled.setChecked(plugin.isEnabled());
            holder.checkboxVisible.setChecked(plugin.isVisibleInTabs());
            holder.name.setTag(plugin);
            holder.checkboxEnabled.setTag(plugin);
            holder.checkboxVisible.setTag(plugin);

            if (!plugin.canBeHidden()) {
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
                if (!plugin.isEnabled()) {
                    holder.checkboxVisible.setEnabled(false);
                    holder.checkboxVisible.setChecked(false);
                } else {
                    holder.checkboxVisible.setEnabled(true);
                }
            }

            return convertView;

        }
    }

    @Nullable
    public BgSourceInterface getActiveBgSource() {
        return activeBgSource;
    }

    @Nullable
    public PumpInterface getActivePump() {
        return this;
    }

    @Nullable
    public ProfileInterface getActiveProfile() {
        return activeProfile;
    }

    @Nullable
    public TreatmentsInterface getActiveTreatments() {
        return activeTreatments;
    }

    @Nullable
    public TempBasalsInterface getActiveTempBasals() {
        return activeTempBasals;
    }

    void onEnabledCategoryChanged(PluginBase changedPlugin) {
        int category = changedPlugin.getType();
        ArrayList<PluginBase> pluginsInCategory = MainActivity.getSpecificPluginsList(category);
        switch (category) {
            // Multiple selection allowed
            case PluginBase.APS:
            case PluginBase.GENERAL:
            case PluginBase.CONSTRAINTS:
                break;
            // Single selection allowed
            case PluginBase.PROFILE:
            case PluginBase.PUMP:
            case PluginBase.BGSOURCE:
            case PluginBase.LOOP:
            case PluginBase.TEMPBASAL:
            case PluginBase.TREATMENT:
                boolean newSelection = changedPlugin.isEnabled();
                if (newSelection) { // new plugin selected -> disable others
                    for (PluginBase p : pluginsInCategory) {
                        if (p.getName().equals(changedPlugin.getName())) {
                            // this is new selected
                        } else {
                            p.setFragmentEnabled(false);
                            setViews();
                        }
                    }
                } else { // enable first plugin in list
                    pluginsInCategory.get(0).setFragmentEnabled(true);
                }
                break;
        }
    }

    private void verifySelectionInCategories() {
        for (int category : new int[]{PluginBase.GENERAL, PluginBase.APS, PluginBase.PROFILE, PluginBase.PUMP, PluginBase.LOOP, PluginBase.TEMPBASAL, PluginBase.TREATMENT, PluginBase.BGSOURCE}) {
            ArrayList<PluginBase> pluginsInCategory = MainActivity.getSpecificPluginsList(category);
            switch (category) {
                // Multiple selection allowed
                case PluginBase.APS:
                case PluginBase.GENERAL:
                case PluginBase.CONSTRAINTS:
                    break;
                // Single selection allowed
                case PluginBase.BGSOURCE:
                    activeBgSource = (BgSourceInterface) getTheOneEnabledInArray(pluginsInCategory);
                    if (Config.logConfigBuilder)
                        log.debug("Selected bgSource interface: " + ((PluginBase) activeBgSource).getName());
                    for (PluginBase p : pluginsInCategory) {
                        if (!p.getName().equals(((PluginBase) activeBgSource).getName())) {
                            p.setFragmentVisible(false);
                        }
                    }
                    break;
                // Single selection allowed
                case PluginBase.PROFILE:
                    activeProfile = (ProfileInterface) getTheOneEnabledInArray(pluginsInCategory);
                    if (Config.logConfigBuilder)
                        log.debug("Selected profile interface: " + ((PluginBase) activeProfile).getName());
                    for (PluginBase p : pluginsInCategory) {
                        if (!p.getName().equals(((PluginBase) activeProfile).getName())) {
                            p.setFragmentVisible(false);
                        }
                    }
                    break;
                case PluginBase.PUMP:
                    activePump = (PumpInterface) getTheOneEnabledInArray(pluginsInCategory);
                    if (Config.logConfigBuilder)
                        log.debug("Selected pump interface: " + ((PluginBase) activePump).getName());
                    for (PluginBase p : pluginsInCategory) {
                        if (!p.getName().equals(((PluginBase) activePump).getName())) {
                            p.setFragmentVisible(false);
                        }
                    }
                    break;
                case PluginBase.LOOP:
                    PluginBase loop = getTheOneEnabledInArray(pluginsInCategory);
                    if (Config.logConfigBuilder)
                        log.debug("Selected loop interface: " + loop.getName());
                    for (PluginBase p : pluginsInCategory) {
                        if (!p.getName().equals(loop.getName())) {
                            p.setFragmentVisible(false);
                        }
                    }
                    break;
                case PluginBase.TEMPBASAL:
                    activeTempBasals = (TempBasalsInterface) getTheOneEnabledInArray(pluginsInCategory);
                    if (Config.logConfigBuilder)
                        log.debug("Selected tempbasal interface: " + ((PluginBase) activeTempBasals).getName());
                    for (PluginBase p : pluginsInCategory) {
                        if (!p.getName().equals(((PluginBase) activeTempBasals).getName())) {
                            p.setFragmentVisible(false);
                        }
                    }
                    break;
                case PluginBase.TREATMENT:
                    activeTreatments = (TreatmentsInterface) getTheOneEnabledInArray(pluginsInCategory);
                    if (Config.logConfigBuilder)
                        log.debug("Selected treatment interface: " + ((PluginBase) activeTreatments).getName());
                    for (PluginBase p : pluginsInCategory) {
                        if (!p.getName().equals(((PluginBase) activeTreatments).getName())) {
                            p.setFragmentVisible(false);
                        }
                    }
                    break;
            }

        }
    }

    private PluginBase getTheOneEnabledInArray(ArrayList<PluginBase> pluginsInCategory) {
        PluginBase found = null;
        for (PluginBase p : pluginsInCategory) {
            if (p.isEnabled() && found == null) {
                found = p;
                continue;
            } else if (p.isEnabled()) {
                // set others disabled
                p.setFragmentEnabled(false);
            }
        }
        // If none enabled, enable first one
        if (found == null)
            found = pluginsInCategory.get(0);
        return found;
    }

    private void storeSettings() {
        if (Config.logPrefsChange)
            log.debug("Storing settings");
        SharedPreferences settings = MainApp.instance().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();

        for (PluginBase p : pluginList) {
            editor.putBoolean(p.getName() + "Enabled", p.isEnabled());
            editor.putBoolean(p.getName() + "Visible", p.isVisibleInTabs());
        }
        editor.commit();
        verifySelectionInCategories();
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");
        SharedPreferences settings = MainApp.instance().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
        for (PluginBase p : pluginList) {
            if (settings.contains(p.getName() + "Enabled"))
                p.setFragmentEnabled(settings.getBoolean(p.getName() + "Enabled", true));
            if (settings.contains(p.getName() + "Visible"))
                p.setFragmentVisible(settings.getBoolean(p.getName() + "Visible", true));
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
    public boolean isAutomaticProcessingEnabled() {
        boolean result = true;

        ArrayList<PluginBase> constraintsPlugins = MainActivity.getSpecificPluginsList(PluginBase.CONSTRAINTS);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled()) continue;
            result = result && constrain.isAutomaticProcessingEnabled();
        }
        return result;
    }

    @Override
    public boolean manualConfirmationNeeded() {
        boolean result = false;

        ArrayList<PluginBase> constraintsPlugins = MainActivity.getSpecificPluginsList(PluginBase.CONSTRAINTS);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled()) continue;
            result = result || constrain.manualConfirmationNeeded();
        }
        return result;
    }

    @Override
    public APSResult applyBasalConstraints(APSResult result) {
        ArrayList<PluginBase> constraintsPlugins = MainActivity.getSpecificPluginsList(PluginBase.CONSTRAINTS);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled()) continue;
            constrain.applyBasalConstraints(result);
        }
        return result;
    }

    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        Double rateAfterConstrain = absoluteRate;
        ArrayList<PluginBase> constraintsPlugins = MainActivity.getSpecificPluginsList(PluginBase.CONSTRAINTS);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled()) continue;
            rateAfterConstrain = constrain.applyBasalConstraints(rateAfterConstrain);
        }
        return rateAfterConstrain;
    }

    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        Integer rateAfterConstrain = percentRate;
        ArrayList<PluginBase> constraintsPlugins = MainActivity.getSpecificPluginsList(PluginBase.CONSTRAINTS);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled()) continue;
            rateAfterConstrain = constrain.applyBasalConstraints(rateAfterConstrain);
        }
        return rateAfterConstrain;
    }

    @Override
    public Double applyBolusConstraints(Double insulin) {
        Double insulinAfterConstrain = insulin;
        ArrayList<PluginBase> constraintsPlugins = MainActivity.getSpecificPluginsList(PluginBase.CONSTRAINTS);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled()) continue;
            insulinAfterConstrain = constrain.applyBolusConstraints(insulinAfterConstrain);
        }
        return insulinAfterConstrain;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        Integer carbsAfterConstrain = carbs;
        ArrayList<PluginBase> constraintsPlugins = MainActivity.getSpecificPluginsList(PluginBase.CONSTRAINTS);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled()) continue;
            carbsAfterConstrain = constrain.applyCarbsConstraints(carbsAfterConstrain);
        }
        return carbsAfterConstrain;
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
            List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            if (q.size() < 1) {
                log.error("DBADD No receivers");
            } else log.debug("DBADD dbAdd " + q.size() + " receivers " + data.toString());
        } catch (JSONException e) {
        }
    }

   public static void uploadTempBasalStartPercent(Integer percent, double durationInMinutes) {
        try {
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
            List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            if (q.size() < 1) {
                log.error("DBADD No receivers");
            } else log.debug("DBADD dbAdd " + q.size() + " receivers " + data.toString());
        } catch (JSONException e) {
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
            List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            if (q.size() < 1) {
                log.error("DBADD No receivers");
            } else log.debug("DBADD dbAdd " + q.size() + " receivers " + data.toString());
        } catch (JSONException e) {
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
            List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            if (q.size() < 1) {
                log.error("DBADD No receivers");
            } else log.debug("DBADD dbAdd " + q.size() + " receivers " + data.toString());
        } catch (JSONException e) {
        }
    }

}
