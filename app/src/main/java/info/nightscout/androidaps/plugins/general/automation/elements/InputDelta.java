package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.annotation.StringRes;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.NumberPicker;

public class InputDelta extends Element {
    private Comparator.Compare compare = Comparator.Compare.IS_EQUAL;

    public enum DeltaType {
        DELTA,
        SHORT_AVERAGE,
        LONG_AVERAGE;

        public @StringRes
        int getStringRes() {
            switch (this) {
                case DELTA:
                    return R.string.delta;
                case SHORT_AVERAGE:
                    return R.string.short_avgdelta;
                case LONG_AVERAGE:
                    return R.string.long_avgdelta;
                default:
                    return R.string.unknown;
            }
        }

        public static List<String> labels() {
            List<String> list = new ArrayList<>();
            for (DeltaType d : DeltaType.values()) {
                list.add(MainApp.gs(d.getStringRes()));
            }
            return list;
        }
    }

    private double value;
    double minValue;
    double maxValue;
    private double step;
    private DecimalFormat decimalFormat;
    private DeltaType deltaType;

    NumberPicker numberPicker;

    public InputDelta() {
        super();
    }

    public InputDelta(double value, double minValue, double maxValue, double step, DecimalFormat decimalFormat, DeltaType deltaType) {
        super();
        this.value = value;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.decimalFormat = decimalFormat;
        this.deltaType = deltaType;
    }

    public InputDelta(InputDelta another) {
        super();
        value = another.getValue();
        minValue = another.minValue;
        maxValue = another.maxValue;
        step = another.step;
        decimalFormat = another.decimalFormat;
        deltaType = another.deltaType;
    }


    @Override
    public void addToLayout(LinearLayout root) {
        Spinner spinner = new Spinner(root.getContext());
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(root.getContext(), R.layout.spinner_centered, DeltaType.labels());
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerArrayAdapter);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinnerParams.setMargins(0, MainApp.dpToPx(4), 0, MainApp.dpToPx(4));
        spinner.setLayoutParams(spinnerParams);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                deltaType = DeltaType.values()[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setSelection(this.deltaType.ordinal());
//        root.addView(spinner);
        numberPicker = new NumberPicker(root.getContext(), null);
        numberPicker.setParams(value, minValue, maxValue, step, decimalFormat, true, null, null);
        numberPicker.setOnValueChangedListener(value -> this.value = value);
        LinearLayout l = new LinearLayout(root.getContext());
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        l.addView(spinner);
        l.addView(numberPicker);
        root.addView(l);
    }

    public InputDelta setValue(double value, DeltaType type) {
        this.value = value;
        this.deltaType = type;
        if (numberPicker != null)
            numberPicker.setValue(value);
        return this;
    }

    public double getValue() {
        return value;
    }

    public DeltaType getDeltaType() {
        return deltaType;
    }

}