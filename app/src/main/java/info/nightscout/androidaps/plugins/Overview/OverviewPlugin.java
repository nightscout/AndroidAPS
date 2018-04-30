package info.nightscout.androidaps.plugins.Overview;

import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.QuickWizard;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.NotificationStore;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class OverviewPlugin extends PluginBase {
    private static Logger log = LoggerFactory.getLogger(OverviewPlugin.class);

    private static OverviewPlugin overviewPlugin = new OverviewPlugin();

    public static OverviewPlugin getPlugin() {
        if (overviewPlugin == null)
            overviewPlugin = new OverviewPlugin();
        return overviewPlugin;
    }

    public static double bgTargetLow = 80d;
    public static double bgTargetHigh = 180d;

    public QuickWizard quickWizard = new QuickWizard();

    public NotificationStore notificationStore = new NotificationStore();

    public OverviewPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(OverviewFragment.class.getName())
                .alwayVisible(true)
                .alwaysEnabled(true)
                .pluginName(R.string.overview)
                .shortName(R.string.overview_shortname)
                .preferencesId(R.xml.pref_overview)
        );
        String storedData = SP.getString("QuickWizard", "[]");
        try {
            quickWizard.setData(new JSONArray(storedData));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
    }

    @Subscribe
    public void onStatusEvent(final EventNewNotification n) {
        if (notificationStore.add(n.notification))
            MainApp.bus().post(new EventRefreshOverview("EventNewNotification"));
    }

    @Subscribe
    public void onStatusEvent(final EventDismissNotification n) {
        if (notificationStore.remove(n.id))
            MainApp.bus().post(new EventRefreshOverview("EventDismissNotification"));
    }

    public double determineHighLine(String units) {
        double highLineSetting = SP.getDouble("high_mark", Profile.fromMgdlToUnits(OverviewPlugin.bgTargetHigh, units));
        if (highLineSetting < 1)
            highLineSetting = Profile.fromMgdlToUnits(180d, units);
        return highLineSetting;
    }

    public double determineLowLine() {
        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile == null) {
            return bgTargetLow;
        }
        return determineLowLine(profile.getUnits());
    }

    public double determineLowLine(String units) {
        double lowLineSetting = SP.getDouble("low_mark", Profile.fromMgdlToUnits(OverviewPlugin.bgTargetLow, units));
        if (lowLineSetting < 1)
            lowLineSetting = Profile.fromMgdlToUnits(76d, units);
        return lowLineSetting;
    }

}
