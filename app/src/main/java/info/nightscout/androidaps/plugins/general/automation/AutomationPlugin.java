package info.nightscout.androidaps.plugins.general.automation;

import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventChargingState;
import info.nightscout.androidaps.events.EventLocationChange;
import info.nightscout.androidaps.events.EventNetworkChange;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.services.LocationService;

public class AutomationPlugin extends PluginBase {

    static AutomationPlugin plugin = null;

    public static AutomationPlugin getPlugin() {
        if (plugin == null)
            plugin = new AutomationPlugin();
        return plugin;
    }

    private final List<AutomationEvent> automationEvents = new ArrayList<>();
    private EventLocationChange eventLocationChange;
    private EventChargingState eventChargingState;
    private EventNetworkChange eventNetworkChange;

    private AutomationPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(AutomationFragment.class.getName())
                .pluginName(R.string.automation)
                .shortName(R.string.automation_short)
                .preferencesId(R.xml.pref_automation)
                .description(R.string.automation_description)
        );
    }

    @Override
    protected void onStart() {
        Context context = MainApp.instance().getApplicationContext();
        context.startService(new Intent(context, LocationService.class));

        MainApp.bus().register(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        Context context = MainApp.instance().getApplicationContext();
        context.stopService(new Intent(context, LocationService.class));

        MainApp.bus().unregister(this);
    }

    public List<AutomationEvent> getAutomationEvents() {
        return automationEvents;
    }

    public EventLocationChange getEventLocationChange() {
        return eventLocationChange;
    }

    public EventChargingState getEventChargingState() {
        return eventChargingState;
    }

    public EventNetworkChange getEventNetworkChange() {
        return eventNetworkChange;
    }

    @Subscribe
    public void onEventPreferenceChange(EventPreferenceChange e) {
        if (e.isChanged(R.string.key_location)) {
            Context context = MainApp.instance().getApplicationContext();
            context.stopService(new Intent(context, LocationService.class));
            context.startService(new Intent(context, LocationService.class));
        }
    }

    @Subscribe
    public void onEventLocationChange(EventLocationChange e) {
        eventLocationChange = e;
        processActions();
    }

    @Subscribe
    public void onEventChargingState(EventChargingState e) {
        eventChargingState = e;
        processActions();
    }

    @Subscribe
    public void onEventNetworkChange(EventNetworkChange e) {
        eventNetworkChange = e;
        processActions();
    }

    @Subscribe
    public void onEventAutosensCalculationFinished(EventAutosensCalculationFinished e) {
        processActions();
    }

    // TODO keepalive

    void processActions() {

    }
}
