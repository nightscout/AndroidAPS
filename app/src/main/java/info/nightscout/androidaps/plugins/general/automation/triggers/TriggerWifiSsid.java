package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.widget.LinearLayout;

import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventNetworkChange;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.general.automation.elements.InputString;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel;
import info.nightscout.androidaps.receivers.NetworkChangeReceiver;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerWifiSsid extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    private InputString ssid = new InputString();
    private Comparator comparator = new Comparator();

    public TriggerWifiSsid() {
        super();
    }

    private TriggerWifiSsid(TriggerWifiSsid triggerWifiSsid) {
        super();
        ssid = new InputString(triggerWifiSsid.ssid);
        comparator = new Comparator(triggerWifiSsid.comparator);
        lastRun = triggerWifiSsid.lastRun;
    }

    public String getValue() {
        return ssid.getValue();
    }

    public Comparator getComparator() {
        return comparator;
    }

    @Override
    public synchronized boolean shouldRun() {
        EventNetworkChange eventNetworkChange = NetworkChangeReceiver.getLastEvent();
        if (eventNetworkChange == null)
            return false;

        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        if (!eventNetworkChange.getWifiConnected() && comparator.getValue() == Comparator.Compare.IS_NOT_AVAILABLE) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: " + friendlyDescription());
            return true;
        }

        boolean doRun = eventNetworkChange.getWifiConnected() && comparator.getValue().check(eventNetworkChange.connectedSsid(), getValue());
        if (doRun) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: " + friendlyDescription());
            return true;
        }
        return false;
    }

    @Override
    public synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerWifiSsid.class.getName());
            JSONObject data = new JSONObject();
            data.put("ssid", getValue());
            data.put("lastRun", lastRun);
            data.put("comparator", comparator.getValue().toString());
            o.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            ssid.setValue(JsonHelper.safeGetString(d, "ssid"));
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
            comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.ns_wifi_ssids;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.wifissidcompared, MainApp.gs(comparator.getValue().getStringRes()), getValue());
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_network_wifi);
    }

    @Override
    public Trigger duplicate() {
        return new TriggerWifiSsid(this);
    }

    TriggerWifiSsid setValue(String value) {
        ssid.setValue(value);
        return this;
    }

    TriggerWifiSsid lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerWifiSsid comparator(Comparator.Compare compare) {
        this.comparator = new Comparator().setValue(compare);
        return this;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        new LayoutBuilder()
                .add(new StaticLabel(R.string.ns_wifi_ssids))
                .add(comparator)
                .add(new LabelWithElement(MainApp.gs(R.string.ns_wifi_ssids) + ": ", "", ssid))
                .build(root);
    }
}
