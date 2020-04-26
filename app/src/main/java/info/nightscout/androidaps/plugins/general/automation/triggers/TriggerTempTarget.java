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
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerTempTarget extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    private ComparatorExists comparator = new ComparatorExists();

    public TriggerTempTarget() {
        super();
    }

    private TriggerTempTarget(TriggerTempTarget triggerTempTarget) {
        super();
        comparator = new ComparatorExists(triggerTempTarget.comparator);
        lastRun = triggerTempTarget.lastRun;
    }

    public ComparatorExists getComparator() {
        return comparator;
    }

    @Override
    public synchronized boolean shouldRun() {
        TempTarget tt = TreatmentsPlugin.getPlugin().getTempTargetFromHistory();

        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        if (tt == null && comparator.getValue() == ComparatorExists.Compare.NOT_EXISTS) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: " + friendlyDescription());
            return true;
        }

        if (tt != null && comparator.getValue() == ComparatorExists.Compare.EXISTS) {
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
            o.put("type", TriggerTempTarget.class.getName());
            JSONObject data = new JSONObject();
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
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
            comparator.setValue(ComparatorExists.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")));
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.careportal_temporarytarget;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.temptargetcompared, MainApp.gs(comparator.getValue().getStringRes()));
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_keyboard_tab);
    }

    @Override
    public Trigger duplicate() {
        return new TriggerTempTarget(this);
    }

    TriggerTempTarget lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    public TriggerTempTarget comparator(ComparatorExists.Compare compare) {
        this.comparator = new ComparatorExists().setValue(compare);
        return this;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        new LayoutBuilder()
                .add(new StaticLabel(R.string.careportal_temporarytarget))
                .add(comparator)
                .build(root);
    }
}
