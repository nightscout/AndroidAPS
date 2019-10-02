package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.widget.LinearLayout;

import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.general.automation.elements.InputBg;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerBg extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    private InputBg bg = new InputBg();
    private Comparator comparator = new Comparator();

    public TriggerBg() {
        super();
    }

    private TriggerBg(TriggerBg triggerBg) {
        super();
        bg = new InputBg(triggerBg.bg);
        comparator = new Comparator(triggerBg.comparator);
        lastRun = triggerBg.lastRun;
    }

    public double getValue() {
        return bg.getValue();
    }

    public Comparator getComparator() {
        return comparator;
    }

    public String getUnits() {
        return bg.getUnits();
    }

    public long getLastRun() {
        return lastRun;
    }

    @Override
    public synchronized boolean shouldRun() {
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

        if (lastRun > DateUtil.now() - T.mins(5).msecs()) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("NOT ready for execution: " + friendlyDescription());
            return false;
        }

        if (glucoseStatus == null && comparator.getValue().equals(Comparator.Compare.IS_NOT_AVAILABLE)) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: " + friendlyDescription());
            return true;
        }
        if (glucoseStatus == null) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("NOT ready for execution: " + friendlyDescription());
            return false;
        }

        boolean doRun = comparator.getValue().check(glucoseStatus.glucose, Profile.toMgdl(bg.getValue(), bg.getUnits()));
        if (doRun) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: " + friendlyDescription());
            return true;
        }

        if (L.isEnabled(L.AUTOMATION))
            log.debug("NOT ready for execution: " + friendlyDescription());
        return false;
    }

    @Override
    public synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerBg.class.getName());
            JSONObject data = new JSONObject();
            data.put("bg", bg.getValue());
            data.put("lastRun", lastRun);
            data.put("comparator", comparator.getValue().toString());
            data.put("units", bg.getUnits());
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
            bg.setUnits(JsonHelper.safeGetString(d, "units"));
            bg.setValue(JsonHelper.safeGetDouble(d, "bg"));
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
            comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.glucose;
    }

    @Override
    public String friendlyDescription() {
        if (comparator.getValue().equals(Comparator.Compare.IS_NOT_AVAILABLE))
            return MainApp.gs(R.string.glucoseisnotavailable);
        else {
            return MainApp.gs(bg.getUnits().equals(Constants.MGDL) ? R.string.glucosecomparedmgdl : R.string.glucosecomparedmmol, MainApp.gs(comparator.getValue().getStringRes()), bg.getValue(), bg.getUnits());
        }
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_cp_bgcheck);
    }

    @Override
    public Trigger duplicate() {
        return new TriggerBg(this);
    }

    TriggerBg setValue(double value) {
        bg.setValue(value);
        return this;
    }

    TriggerBg lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerBg comparator(Comparator.Compare compare) {
        this.comparator = new Comparator().setValue(compare);
        return this;
    }

    TriggerBg setUnits(String units) {
        bg.setUnits(units);
        return this;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        new LayoutBuilder()
                .add(new StaticLabel(R.string.glucose))
                .add(comparator)
                .add(new LabelWithElement(MainApp.gs(R.string.glucose_u, bg.getUnits()), "", bg))
                .build(root);
    }
}
