package info.nightscout.androidaps.Services;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class AlertMessage {
    private static Logger log = LoggerFactory.getLogger(AlertMessage.class);

    private static boolean displayed = false;

    private WindowManager mWindowManager;
    private LinearLayout mLinLayout;
    private WindowManager.LayoutParams mLinLayoutParams;
    private TextView mFloatingTextView;
    private Context mApplicationContext;
    private Button mButtonDismis;

    private static SoundPool mSoundPool;
    private static int mSoundID;
    private static int mPlayingId;
    private Runnable mOnDismiss;
    private String mAlertText = "Alarm";
    PowerManager.WakeLock mWakeLock;

    static {
        mSoundPool = new SoundPool(1, AudioManager.STREAM_ALARM, 0);
        mSoundID = mSoundPool.load(MainApp.instance().getApplicationContext(), R.raw.beep_beep, 1);
    }

    public AlertMessage(Context mApplicationContext) {
        this.mApplicationContext = mApplicationContext;
        PowerManager powerManager = (PowerManager) mApplicationContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "AlertMessage");
    }

    public void showMessage() {
        log.debug("showMessage() displayed:" + displayed);
        if (displayed) {
            return;
        }
        displayed = true;
        mWakeLock.acquire();

        mPlayingId = 0;
        int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        float volume = (hourOfDay > 11 && hourOfDay < 23) ? 0.03f : 1.0f;
        do {
            mPlayingId = mSoundPool.play(mSoundID, volume, volume, 0, -1, 1f);
            log.debug("mSoundPool.play returned " + mPlayingId);
            if (mPlayingId == 0) {
                try {
                    synchronized (this) {
                        this.wait(100);
                    }
                } catch (InterruptedException e) {
                }
            }
        } while (mPlayingId == 0);

        if (mWindowManager == null)
            mWindowManager = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if (mLinLayout != null)
            mWindowManager.removeViewImmediate(mLinLayout);

        mLinLayout = new LinearLayout(getApplicationContext());
        mLinLayoutParams = new WindowManager.LayoutParams();

        mLinLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        mLinLayoutParams.format = 1;
        mLinLayoutParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN;
        mLinLayoutParams.width = 500;
        mLinLayoutParams.height = 200;
        mLinLayoutParams.gravity = Gravity.CENTER;
        mLinLayout.setBackgroundColor(Color.argb(220, 255, 0, 0));
        mWindowManager.addView(mLinLayout, mLinLayoutParams);
        mLinLayout.setOrientation(LinearLayout.VERTICAL);
        mFloatingTextView = new TextView(getApplicationContext());
        mLinLayout.addView(mFloatingTextView);

        mFloatingTextView.setText(mAlertText);
        mFloatingTextView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        mFloatingTextView.setTextSize(24.0F);
        mFloatingTextView.setGravity(Gravity.CENTER);
        mFloatingTextView.setTextColor(Color.WHITE);

        mButtonDismis = new Button(getApplicationContext());

        mButtonDismis.setText(MainApp.resources.getString(R.string.dismiss));
        mButtonDismis.setTextSize(20.0F);
        mButtonDismis.setTextAlignment(Button.TEXT_ALIGNMENT_CENTER);
        mButtonDismis.setGravity(Gravity.CENTER);
        mButtonDismis.setPadding(30, 20, 30, 20);

        mLinLayout.addView(mButtonDismis);

        mButtonDismis.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertMessage.this.dismis();
                if (mOnDismiss != null) {
                    mOnDismiss.run();
                }
            }
        });
    }

    public void dismis() {
        mSoundPool.stop(mPlayingId);

        if (mWindowManager == null)
            mWindowManager = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);

        if (mLinLayout != null)
            mWindowManager.removeViewImmediate(mLinLayout);
        displayed = false;
        mWakeLock.release();
        log.debug("dismis()");
    }

    private Context getApplicationContext() {
        return mApplicationContext;
    }

    public void setText(String text) {
        mAlertText = text;
    }

    public void setOnDismiss(Runnable runnable) {
        mOnDismiss = runnable;
    }
}
