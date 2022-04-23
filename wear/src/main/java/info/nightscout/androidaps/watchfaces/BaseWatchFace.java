package info.nightscout.androidaps.watchfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.wearable.view.WatchViewStub;
import android.text.format.DateFormat;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.ustwo.clockwise.common.WatchFaceTime;
import com.ustwo.clockwise.common.WatchMode;
import com.ustwo.clockwise.common.WatchShape;
import com.ustwo.clockwise.wearable.WatchFace;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventWearPreferenceChange;
import info.nightscout.androidaps.events.EventWearToMobile;
import info.nightscout.androidaps.interaction.utils.Persistence;
import info.nightscout.androidaps.interaction.utils.WearUtil;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.rx.AapsSchedulers;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.shared.sharedPreferences.SP;
import info.nightscout.shared.weardata.EventData;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import lecho.lib.hellocharts.view.LineChartView;

/**
 * Created by emmablack on 12/29/14.
 * Updated by andrew-warrington on 02-Jan-2018.
 * Refactored by dlvoy on 2019-11-2019
 */

public abstract class BaseWatchFace extends WatchFace {

    @Inject WearUtil wearUtil;
    @Inject Persistence persistence;
    @Inject AAPSLogger aapsLogger;
    @Inject RxBus rxBus;
    @Inject AapsSchedulers aapsSchedulers;
    @Inject SP sp;
    @Inject DateUtil dateUtil;

    CompositeDisposable disposable = new CompositeDisposable();

    protected EventData.SingleBg singleBg = new EventData.SingleBg(0, "---", "-", "--", "--", "--", 0, 0.0, 0.0, 0.0, 0);
    protected EventData.Status status = new EventData.Status("no status", "IOB", "-.--", false, "--g", "-.--U/h", "--", "--", -1, "--", false, 1);
    protected EventData.TreatmentData treatmentData = new EventData.TreatmentData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    protected EventData.GraphData graphData = new EventData.GraphData(new ArrayList<>());

    static IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

    public final Point displaySize = new Point();
    public TextView mTime, mHour, mMinute, mTimePeriod, mSgv, mDirection, mTimestamp, mUploaderBattery, mRigBattery, mDelta, mAvgDelta, mStatus, mBasalRate, mIOB1, mIOB2, mCOB1, mCOB2, mBgi, mLoop, mDay, mDayName, mMonth, isAAPSv2, mHighLight, mLowLight;
    public ImageView mGlucoseDial, mDeltaGauge, mHourHand, mMinuteHand;
    public RelativeLayout mRelativeLayout;
    public LinearLayout mLinearLayout, mLinearLayout2, mDate, mChartTap, mMainMenuTap;
    public int ageLevel = 1;
    public int loopLevel = -1;
    public int highColor = Color.YELLOW;
    public int lowColor = Color.RED;
    public int midColor = Color.WHITE;
    public int gridColor = Color.WHITE;
    public int basalBackgroundColor = Color.BLUE;
    public int basalCenterColor = Color.BLUE;
    public int bolusColor = Color.MAGENTA;
    public boolean lowResMode = false;
    public boolean layoutSet = false;
    public boolean bIsRound = false;
    public boolean dividerMatchesBg = false;
    public int pointSize = 2;
    public BgGraphBuilder bgGraphBuilder;
    public LineChartView chart;
    public PowerManager.WakeLock wakeLock;
    // related endTime manual layout
    public View layoutView;
    public int specW, specH;
    public boolean forceSquareCanvas = false; // Set to true by the Steampunk watch face.
    public String sMinute = "0";
    public String sHour = "0";
    private BroadcastReceiver batteryReceiver;
    private int colorDarkHigh, colorDarkMid, colorDarkLow;
    private java.text.DateFormat timeFormat;
    private SimpleDateFormat sdfDay, sdfMonth, sdfHour, sdfPeriod, sdfDayName, sdfMinute;
    private Paint mBackgroundPaint, mTimePaint, mSvgPaint, mDirectionPaint;
    private Date mDateTime;
    private String mLastSvg = "", mLastDirection = "";
    private float mYOffset = 0;

