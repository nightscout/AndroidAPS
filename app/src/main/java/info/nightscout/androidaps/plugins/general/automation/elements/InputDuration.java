package info.nightscout.androidaps.plugins.general.automation.elements;

import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.androidaps.utils.NumberPicker;

public class InputDuration extends Element {
    public enum TimeUnit {
        MINUTES,
        HOURS
    }

    private TimeUnit unit;
    private int value;

    public InputDuration(int value, TimeUnit unit) {
        this.unit = unit;
        this.value = value;
    }

    public InputDuration(InputDuration another) {
        unit =  another.unit;
        value = another.value;
    }

    @Override
    public void addToLayout(LinearLayout root) {
        NumberPicker numberPicker = new NumberPicker(root.getContext(), null);
        if (unit.equals(TimeUnit.MINUTES)) {
            // Minutes
            numberPicker.setParams(0d, 0d, 24 * 60d, 10d, new DecimalFormat("0"), false, null);
        } else {
            // Hours
            numberPicker.setParams(0d, 0d, 24d, 1d, new DecimalFormat("0"), false, null);
        }
        numberPicker.setValue((double) value);
        numberPicker.setOnValueChangedListener(value -> this.value = (int) value);
        root.addView(numberPicker);
    }

    TimeUnit getUnit() {
        return unit;
    }

    public double getValue() {
        return value;
    }

    public void setMinutes(int value) {
        if (unit.equals(TimeUnit.MINUTES)) {
            this.value = value;
        } else {
            this.value = value / 60;
        }
    }

    public int getMinutes() {
        if (unit.equals(TimeUnit.MINUTES)) {
            return value;
        } else {
            return value * 60;
        }
    }
}
