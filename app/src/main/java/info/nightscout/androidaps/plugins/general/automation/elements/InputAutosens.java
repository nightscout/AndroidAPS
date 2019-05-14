package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SP;

public class InputAutosens extends Element {

    private double value;
    double minValue = SP.getDouble("key_openapsama_autosens_min", 0.7d);
    double maxValue = SP.getDouble("key_openapsama_autosens_max", 1.2d);
    private double step = 0.01d;
    private DecimalFormat decimalFormat = new DecimalFormat("0.00");;

    NumberPicker numberPicker;
    final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
                value = Math.max(value, 0.7d);
                value = Math.min(value, 2d);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };




    public InputAutosens() {
        super();
    }

    public InputAutosens(double value, double minValue, double maxValue, double step, DecimalFormat decimalFormat) {
        super();
        this.value = value;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.decimalFormat = decimalFormat;
    }

    public InputAutosens(InputAutosens another) {
        super();
        value = another.getValue();
        minValue = another.minValue;
        maxValue = another.maxValue;
        step = another.step;
        decimalFormat = another.decimalFormat;
    }


    @Override
    public void addToLayout(LinearLayout root) {
        numberPicker = new NumberPicker(root.getContext(), null);
        numberPicker.setParams(value, minValue, maxValue, step, decimalFormat, true, textWatcher);
        numberPicker.setOnValueChangedListener(value -> this.value = value);
        root.addView(numberPicker);
    }

    public InputAutosens setValue(double value) {
        minValue = SP.getDouble("key_openapsama_autosens_min", 0.7d);
        maxValue = SP.getDouble("key_openapsama_autosens_max", 1.2d);
        if (value > maxValue)
            value = Math.max(value, this.maxValue);
        if (value < minValue)
            value = minValue;
        this.value = value;
        if (numberPicker != null)
            numberPicker.setValue(value);
        return this;
    }

    public double getValue() {
        return value;
    }

}