    @Override
    public void onCreate() {
        // Not derived from DaggerService, do injection here
        AndroidInjection.inject(this);
        super.onCreate();

        colorDarkHigh = ContextCompat.getColor(this, R.color.dark_highColor);
        colorDarkMid = ContextCompat.getColor(this, R.color.dark_midColor);
        colorDarkLow = ContextCompat.getColor(this, R.color.dark_lowColor);

        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        display.getSize(displaySize);
        wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:BaseWatchFace");

        specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY);
        if (forceSquareCanvas) {
            specH = specW;
        } else {
            specH = View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY);
        }

        disposable.add(rxBus
                .toObservable(EventWearPreferenceChange.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> {
                    setupBatteryReceiver();
                    if (event.getChangedKey() != null && event.getChangedKey().equals("delta_granularity"))
                        rxBus.send(new EventWearToMobile(new EventData.ActionResendData("BaseWatchFace:onSharedPreferenceChanged")));
                    if (layoutSet) setDataFields();
                    invalidate();
                })
        );
        disposable.add(rxBus
                .toObservable(EventData.SingleBg.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> singleBg = event)
        );
        disposable.add(rxBus
                .toObservable(EventData.TreatmentData.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> treatmentData = event)
        );
        disposable.add(rxBus
                .toObservable(EventData.GraphData.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> graphData = event)
        );
        disposable.add(rxBus
                .toObservable(EventData.Status.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> {
                    status = event;
                    // this event is received as last batch of data
                    if (!isSimpleUi() || !needUpdate()) {
                        setupCharts();
                        setDataFields();
                    }
                    invalidate();
                })
        );

        persistence.turnOff();

        setupBatteryReceiver();
        initFormats();
        setupSimpleUi();
    }

    private void setupBatteryReceiver() {
        String setting = sp.getString("simplify_ui", "off");
        if ((setting.equals("charging") || setting.equals("ambient_charging")) && batteryReceiver == null) {
            IntentFilter intentBatteryFilter = new IntentFilter();
            intentBatteryFilter.addAction(BatteryManager.ACTION_CHARGING);
            intentBatteryFilter.addAction(BatteryManager.ACTION_DISCHARGING);
            batteryReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    setDataFields();
                    invalidate();
                }
            };
            registerReceiver(batteryReceiver, intentBatteryFilter);
        }
    }

    private void initFormats() {
        Locale locale = Locale.getDefault();
        timeFormat = DateFormat.getTimeFormat(BaseWatchFace.this);
        sdfMinute = new SimpleDateFormat("mm", locale);
        sdfHour = DateFormat.is24HourFormat(this) ? new SimpleDateFormat("HH", locale) : new SimpleDateFormat("hh", locale);
        sdfPeriod = new SimpleDateFormat("a", locale);
        sdfDay = new SimpleDateFormat("dd", locale);
        sdfDayName = new SimpleDateFormat("E", locale);
        sdfMonth = new SimpleDateFormat("MMM", locale);
    }

    private void setupSimpleUi() {
        mDateTime = new Date();

        int black = ContextCompat.getColor(this, R.color.black);
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(black);

        final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        int white = ContextCompat.getColor(this, R.color.white);

        Resources resources = this.getResources();
        float textSizeSvg = resources.getDimension(R.dimen.simple_ui_svg_text_size);
        float textSizeDirection = resources.getDimension(R.dimen.simple_ui_direction_text_size);
        float textSizeTime = resources.getDimension(R.dimen.simple_ui_time_text_size);
        mYOffset = resources.getDimension(R.dimen.simple_ui_y_offset);

        mSvgPaint = createTextPaint(NORMAL_TYPEFACE, white, textSizeSvg);
        mDirectionPaint = createTextPaint(BOLD_TYPEFACE, white, textSizeDirection);
        mTimePaint = createTextPaint(NORMAL_TYPEFACE, white, textSizeTime);
    }

    private Paint createTextPaint(Typeface typeface, int colour, float textSize) {
        Paint paint = new Paint();
        paint.setColor(colour);
        paint.setTypeface(typeface);
        paint.setAntiAlias(true);
        paint.setTextSize(textSize);
        return paint;
    }

    @Override
    protected void onLayout(WatchShape shape, Rect screenBounds, WindowInsets screenInsets) {
        super.onLayout(shape, screenBounds, screenInsets);
        layoutView.onApplyWindowInsets(screenInsets);
        bIsRound = screenInsets.isRound();
    }

    public void performViewSetup() {
            mTime = layoutView.findViewById(R.id.watch_time);
            mHour = layoutView.findViewById(R.id.hour);
            mMinute = layoutView.findViewById(R.id.minute);
            mTimePeriod = layoutView.findViewById(R.id.timePeriod);
            mDay = layoutView.findViewById(R.id.day);
            mDayName = layoutView.findViewById(R.id.dayname);
            mMonth = layoutView.findViewById(R.id.month);
            mDate = layoutView.findViewById(R.id.date_time);
            mLoop = layoutView.findViewById(R.id.loop);
            mSgv = layoutView.findViewById(R.id.sgv);
            mDirection = layoutView.findViewById(R.id.direction);
            mTimestamp = layoutView.findViewById(R.id.timestamp);
            mIOB1 = layoutView.findViewById(R.id.iob_text);
            mIOB2 = layoutView.findViewById(R.id.iobView);
            mCOB1 = layoutView.findViewById(R.id.cob_text);
            mCOB2 = layoutView.findViewById(R.id.cobView);
            mBgi = layoutView.findViewById(R.id.bgiView);
            mStatus = layoutView.findViewById(R.id.externaltstatus);
            mBasalRate = layoutView.findViewById(R.id.tmpBasal);
            mUploaderBattery = layoutView.findViewById(R.id.uploader_battery);
            mRigBattery = layoutView.findViewById(R.id.rig_battery);
            mDelta = layoutView.findViewById(R.id.delta);
            mAvgDelta = layoutView.findViewById(R.id.avgdelta);
            isAAPSv2 = layoutView.findViewById(R.id.AAPSv2);
            mHighLight = layoutView.findViewById(R.id.highLight);
            mLowLight = layoutView.findViewById(R.id.lowLight);
            mRelativeLayout = layoutView.findViewById(R.id.main_layout);
            mLinearLayout = layoutView.findViewById(R.id.secondary_layout);
            mLinearLayout2 = layoutView.findViewById(R.id.tertiary_layout);
            mGlucoseDial = layoutView.findViewById(R.id.glucose_dial);
            mDeltaGauge = layoutView.findViewById(R.id.delta_pointer);
            mHourHand = layoutView.findViewById(R.id.hour_hand);
            mMinuteHand = layoutView.findViewById(R.id.minute_hand);
            mChartTap = layoutView.findViewById(R.id.chart_zoom_tap);
            mMainMenuTap = layoutView.findViewById(R.id.main_menu_tap);
            chart = layoutView.findViewById(R.id.chart);
            layoutSet = true;
            setupCharts();
            setDataFields();
            missedReadingAlert();

        wakeLock.acquire(50);
    }

    public int ageLevel() {
        if (timeSince() <= (1000 * 60 * 12)) {
            return 1;
        }
        return 0;
    }

    public double timeSince() {
        return System.currentTimeMillis() - singleBg.getTimeStamp();
    }

    public String readingAge(boolean shortString) {
        if (singleBg.getTimeStamp() == 0) {
            return shortString ? "--" : "-- Minute ago";
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
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        super.onDestroy();
    }

    @Override
    protected long getInteractiveModeUpdateRate() {
        return 60 * 1000L; // Only call onTimeChanged every 60 seconds
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isSimpleUi()) {
            onDrawSimpleUi(canvas);
        } else {
            if (layoutSet) {
                mRelativeLayout.measure(specW, specH);
                int y = forceSquareCanvas ? displaySize.x : displaySize.y; // Square Steampunk
                mRelativeLayout.layout(0, 0, displaySize.x, y);
                mRelativeLayout.draw(canvas);
            }
        }
    }

    protected void onDrawSimpleUi(Canvas canvas) {
        canvas.drawRect(0, 0, displaySize.x, displaySize.y, mBackgroundPaint);
        float xHalf = displaySize.x / 2f;
        float yThird = displaySize.y / 3f;

        boolean isOutdated = singleBg.getTimeStamp() > 0 && ageLevel() <= 0;
        mSvgPaint.setStrikeThruText(isOutdated);

        mSvgPaint.setColor(getBgColour(singleBg.getSgvLevel()));
        mDirectionPaint.setColor(getBgColour(singleBg.getSgvLevel()));

        String sSvg = singleBg.getSgvString();
        float svgWidth = mSvgPaint.measureText(sSvg);

        String sDirection = " " + singleBg.getSgvString() + "\uFE0E";
        float directionWidth = mDirectionPaint.measureText(sDirection);

        float xSvg = xHalf - (svgWidth + directionWidth) / 2;
        canvas.drawText(sSvg, xSvg, yThird + mYOffset, mSvgPaint);
        float xDirection = xSvg + svgWidth;
        canvas.drawText(sDirection, xDirection, yThird + mYOffset, mDirectionPaint);

        String sTime = timeFormat.format(mDateTime);
        float xTime = xHalf - mTimePaint.measureText(sTime) / 2f;
        canvas.drawText(timeFormat.format(mDateTime), xTime, yThird * 2f + mYOffset, mTimePaint);
    }

    int getBgColour(long level) {
        if (level == 1) {
            return colorDarkHigh;
        }
        if (level == 0) {
            return colorDarkMid;
        }
        return colorDarkLow;
    }

    @Override
    protected void onTimeChanged(WatchFaceTime oldTime, WatchFaceTime newTime) {
        if (layoutSet && (newTime.hasHourChanged(oldTime) || newTime.hasMinuteChanged(oldTime))) {
            long now = System.currentTimeMillis();
            mDateTime.setTime(now);

            PowerManager.WakeLock wl = wearUtil.getWakeLock("readingPrefs", 50);
            missedReadingAlert();
            checkVibrateHourly(oldTime, newTime);
            if (!isSimpleUi()) {
                setDataFields();
            }
            wearUtil.releaseWakeLock(wl);
        }
    }

    private boolean isCharging() {
        Intent mBatteryStatus = this.registerReceiver(null, iFilter);
        int status = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private void checkVibrateHourly(WatchFaceTime oldTime, WatchFaceTime newTime) {
        boolean hourlyVibratePref = sp.getBoolean("vibrate_Hourly", false);
        if (hourlyVibratePref && layoutSet && newTime.hasHourChanged(oldTime)) {
            aapsLogger.info(LTag.WEAR, "hourlyVibratePref", "true --> " + newTime);
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] vibrationPattern = {0, 150, 125, 100};
            vibrator.vibrate(vibrationPattern, -1);
        }
    }

    public void setDataFields() {
        setDateAndTime();
        if (mSgv != null) {
            if (sp.getBoolean("showBG", true)) {
                mSgv.setText(singleBg.getSgvString());
                mSgv.setVisibility(View.VISIBLE);
            } else {
                // Leave the textview there but invisible, as a height holder for the empty space above the white line
                mSgv.setVisibility(View.INVISIBLE);
                mSgv.setText("");
            }
        }

        strikeThroughSgvIfNeeded();

        if (mDirection != null) {
            if (sp.getBoolean("show_direction", true)) {
                mDirection.setText(singleBg.getSgvString() + "\uFE0E");
                mDirection.setVisibility(View.VISIBLE);
            } else {
                mDirection.setVisibility(View.GONE);
            }
        }

        if (mDelta != null) {
            if (sp.getBoolean("showDelta", true)) {
                mDelta.setText(singleBg.getDelta());
                mDelta.setVisibility(View.VISIBLE);
            } else {
                mDelta.setVisibility(View.GONE);
            }
        }

        if (mAvgDelta != null) {
            if (sp.getBoolean("showAvgDelta", true)) {
                mAvgDelta.setText(singleBg.getAvgDelta());
                mAvgDelta.setVisibility(View.VISIBLE);
            } else {
                mAvgDelta.setVisibility(View.GONE);
            }
        }

        if (mCOB1 != null && mCOB2 != null) {
            mCOB2.setText(status.getCob());
            if (sp.getBoolean("show_cob", true)) {
                mCOB1.setVisibility(View.VISIBLE);
                mCOB2.setVisibility(View.VISIBLE);
            } else {
                mCOB1.setVisibility(View.GONE);
                mCOB2.setVisibility(View.GONE);
            }
            // Deal with cases where there is only the value shown for COB, and not the label
        } else if (mCOB2 != null) {
            mCOB2.setText(status.getCob());
            if (sp.getBoolean("show_cob", true)) {
                mCOB2.setVisibility(View.VISIBLE);
            } else {
                mCOB2.setVisibility(View.GONE);
            }
        }

        if (mIOB1 != null && mIOB2 != null) {
            if (sp.getBoolean("show_iob", true)) {
                mIOB1.setVisibility(View.VISIBLE);
                mIOB2.setVisibility(View.VISIBLE);
                if (status.getDetailedIob()) {
                    mIOB1.setText(status.getIobSum());
                    mIOB2.setText(status.getIobDetail());
                } else {
                    mIOB1.setText(getString(R.string.activity_IOB));
                    mIOB2.setText(status.getIobSum());
                }
            } else {
                mIOB1.setVisibility(View.GONE);
                mIOB2.setVisibility(View.GONE);
            }
            // Deal with cases where there is only the value shown for IOB, and not the label
        } else if (mIOB2 != null) {
            if (sp.getBoolean("show_iob", true)) {
                mIOB2.setVisibility(View.VISIBLE);
                if (status.getDetailedIob()) {
                    mIOB2.setText(status.getIobDetail());
                } else {
                    mIOB2.setText(status.getIobSum());
                }
            } else {
                mIOB2.setText("");
            }
        }

        if (mTimestamp != null) {
            if (sp.getBoolean("showAgo", true)) {
                if (isAAPSv2 != null) {
                    mTimestamp.setText(readingAge(true));
                } else {
                    boolean shortString = sp.getBoolean("showExternalStatus", true);
                    mTimestamp.setText(readingAge(shortString));
                }
                mTimestamp.setVisibility(View.VISIBLE);
            } else {
                mTimestamp.setVisibility(View.GONE);
            }
        }

        if (mUploaderBattery != null) {
            if (sp.getBoolean("show_uploader_battery", true)) {
                if (isAAPSv2 != null) {
                    mUploaderBattery.setText(status.getBattery() + "%");
                    mUploaderBattery.setVisibility(View.VISIBLE);
                } else {
                    if (sp.getBoolean("showExternalStatus", true)) {
                        mUploaderBattery.setText("U: " + status.getBattery() + "%");
                    } else {
                        mUploaderBattery.setText("Uploader: " + status.getBattery() + "%");
                    }
                }
            } else {
                mUploaderBattery.setVisibility(View.GONE);
            }
        }

        if (mRigBattery != null) {
            if (sp.getBoolean("show_rig_battery", false)) {
                mRigBattery.setText(status.getRigBattery());
                mRigBattery.setVisibility(View.VISIBLE);
            } else {
                mRigBattery.setVisibility(View.GONE);
            }
        }

        if (mBasalRate != null) {
            if (sp.getBoolean("show_temp_basal", true)) {
                mBasalRate.setText(status.getCurrentBasal());
                mBasalRate.setVisibility(View.VISIBLE);
            } else {
                mBasalRate.setVisibility(View.GONE);
            }
        }

        if (mBgi != null) {
            if (status.getShowBgi()) {
                mBgi.setText(status.getBgi());
                mBgi.setVisibility(View.VISIBLE);
            } else {
                mBgi.setVisibility(View.GONE);
            }
        }

        if (mStatus != null) {
            if (sp.getBoolean("showExternalStatus", true)) {
                mStatus.setText(status.getExternalStatus());
                mStatus.setVisibility(View.VISIBLE);
            } else {
                mStatus.setVisibility(View.GONE);
            }
        }

        if (mLoop != null) {
            if (sp.getBoolean("showExternalStatus", true)) {
                mLoop.setVisibility(View.VISIBLE);
                if (status.getOpenApsStatus() != -1) {
                    int mins = (int) ((System.currentTimeMillis() - status.getOpenApsStatus()) / 1000 / 60);
                    mLoop.setText(mins + "'");
                    if (mins > 14) {
                        loopLevel = 0;
                        mLoop.setBackgroundResource(R.drawable.loop_red_25);
                    } else {
                        loopLevel = 1;
                        mLoop.setBackgroundResource(R.drawable.loop_green_25);
                    }
                } else {
                    loopLevel = -1;
                    mLoop.setText("-");
                    mLoop.setBackgroundResource(R.drawable.loop_grey_25);
                }
            } else {
                mLoop.setVisibility(View.GONE);
            }
        }

        setColor();
    }

    @Override
    protected void on24HourFormatChanged(boolean is24HourFormat) {
        initFormats();
        if (!isSimpleUi()) {
            setDataFields();
        }
        invalidate();
    }

    public void setDateAndTime() {
        if (mTime != null) {
            mTime.setText(timeFormat.format(mDateTime));
        }

        sMinute = sdfMinute.format(mDateTime);
        sHour = sdfHour.format(mDateTime);
        if (mHour != null && mMinute != null) {
            mHour.setText(sHour);
            mMinute.setText(sMinute);
        }

        if (mTimePeriod != null) {
            if (!DateFormat.is24HourFormat(this)) {
                mTimePeriod.setVisibility(View.VISIBLE);
                mTimePeriod.setText(sdfPeriod.format(mDateTime).toUpperCase());
            } else {
                mTimePeriod.setVisibility(View.GONE);
            }
        }

        if (mDate != null && mDay != null && mMonth != null) {
            if (sp.getBoolean("show_date", false)) {
                if (mDayName != null) {
                    mDayName.setText(sdfDayName.format(mDateTime));
                }

                mDay.setText(sdfDay.format(mDateTime));
                mMonth.setText(sdfMonth.format(mDateTime));
                mDate.setVisibility(View.VISIBLE);
            } else {
                mDate.setVisibility(View.GONE);
            }
        }
    }

    public void setColor() {
        dividerMatchesBg = sp.getBoolean("match_divider", false);
        if (lowResMode) {
            setColorLowRes();
        } else if (sp.getBoolean("dark", true)) {
            setColorDark();
        } else {
            setColorBright();
        }
    }

    public void strikeThroughSgvIfNeeded() {
        if (mSgv != null) {
            if (sp.getBoolean("showBG", true)) {
                if (ageLevel() <= 0 && singleBg.getTimeStamp() > 0) {
                    mSgv.setPaintFlags(mSgv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    mSgv.setPaintFlags(mSgv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                }
            }
        }
    }

    protected void onWatchModeChanged(WatchMode watchMode) {
        lowResMode = isLowRes(watchMode);
        if (isSimpleUi()) {
            setSimpleUiAntiAlias();
        } else {
            setDataFields();
        }
        invalidate();
    }

    void setSimpleUiAntiAlias() {
        boolean antiAlias = getCurrentWatchMode() == WatchMode.AMBIENT;
        mSvgPaint.setAntiAlias(antiAlias);
        mDirectionPaint.setAntiAlias(antiAlias);
        mTimePaint.setAntiAlias(antiAlias);
    }

    private boolean isLowRes(WatchMode watchMode) {
        return watchMode == WatchMode.LOW_BIT || watchMode == WatchMode.LOW_BIT_BURN_IN;
    }

    private boolean isSimpleUi() {
        String simplify = sp.getString("simplify_ui", "off");
        if (simplify.equals("off")) {
            return false;
        }
        if ((simplify.equals("ambient") || simplify.equals("ambient_charging")) && getCurrentWatchMode() == WatchMode.AMBIENT) {
            return true;
        }
        return (simplify.equals("charging") || simplify.equals("ambient_charging")) && isCharging();
    }

    protected abstract void setColorDark();

    protected abstract void setColorBright();

    protected abstract void setColorLowRes();

    public void missedReadingAlert() {
        int minutes_since = (int) Math.floor(timeSince() / (1000 * 60));
        if (singleBg.getTimeStamp() == 0 || minutes_since >= 16 && ((minutes_since - 16) % 5) == 0) {
            // Attempt endTime recover missing data
            rxBus.send(new EventWearToMobile(new EventData.ActionResendData("BaseWatchFace:missedReadingAlert")));
        }
    }

    public void setupCharts() {
        if (isSimpleUi()) {
            return;
        }
        if (chart != null && graphData.getEntries().size() > 0) {
            int timeframe = sp.getInt("chart_timeframe", 3);
            if (lowResMode) {
                bgGraphBuilder = new BgGraphBuilder(sp, dateUtil,
                        graphData.getEntries(), treatmentData.getPredictions(), treatmentData.getTemps(), treatmentData.getBasals(), treatmentData.getBoluses(), pointSize, midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, Color.GREEN, timeframe);
            } else {
                bgGraphBuilder = new BgGraphBuilder(sp, dateUtil, graphData.getEntries(),
                        treatmentData.getPredictions(), treatmentData.getTemps(), treatmentData.getBasals(), treatmentData.getBoluses(), pointSize, highColor, lowColor, midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, Color.GREEN, timeframe);
            }

            chart.setLineChartData(bgGraphBuilder.lineData());
            chart.setViewportCalculationEnabled(true);
            chart.setMaximumViewport(chart.getMaximumViewport());
        }
    }

    private boolean needUpdate() {
        if (mLastSvg.equals(singleBg.getSgvString()) && mLastDirection.equals(singleBg.getSgvString())) {
            return false;
        }
        mLastSvg = singleBg.getSgvString();
        mLastDirection = singleBg.getSgvString();
        return true;
    }
}
