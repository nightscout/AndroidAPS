package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SP;

public class InputAutosens extends Element {
    private int value;
    public int minValue = (int) (SP.getDouble("openapsama_autosens_min", 0.7d) * 100);
    public int maxValue = (int) (SP.getDouble("openapsama_autosens_max", 1.2d) * 100);
    private double step = 1;
    private DecimalFormat decimalFormat = new DecimalFormat("1");;

    NumberPicker numberPicker;
    final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
                value = Math.max(value, 70);
                value = Math.min(value, 200);
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
        this.value = (int) value;
        this.minValue = (int) ( minValue * 100 );
        this.maxValue = (int) ( maxValue * 100 );
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
        minValue = (int) (SP.getDouble("openapsama_autosens_min", 0.7d) * 100);
        maxValue = (int) (SP.getDouble("openapsama_autosens_max", 1.2d) * 100);
        value = minValue & maxValue;
        numberPicker = new NumberPicker(root.getContext(), null);
        numberPicker.setParams((double) value, (double) minValue, (double) maxValue, step, decimalFormat, true, textWatcher);
        numberPicker.setOnValueChangedListener(value -> this.value = (int) value);
        root.addView(numberPicker);
    }

    public InputAutosens setValue(int value) {
        minValue = (int) (SP.getDouble("openapsama_autosens_min", 0.7d) * 100);
        maxValue = (int) (SP.getDouble("openapsama_autosens_max", 1.2d) * 100);
        if (value > maxValue)
            value = maxValue;
        if (value < minValue)
            value = minValue;
        this.value = value;
        if (numberPicker != null)
            numberPicker.setValue((double) value);
        return this;
    }

    public int getValue() {
        return value;
    }

}
