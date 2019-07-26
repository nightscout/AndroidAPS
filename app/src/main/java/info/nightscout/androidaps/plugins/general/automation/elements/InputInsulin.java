package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.androidaps.utils.NumberPicker;

public class InputInsulin extends Element {

    final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
            value = Math.max(value, -20d);
            value = Math.min(value, 20d);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    private double value;

    public InputInsulin() {
        super();
    }

    public InputInsulin(InputInsulin another) {
        super();
        value = another.getValue();
    }

    @Override
    public void addToLayout(LinearLayout root) {
        NumberPicker numberPicker = new NumberPicker(root.getContext(), null);
        numberPicker.setParams(0d, -20d, 20d, 0.1, new DecimalFormat("0.0"), true, null, textWatcher);
        numberPicker.setValue(value);
        numberPicker.setOnValueChangedListener(value -> this.value = value);
        root.addView(numberPicker);
    }

    public double getValue() {
        return value;
    }

    public InputInsulin setValue(double value) {
        this.value = value;
        return this;
    }
}
