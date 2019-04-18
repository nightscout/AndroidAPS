package info.nightscout.androidaps.plugins.general.automation;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.automation.actions.Action;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged;
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.services.LocationService;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

public class AutomationPlugin extends PluginBase {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    private final String key_AUTOMATION_EVENTS = "AUTOMATION_EVENTS";
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
    List<String> executionLog = new ArrayList<>();

    private Handler loopHandler = new Handler();
    private Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            processActions();
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs());
        }
    };

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
        loadFromSP();
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs());
    }

    @Override
    protected void onStop() {
        loopHandler.removeCallbacks(refreshLoop);
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

    private void storeToSP() {
        JSONArray array = new JSONArray();
        try {
            for (AutomationEvent event : getAutomationEvents()) {
                array.put(new JSONObject(event.toJSON()));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        SP.putString(key_AUTOMATION_EVENTS, array.toString());
    }

    private void loadFromSP() {
        automationEvents.clear();
        String data = SP.getString(key_AUTOMATION_EVENTS, "");
        if (!data.equals("")) {
            try {
                JSONArray array = new JSONArray(data);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    AutomationEvent event = new AutomationEvent().fromJSON(o.toString());
                    automationEvents.add(event);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

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
    public void onEvent(EventAutomationDataChanged e) {
        storeToSP();
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

    synchronized void processActions() {
        if (L.isEnabled(L.AUTOMATION))
            log.debug("processActions");
        for (AutomationEvent event : getAutomationEvents()) {
            if (event.getTrigger().shouldRun()) {
                List<Action> actions = event.getActions();
                for (Action action : actions) {
                    action.doAction(new Callback() {
                        @Override
                        public void run() {
                            StringBuilder sb = new StringBuilder();
                            sb.append(DateUtil.timeString(DateUtil.now()));
                            sb.append(" ");
                            sb.append(result.success ? "☺" : "X");
                            sb.append(" ");
                            sb.append(event.getTitle());
                            sb.append(": ");
                            sb.append(action.shortDescription());
                            sb.append(": ");
                            sb.append(result.comment);
                            executionLog.add(sb.toString());
                            if (L.isEnabled(L.AUTOMATION))
                                log.debug("Executed: " + sb.toString());
                            MainApp.bus().post(new EventAutomationUpdateGui());
                        }
                    });
                }
                event.getTrigger().executed(DateUtil.now());
            }
        }
        storeToSP(); // save last run time
    }
}
