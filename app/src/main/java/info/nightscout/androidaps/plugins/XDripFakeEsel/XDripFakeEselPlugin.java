package info.nightscout.androidaps.plugins.XDripFakeEsel;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * Created by adrian on 13/12/17.
 */

public class XDripFakeEselPlugin implements PluginBase {

    private static Logger log = LoggerFactory.getLogger(XDripFakeEselPlugin.class);

    public static final String XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR";
    private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

    private boolean fragmentEnabled = false;

    private final Context ctx;

    private static XDripFakeEselPlugin thisPlugin;

    public static XDripFakeEselPlugin getPlugin() {
        return thisPlugin;
    }

    public static XDripFakeEselPlugin initPlugin(Context ctx) {

        if (thisPlugin == null) {
            thisPlugin = new XDripFakeEselPlugin(ctx);
        }

        return thisPlugin;
    }

    private XDripFakeEselPlugin(Context ctx) {
        this.ctx = ctx;
        MainApp.bus().register(this);
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return null;
    }

    @Override
    public String getName() {
        return "Fake ESEL for xDrip";
    }

    @Override
    public String getNameShort() {
        String name = "FakeESEL";
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == GENERAL && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return false;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return false;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) {
            this.fragmentEnabled = fragmentEnabled;
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        // do nothing, no gui
    }

    @Override
    public int getPreferencesId() {
        return -1;
    }


    private void sendBG() {
        if (!fragmentEnabled) {
            return;
        }
        try {

            final JSONArray entriesBody = new JSONArray();
            addLastSgvEntry(entriesBody);

            sendBundle("add", "entries", entriesBody, XDRIP_PLUS_NS_EMULATOR);
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        sendBG();
    }

    private static void sendBundle(String action, String collection, Object json, String intentIdAction) {
        final Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("collection", collection);
        bundle.putString("data", json.toString());
        final Intent intent = new Intent(intentIdAction);
        intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        MainApp.instance().sendBroadcast(intent);
        List<ResolveInfo> receivers = MainApp.instance().getPackageManager().queryBroadcastReceivers(intent, 0);
        if (receivers.size() < 1) {
            log.debug("No xDrip receivers found. ");
        } else {
            log.debug(receivers.size() + " xDrip receivers");
        }
    }

    private static void addLastSgvEntry(JSONArray entriesArray) throws Exception {
        JSONObject json = new JSONObject();
        BgReading bgReading = DatabaseHelper.lastBg();
        if(bgReading==null){
            log.debug("bgReading==null");
        }

        json.put("sgv", bgReading.value);
        if (bgReading.direction == null){
            json.put("direction", "NONE");
        } else {
            json.put("direction", bgReading.direction);
        }
        json.put("device", "ESEL");
        json.put("type", "sgv");
        json.put("date", bgReading.date);
        json.put("dateString", format.format(bgReading.date));

        entriesArray.put(json);
    }

    public boolean isEnabled() {
        return fragmentEnabled;
    }

}
