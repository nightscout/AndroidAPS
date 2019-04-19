package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.content.Context;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.T;

public class TriggerIob extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    private double threshold;
    private Comparator comparator = Comparator.IS_EQUAL;
    private long lastRun;

    final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
            threshold = Math.max(threshold, -20d);
            threshold = Math.min(threshold, 20d);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    public TriggerIob() {
        super();
    }

    TriggerIob(TriggerIob triggerIob) {
        super();
        comparator = triggerIob.comparator;
        lastRun = triggerIob.lastRun;
        threshold = triggerIob.threshold;
    }

    public double getThreshold() {
        return threshold;
    }

    public Comparator getComparator() {
        return comparator;
    }

    public long getLastRun() {
        return lastRun;
    }

    @Override
    public synchronized boolean shouldRun() {
        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null)
            return false;
        IobTotal iob = IobCobCalculatorPlugin.getPlugin().calculateFromTreatmentsAndTempsSynchronized(DateUtil.now(), profile);

        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        boolean doRun = comparator.check(iob.iob, threshold);
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
            o.put("type", TriggerIob.class.getName());
            JSONObject data = new JSONObject();
            data.put("threshold", threshold);
            data.put("lastRun", lastRun);
            data.put("comparator", comparator.toString());
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
            threshold = JsonHelper.safeGetDouble(d, "threshold");
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
            comparator = Comparator.valueOf(JsonHelper.safeGetString(d, "comparator"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.iob;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.iobcompared, MainApp.gs(comparator.getStringRes()), threshold);
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.remove); // TODO icon
    }

    @Override
    public void executed(long time) {
        lastRun = time;
    }

    @Override
    public Trigger duplicate() {
        return new TriggerIob(this);
    }

    TriggerIob threshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    TriggerIob lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerIob comparator(Comparator comparator) {
        this.comparator = comparator;
        return this;
    }

    @Override
    public View createView(Context context, FragmentManager fragmentManager) {
        LinearLayout root = (LinearLayout) super.createView(context, fragmentManager);

        // spinner for comparator
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, Comparator.labels());
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerArrayAdapter);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinnerParams.setMargins(0, MainApp.dpToPx(4), 0, MainApp.dpToPx(4));
        spinner.setLayoutParams(spinnerParams);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                comparator = Comparator.values()[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setSelection(comparator.ordinal());
        root.addView(spinner);

        // horizontal layout
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(layout);

        // input field for threshold
        NumberPicker numberPicker = new NumberPicker(context, null);
        numberPicker.setParams(0d, -20d, 20d, 0.1d, new DecimalFormat("0.0"), true, textWatcher);
        numberPicker.setValue(threshold);
        numberPicker.setOnValueChangedListener(value -> threshold = value);
        layout.addView(numberPicker);

        return root;
    }
}
