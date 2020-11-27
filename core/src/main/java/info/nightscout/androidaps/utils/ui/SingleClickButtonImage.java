package info.nightscout.androidaps.utils.ui;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import org.slf4j.Logger;

import info.nightscout.androidaps.logging.StacktraceLoggerWrapper;

/**
 * Created by mike on 22.12.2017.
 */

public class SingleClickButtonImage extends androidx.appcompat.widget.AppCompatImageButton implements View.OnClickListener {
    private static Logger log = StacktraceLoggerWrapper.getLogger(SingleClickButtonImage.class);

    Context context;
    OnClickListener listener = null;

    public SingleClickButtonImage(Context context) {
        super(context);
        this.context = context;
        super.setOnClickListener(this);
    }

    public SingleClickButtonImage(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        super.setOnClickListener(this);
    }

    public SingleClickButtonImage(Context context, AttributeSet attrs, int defStyleAttr) {
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(3000);
                Activity activity = (Activity) context;
                if (activity != null)
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setEnabled(true);
                            log.debug("Button enabled");
                        }
                    });
            }
        }).start();
        if (listener != null)
            listener.onClick(v);
    }
}
