package info.nightscout.androidaps.utils.ui;

import android.app.Service;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.text.NumberFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.ToastUtils;

public class NumberPicker extends LinearLayout implements View.OnKeyListener,
        View.OnTouchListener, View.OnClickListener {

    public interface OnValueChangedListener {
        void onValueChanged(double value);
    }

    EditText editText;
    Button minusButton;
    Button plusButton;

    double value = 0;
    double minValue = 0d;
    double maxValue = 1d;
    double step = 1d;
    NumberFormat formatter;
    boolean allowZero = false;
    TextWatcher textWatcher = null;

    Button okButton = null;

    protected Boolean focused = false;

    private Handler mHandler;
    private ScheduledExecutorService mUpdater;
    private OnValueChangedListener mOnValueChangedListener;

    private class UpdateCounterTask implements Runnable {
        private final boolean mInc;
        private int repeated = 0;
        private int multiplier = 1;

        private final int doubleLimit = 5;

        UpdateCounterTask(boolean inc) {
            mInc = inc;
        }

        public void run() {
            Message msg = new Message();
            if (repeated % doubleLimit == 0) multiplier *= 2;
            repeated++;
            msg.arg1 = multiplier;
            msg.arg2 = repeated;
            if (mInc) {
                msg.what = MSG_INC;
            } else {
                msg.what = MSG_DEC;
            }
            mHandler.sendMessage(msg);
        }
    }

    private static final int MSG_INC = 0;
    private static final int MSG_DEC = 1;

    public NumberPicker(Context context) {
        super(context, null);
        this.initialize(context);
    }

    public NumberPicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.initialize(context);
    }

    protected void inflate(Context context) {
        LayoutInflater.from(context).inflate(R.layout.number_picker_layout, this, true);
    }

    protected void initialize(Context context) {
        // set layout view
        inflate(context);

        // init ui components
        minusButton = findViewById(R.id.decrement);
        minusButton.setId(View.generateViewId());
        plusButton = findViewById(R.id.increment);
        plusButton.setId(View.generateViewId());
        editText = findViewById(R.id.display);
        editText.setId(View.generateViewId());

        mHandler = new Handler(msg -> {
            switch (msg.what) {
                case MSG_INC:
                    inc(msg.arg1);
                    return true;
                case MSG_DEC:
                    dec(msg.arg1);
                    return true;
            }
            return false;
        });

        minusButton.setOnTouchListener(this);
        minusButton.setOnKeyListener(this);
        minusButton.setOnClickListener(this);
        plusButton.setOnTouchListener(this);
        plusButton.setOnKeyListener(this);
        plusButton.setOnClickListener(this);
        setTextWatcher(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (focused) value = SafeParse.stringToDouble(editText.getText().toString());
                callValueChangedListener();
                if (okButton != null) {
                    if (value > maxValue || value < minValue)
                        okButton.setVisibility(INVISIBLE);
                    else
                        okButton.setVisibility(VISIBLE);
                }
            }
        });

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            focused = hasFocus;
            if (!focused) getValue(); // check min/max
            updateEditText();
        });
    }

    @Override
    public void setTag(Object tag) {
        editText.setTag(tag);
    }

    public void setOnValueChangedListener(OnValueChangedListener onValueChangedListener) {
        mOnValueChangedListener = onValueChangedListener;
    }

    public void setTextWatcher(TextWatcher textWatcher) {
        this.textWatcher = textWatcher;
        editText.addTextChangedListener(textWatcher);
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                value = SafeParse.stringToDouble(editText.getText().toString());
                if (value > maxValue) {
                    value = maxValue;
                    ToastUtils.showToastInUiThread(getContext(), getContext().getString(R.string.youareonallowedlimit));
                    updateEditText();
                    if (okButton != null)
                        okButton.setVisibility(VISIBLE);
                }
                if (value < minValue) {
                    value = minValue;
                    ToastUtils.showToastInUiThread(getContext(), getContext().getString(R.string.youareonallowedlimit));
                    updateEditText();
                    if (okButton != null)
                        okButton.setVisibility(VISIBLE);
                }
            }
        });
    }

    public void setParams(Double initValue, Double minValue, Double maxValue, Double step, NumberFormat formater, boolean allowZero, Button okButton, TextWatcher textWatcher) {
        if (this.textWatcher != null) {
            editText.removeTextChangedListener(this.textWatcher);
        }
        setParams(initValue, minValue, maxValue, step, formater, allowZero, okButton);
        this.textWatcher = textWatcher;
        if (textWatcher != null)
            editText.addTextChangedListener(textWatcher);
    }

    public void setParams(Double initValue, Double minValue, Double maxValue, Double step, NumberFormat formatter, boolean allowZero, Button okButton) {
        this.value = initValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.formatter = formatter;
        this.allowZero = allowZero;
        callValueChangedListener();
        this.okButton = okButton;

        editText.setKeyListener(DigitsKeyListenerWithComma.getInstance(minValue < 0, step != Math.rint(step)));

        if (textWatcher != null)
            editText.removeTextChangedListener(textWatcher);
        updateEditText();
        if (textWatcher != null)
            editText.addTextChangedListener(textWatcher);
    }

    public void setValue(Double value) {
        if (textWatcher != null)
            editText.removeTextChangedListener(textWatcher);
        this.value = value;
        callValueChangedListener();
        updateEditText();
        if (textWatcher != null)
            editText.addTextChangedListener(textWatcher);
    }

    public Double getValue() {
        if (value > maxValue) {
            value = maxValue;
            ToastUtils.showToastInUiThread(getContext(), getContext().getString(R.string.youareonallowedlimit));
        }
        if (value < minValue) {
            value = minValue;
            ToastUtils.showToastInUiThread(getContext(), getContext().getString(R.string.youareonallowedlimit));
        }
        return value;
    }

    public String getText() {
        return editText.getText().toString();
    }

    public void setStep(Double step) {
        this.step = step;
    }

    private void inc(int multiplier) {
        value += step * multiplier;
        if (value > maxValue) {
            value = maxValue;
            callValueChangedListener();
            ToastUtils.showToastInUiThread(getContext(), getContext().getString(R.string.youareonallowedlimit));
            stopUpdating();
        }
        updateEditText();
    }

    private void dec(int multiplier) {
        value -= step * multiplier;
        if (value < minValue) {
            value = minValue;
            callValueChangedListener();
            ToastUtils.showToastInUiThread(getContext(), getContext().getString(R.string.youareonallowedlimit));
            stopUpdating();
        }
        updateEditText();
    }

    protected void updateEditText() {
        if (value == 0d && !allowZero)
            editText.setText("");
        else
            editText.setText(formatter.format(value));
    }

    private void callValueChangedListener() {
        if (mOnValueChangedListener != null)
            mOnValueChangedListener.onValueChanged(value);
    }

    private void startUpdating(boolean inc) {
        if (mUpdater != null) {
            //log.debug("Another executor is still active");
            return;
        }
        mUpdater = Executors.newSingleThreadScheduledExecutor();
        mUpdater.scheduleAtFixedRate(new UpdateCounterTask(inc), 200, 200,
                TimeUnit.MILLISECONDS);
    }

    private void stopUpdating() {
        if (mUpdater != null) {
            mUpdater.shutdownNow();
            mUpdater = null;
        }
    }

    @Override
    public void onClick(View v) {
        if (mUpdater == null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Service.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
            editText.clearFocus();
            if (v == plusButton) {
                inc(1);
            } else {
                dec(1);
            }
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        boolean isKeyOfInterest = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
        boolean isReleased = event.getAction() == KeyEvent.ACTION_UP;
        boolean isPressed = event.getAction() == KeyEvent.ACTION_DOWN
                && event.getAction() != KeyEvent.ACTION_MULTIPLE;

        if (isKeyOfInterest && isReleased) {
            stopUpdating();
        } else if (isKeyOfInterest && isPressed) {
            startUpdating(v == plusButton);
        }
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean isReleased = event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL;
        boolean isPressed = event.getAction() == MotionEvent.ACTION_DOWN;

        if (isReleased) {
            stopUpdating();
        } else if (isPressed) {
            startUpdating(v == plusButton);
        }
        return false;
    }

}
