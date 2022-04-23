package info.nightscout.androidaps.watchfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.PowerManager;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.ustwo.clockwise.common.WatchFaceTime;
import com.ustwo.clockwise.common.WatchMode;
import com.ustwo.clockwise.common.WatchShape;
import com.ustwo.clockwise.wearable.WatchFace;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventWearToMobile;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.utils.rx.AapsSchedulers;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.shared.sharedPreferences.SP;
import info.nightscout.shared.weardata.EventData;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Created by adrianLxM.
 */
public class NOChart extends WatchFace {

    @Inject RxBus rxBus;
    @Inject AapsSchedulers aapsSchedulers;
    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;

    CompositeDisposable disposable = new CompositeDisposable();

    private EventData.SingleBg singleBg;
    private EventData.Status status;

    public static final int SCREENSIZE_SMALL = 280;
    public TextView mTime, mSgv, mTimestamp, mDelta, mAvgDelta;
    public RelativeLayout mRelativeLayout;
    public long sgvLevel = 0;
    public int ageLevel = 1;
    public boolean lowResMode = false;
    public boolean layoutSet = false;
    public PowerManager.WakeLock wakeLock;
    public View layoutView;
    private final Point displaySize = new Point();
    private int specW, specH;

    private TextView statusView;
    private long sgvTapTime = 0L;

