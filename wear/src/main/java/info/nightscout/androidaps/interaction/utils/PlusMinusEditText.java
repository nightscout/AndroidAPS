package info.nightscout.androidaps.interaction.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.InputDeviceCompat;
import androidx.core.view.MotionEventCompat;

import java.text.NumberFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by mike on 28.06.2016.
 */
public class PlusMinusEditText implements View.OnKeyListener,
        View.OnTouchListener, View.OnClickListener, View.OnGenericMotionListener {

    Integer editTextID;
    public TextView editText;
    ImageView minusImage;
    ImageView plusImage;

    Double value;
    Double minValue = 0d;
    Double maxValue = 1d;
    Double step = 1d;
    NumberFormat formatter;
    boolean allowZero = false;
    boolean roundRobin;

    private int mChangeCounter = 0;
    private long mLastChange = 0;
    private final static int THRESHOLD_COUNTER = 5;
    private final static int THRESHOLD_COUNTER_LONG = 10;
    private final static int THRESHOLD_TIME = 100;

    private final Handler mHandler;
    private ScheduledExecutorService mUpdater;

    private class UpdateCounterTask implements Runnable {
        private final boolean mInc;
        private int repeated = 0;
        private int multiplier = 1;

        private final int doubleLimit = 5;

        public UpdateCounterTask(boolean inc) {
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

    public PlusMinusEditText(View view, int editTextID, int plusID, int minusID, Double initValue, Double minValue, Double maxValue, Double step, NumberFormat formatter, boolean allowZero) {
        this(view, editTextID, plusID, minusID, initValue, minValue, maxValue, step, formatter, allowZero, false);
    }

    public PlusMinusEditText(View view, int editTextID, int plusID, int minusID, Double initValue, Double minValue, Double maxValue, Double step, NumberFormat formatter, boolean allowZero, boolean roundRobin) {
        editText = view.findViewById(editTextID);
        minusImage = view.findViewById(minusID);
        plusImage = view.findViewById(plusID);

        this.value = initValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.formatter = formatter;
        this.allowZero = allowZero;
        this.roundRobin = roundRobin;

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INC:
                        inc(msg.arg1);
                        return;
                    case MSG_DEC:
                        dec(msg.arg1);
                        return;
                }
                super.handleMessage(msg);
            }
        };

        minusImage.setOnTouchListener(this);
        minusImage.setOnKeyListener(this);
        minusImage.setOnClickListener(this);
        plusImage.setOnTouchListener(this);
        plusImage.setOnKeyListener(this);
        plusImage.setOnClickListener(this);
        editText.setOnGenericMotionListener(this);
        updateEditText();
    }

    public void setValue(Double value) {
        this.value = value;
        updateEditText();
    }

    public Double getValue() {
        return value;
    }

    public void setStep(Double step) {
        this.step = step;
    }

    private void inc(int multiplier) {
        value += step * multiplier;
        if (value > maxValue) {
            if (roundRobin) {
                value = minValue;
            } else {
                value = maxValue;
                stopUpdating();
            }
        }
        updateEditText();
    }

    private void dec(int multiplier) {
        value -= step * multiplier;
        if (value < minValue) {
            if (roundRobin) {
                value = maxValue;
            } else {
                value = minValue;
                stopUpdating();
            }
        }
        updateEditText();
    }

    private void updateEditText() {
        if (value == 0d && !allowZero)
            editText.setText("");
        else
            editText.setText(formatter.format(value));
    }

    private void startUpdating(boolean inc) {
        if (mUpdater != null) {
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
            if (v == plusImage) {
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
            startUpdating(v == plusImage);
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
            startUpdating(v == plusImage);
        }
        return false;
    }

    @Override
    public boolean onGenericMotion(View v, MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            long now = System.currentTimeMillis();
            if (now - mLastChange > THRESHOLD_TIME) mChangeCounter = 0;

            int dynamicMultiplier = mChangeCounter < THRESHOLD_COUNTER ? 1 :
                    mChangeCounter < THRESHOLD_COUNTER_LONG ? 2 : 4;

            float delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL);
            if (delta > 0) {
                inc(dynamicMultiplier);
            } else {
                dec(dynamicMultiplier);
            }
            mLastChange = System.currentTimeMillis();
            mChangeCounter++;
            return true;
        }
        return false;
    }

}
