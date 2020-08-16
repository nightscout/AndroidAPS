package info.nightscout.androidaps.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.ui.NumberPicker;

/**
 * Created by mike on 28.06.2016.
 */
public class NumberPickerVertical extends NumberPicker {
    public NumberPickerVertical(Context context) {
        super(context);
    }

    public NumberPickerVertical(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void inflate(Context context) {
        LayoutInflater.from(context).inflate(R.layout.number_picker_layout_vertical, this, true);
    }
 }