    @SuppressLint("InflateParams") @Override
    public void onCreate() {
        AndroidInjection.inject(this);
        super.onCreate();
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        display.getSize(displaySize);
        wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:NOChart");

        specW = View.MeasureSpec.makeMeasureSpec(displaySize.x,
                View.MeasureSpec.EXACTLY);
        specH = View.MeasureSpec.makeMeasureSpec(displaySize.y,
                View.MeasureSpec.EXACTLY);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (metrics.widthPixels < SCREENSIZE_SMALL || metrics.heightPixels < SCREENSIZE_SMALL) {
            layoutView = inflater.inflate(R.layout.activity_nochart_small, null);
        } else {
            layoutView = inflater.inflate(R.layout.activity_nochart, null);
        }
        disposable.add(rxBus
                .toObservable(EventData.SingleBg.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> {
                    aapsLogger.debug(LTag.WEAR, "SingleBg received");
                    singleBg = event;

                    mSgv.setText(singleBg.getSgvString());
                    if (ageLevel() <= 0)
                        mSgv.setPaintFlags(mSgv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    else mSgv.setPaintFlags(mSgv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(this);
                    mTime.setText(timeFormat.format(System.currentTimeMillis()));
                    mDelta.setText(singleBg.getDelta());
                    mAvgDelta.setText(singleBg.getAvgDelta());
                })
        );
        disposable.add(rxBus
                .toObservable(EventData.Status.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> {
                    // this event is received as last batch of data
                    aapsLogger.debug(LTag.WEAR, "Status received");
                    status = event;
                    showAgeAndStatus();
                    mRelativeLayout.measure(specW, specH);
                    mRelativeLayout.layout(0, 0, mRelativeLayout.getMeasuredWidth(),
                            mRelativeLayout.getMeasuredHeight());
                    invalidate();
                    setColor();
                })
        );
        disposable.add(rxBus
                .toObservable(EventData.Preferences.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> {
                    setColor();
                    if (layoutSet) {
                        showAgeAndStatus();
                        mRelativeLayout.measure(specW, specH);
                        mRelativeLayout.layout(0, 0, mRelativeLayout.getMeasuredWidth(),
                                mRelativeLayout.getMeasuredHeight());
                    }
                    invalidate();
                })
        );
        performViewSetup();
    }

    @Override
    protected void onLayout(WatchShape shape, Rect screenBounds, WindowInsets screenInsets) {
        super.onLayout(shape, screenBounds, screenInsets);
        layoutView.onApplyWindowInsets(screenInsets);
    }

    public void performViewSetup() {
        mTime = layoutView.findViewById(R.id.watch_time);
        mSgv = layoutView.findViewById(R.id.sgv);
        mTimestamp = layoutView.findViewById(R.id.timestamp);
        mDelta = layoutView.findViewById(R.id.delta);
        mAvgDelta = layoutView.findViewById(R.id.avgdelta);
        mRelativeLayout = layoutView.findViewById(R.id.main_layout);
        statusView = layoutView.findViewById(R.id.aps_status);
        layoutSet = true;
        showAgeAndStatus();
        mRelativeLayout.measure(specW, specH);
        mRelativeLayout.layout(0, 0, mRelativeLayout.getMeasuredWidth(), mRelativeLayout.getMeasuredHeight());
        rxBus.send(new EventWearToMobile(new EventData.ActionResendData("NOChart:performViewSetup")));
        wakeLock.acquire(50);
    }

    @Override
    protected void onTapCommand(int tapType, int x, int y, long eventTime) {
        if (mSgv == null) return;
        int extra = (mSgv.getRight() - mSgv.getLeft()) / 2;

        if (tapType == TAP_TYPE_TAP &&
                x + extra >= mSgv.getLeft() &&
                x - extra <= mSgv.getRight() &&
                y >= mSgv.getTop() &&
                y <= mSgv.getBottom()) {
            if (eventTime - sgvTapTime < 800) {
                Intent intent = new Intent(this, MainMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            sgvTapTime = eventTime;
        }
    }

    protected void onWatchModeChanged(WatchMode watchMode) {

        if (lowResMode ^ isLowRes(watchMode)) { //if there was a change in lowResMode
            lowResMode = isLowRes(watchMode);
            setColor();
        } else if (!sp.getBoolean("dark", true)) {
            //in bright mode: different colours if active:
            setColor();
        }
    }

    private boolean isLowRes(WatchMode watchMode) {
        return (watchMode == WatchMode.LOW_BIT) || (watchMode == WatchMode.LOW_BIT_BURN_IN);
    }


    @SuppressWarnings("deprecation")
    @Override
    protected WatchFaceStyle getWatchFaceStyle() {
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
    }


    public int ageLevel() {
        if (timeSince() <= (1000 * 60 * 12)) {
            return 1;
        } else {
            return 0;
        }
    }

    public double timeSince() {
        return System.currentTimeMillis() - singleBg.getTimeStamp();
    }

    public String readingAge(boolean shortString) {
        if (singleBg == null || singleBg.getTimeStamp() == 0) {
            return shortString ? "--'" : "-- Minute ago";
        }
        int minutesAgo = (int) Math.floor(timeSince() / (1000 * 60));
        if (minutesAgo == 1) {
            return minutesAgo + (shortString ? "'" : " Minute ago");
        }
        return minutesAgo + (shortString ? "'" : " Minutes ago");
    }

    @Override
    public void onDestroy() {
        disposable.clear();
        super.onDestroy();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (layoutSet) {
            this.mRelativeLayout.draw(canvas);
        }
    }

    @Override
    protected void onTimeChanged(WatchFaceTime oldTime, WatchFaceTime newTime) {
        if (layoutSet && (newTime.hasHourChanged(oldTime) || newTime.hasMinuteChanged(oldTime))) {
            wakeLock.acquire(50);
            final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(NOChart.this);
            mTime.setText(timeFormat.format(System.currentTimeMillis()));
            showAgeAndStatus();

            if (ageLevel() <= 0) {
                mSgv.setPaintFlags(mSgv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                mSgv.setPaintFlags(mSgv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }

            missedReadingAlert();
            mRelativeLayout.measure(specW, specH);
            mRelativeLayout.layout(0, 0, mRelativeLayout.getMeasuredWidth(),
                    mRelativeLayout.getMeasuredHeight());
        }
    }

    private void showAgeAndStatus() {

        if (mTimestamp != null) {
            mTimestamp.setText(readingAge(true));
        }
        boolean showAvgDelta = sp.getBoolean("showAvgDelta", true);

        if (showAvgDelta) {
            mAvgDelta.setVisibility(View.VISIBLE);
        } else {
            mAvgDelta.setVisibility(View.GONE);
        }
        if (status != null) {
            statusView.setText(status.getExternalStatus());
            statusView.setVisibility(View.VISIBLE);
        }
    }

    public void setColor() {
        if (lowResMode) {
            setColorLowRes();
        } else if (sp.getBoolean("dark", true)) {
            setColorDark();
        } else {
            setColorBright();
        }

    }

    protected void setColorLowRes() {
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        statusView.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_statusView));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mAvgDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_Timestamp));
    }

    protected void setColorDark() {
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        statusView.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_statusView));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        if (sgvLevel == 1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mAvgDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
        } else if (sgvLevel == 0) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mAvgDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else if (sgvLevel == -1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mAvgDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
        }

        if (ageLevel == 1) {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_Timestamp));
        } else {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_TimestampOld));
        }
    }


    protected void setColorBright() {

        if (getCurrentWatchMode() == WatchMode.INTERACTIVE) {
            mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_bigchart_time));
            statusView.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_bigchart_status));
            mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.light_background));
            if (sgvLevel == 1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mAvgDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
            } else if (sgvLevel == 0) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mAvgDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
            } else if (sgvLevel == -1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mAvgDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
            }

            if (ageLevel == 1) {
                mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_mTimestamp1));
            } else {
                mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_mTimestamp));
            }
        } else {
            setColorDark();
        }
    }

    public void missedReadingAlert() {
        int minutes_since = (int) Math.floor(timeSince() / (1000 * 60));
        if (minutes_since >= 16 && ((minutes_since - 16) % 5) == 0) {
            // attempt endTime recover missing data
            rxBus.send(new EventWearToMobile(new EventData.ActionResendData("NOChart" +
                    ":missedReadingAlert")));
        }
    }
}
