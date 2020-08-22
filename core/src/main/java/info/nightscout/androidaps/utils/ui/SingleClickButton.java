package info.nightscout.androidaps.utils.ui;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.StacktraceLoggerWrapper;

/**
 * Created by mike on 22.12.2017.
 */

public class SingleClickButton extends com.google.android.material.button.MaterialButton implements View.OnClickListener {
    final static Logger log = LoggerFactory.getLogger(SingleClickButton.class);

    Context context;
    OnClickListener listener = null;

    public SingleClickButton(Context context) {
        super(context);
        this.context = context;
        super.setOnClickListener(this);
    }

    public SingleClickButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        super.setOnClickListener(this);
    }

    public SingleClickButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        super.setOnClickListener(this);
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        listener = l;
    }

    @Override
    public void onClick(final View v) {
        setEnabled(false);
        new Thread(() -> {
            SystemClock.sleep(1500);
            Activity activity = (Activity) context;
            if (activity != null)
                activity.runOnUiThread(() -> {
                    setEnabled(true);
                    log.debug("Button enabled");
                });
        }).start();
        if (listener != null)
            listener.onClick(v);
    }
}
