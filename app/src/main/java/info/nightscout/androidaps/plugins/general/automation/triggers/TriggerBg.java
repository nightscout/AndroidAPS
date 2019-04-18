package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.content.Context;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.T;

public class TriggerBg extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    private double threshold;
    private Comparator comparator = Comparator.IS_EQUAL;
    private String units = ProfileFunctions.getInstance().getProfileUnits();
    private long lastRun;

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
            if (units.equals(Constants.MMOL)) {
                threshold = Math.max(threshold, 4d);
                threshold = Math.min(threshold, 15d);
            } else {
                threshold = Math.max(threshold, 72d);
                threshold = Math.min(threshold, 270d);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    public TriggerBg() {
        super();
        threshold = units.equals(Constants.MGDL) ? 100d : 5.5d;
    }

    private TriggerBg(TriggerBg triggerBg) {
        super();
        comparator = triggerBg.comparator;
        lastRun = triggerBg.lastRun;
        units = triggerBg.units;
        threshold = triggerBg.threshold;
    }

    public double getThreshold() {
        return threshold;
    }

    public Comparator getComparator() {
        return comparator;
    }

    public String getUnits() {
        return units;
    }

    @Override
    public synchronized boolean shouldRun() {
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

        if (glucoseStatus == null && comparator.equals(Comparator.IS_NOT_AVAILABLE))
            if (lastRun < DateUtil.now() - T.mins(5).msecs()) {
                if (L.isEnabled(L.AUTOMATION))
                    log.debug("Ready for execution: " + friendlyDescription());
                return true;
            }
        if (glucoseStatus == null)
            return false;

        return comparator.check(glucoseStatus.glucose, Profile.toMgdl(threshold, units));
    }

    @Override
    public synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerBg.class.getName());
            JSONObject data = new JSONObject();
            data.put("threshold", threshold);
            data.put("lastRun", lastRun);
            data.put("comparator", comparator.toString());
            data.put("units", units);
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
            units = JsonHelper.safeGetString(d, "units");
        } catch (JSONException e) {
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
        if (comparator.equals(Comparator.IS_NOT_AVAILABLE))
            return MainApp.gs(R.string.glucoseisnotavailable);
        else {
            return MainApp.gs(units.equals(Constants.MGDL) ? R.string.glucosecomparedmgdl : R.string.glucosecomparedmmol, MainApp.gs(comparator.getStringRes()), threshold, units);
        }
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_cp_bgcheck);
    }

    @Override
    public void executed(long time) {
    }

    @Override
    public Trigger duplicate() {
        return new TriggerBg(this);
    }

    TriggerBg threshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    TriggerBg lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerBg comparator(Comparator comparator) {
        this.comparator = comparator;
        return this;
    }

    TriggerBg units(String units) {
        this.units = units;
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
        double min = units.equals(Constants.MGDL) ? 3 * Constants.MMOLL_TO_MGDL : 3;
        double max = units.equals(Constants.MGDL) ? 20 * Constants.MMOLL_TO_MGDL : 20;
        double step = units.equals(Constants.MGDL) ? 1 : 0.1d;
        DecimalFormat pattern = units.equals(Constants.MGDL) ? new DecimalFormat("0") : new DecimalFormat("0.0");
        numberPicker.setParams(0d, min, max, step, pattern, false, textWatcher);
        numberPicker.setValue(threshold);
        numberPicker.setOnValueChangedListener(value -> threshold = value);
        layout.addView(numberPicker);

        // text view for unit
        TextView tvUnits = new TextView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        params.setMargins(MainApp.dpToPx(6), 0, 0, 0);
        tvUnits.setLayoutParams(params);
        tvUnits.setText(units);
        tvUnits.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(tvUnits);

        return root;
    }
}
