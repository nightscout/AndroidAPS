package info.nightscout.androidaps.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 28.06.2016.
 */
public class NumberPickerVertical extends NumberPicker {
    private static Logger log = LoggerFactory.getLogger(NumberPickerVertical.class);

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
