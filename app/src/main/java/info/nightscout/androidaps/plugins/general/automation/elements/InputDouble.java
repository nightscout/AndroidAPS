package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.androidaps.utils.NumberPicker;

public class InputDouble extends Element {

    final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    private double value;
    private double minValue;
    private double maxValue;
    private double step;
    private DecimalFormat decimalFormat;

    NumberPicker numberPicker;

    public InputDouble() {
        super();
    }

    public InputDouble(double value, double minValue, double maxValue, double step, DecimalFormat decimalFormat) {
        super();
        this.value = value;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.decimalFormat = decimalFormat;
    }

    public InputDouble(InputDouble another) {
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
        numberPicker.setParams(value, minValue, maxValue, step, decimalFormat, true, null, textWatcher);
        numberPicker.setOnValueChangedListener(value -> this.value = value);
        root.addView(numberPicker);
    }

    public InputDouble setValue(double value) {
        this.value = value;
        if (numberPicker != null)
            numberPicker.setValue(value);
        return this;
    }

    public double getValue() {
        return value;
    }

}
