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
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.general.automation.elements.InputPercent;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerProfilePercent extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    private InputPercent pct = new InputPercent();
    private Comparator comparator = new Comparator();

    public TriggerProfilePercent() {
        super();
    }

    private TriggerProfilePercent(TriggerProfilePercent triggerProfilePercent) {
        super();
        pct = new InputPercent(triggerProfilePercent.pct);
        comparator = new Comparator(triggerProfilePercent.comparator);
        lastRun = triggerProfilePercent.lastRun;
    }

    public double getValue() {
        return pct.getValue();
    }

    public Comparator getComparator() {
        return comparator;
    }

    public long getLastRun() {
        return lastRun;
    }

    @Override
    public synchronized boolean shouldRun() {
        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null && comparator.getValue().equals(Comparator.Compare.IS_NOT_AVAILABLE)) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: " + friendlyDescription());
            return true;
        }
        if (profile == null)
            return false;

        boolean doRun = comparator.getValue().check((double) profile.getPercentage(), pct.getValue());
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
            o.put("type", TriggerProfilePercent.class.getName());
            JSONObject data = new JSONObject();
            data.put("percentage", pct.getValue());
            data.put("lastRun", lastRun);
            data.put("comparator", comparator.getValue().toString());
            o.put("data", data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return o.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            pct.setValue(JsonHelper.safeGetDouble(d, "percentage"));
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
            comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")));
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.profilepercentage;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.percentagecompared, MainApp.gs(comparator.getValue().getStringRes()), (int) pct.getValue());
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_actions_profileswitch);
    }

    @Override
    public Trigger duplicate() {
        return new TriggerProfilePercent(this);
    }

    public TriggerProfilePercent setValue(double value) {
        pct.setValue(value);
        return this;
    }

    TriggerProfilePercent lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    public TriggerProfilePercent comparator(Comparator.Compare compare) {
        this.comparator = new Comparator().setValue(compare);
        return this;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        new LayoutBuilder()
                .add(new StaticLabel(R.string.profilepercentage))
                .add(comparator)
                .add(new LabelWithElement(MainApp.gs(R.string.percent_u), "", pct))
                .build(root);
    }
}
