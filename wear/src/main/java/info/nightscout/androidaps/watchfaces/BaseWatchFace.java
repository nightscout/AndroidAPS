package info.nightscout.androidaps.watchfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.wearable.view.WatchViewStub;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.wearable.DataMap;
import com.ustwo.clockwise.common.WatchFaceTime;
import com.ustwo.clockwise.common.WatchMode;
import com.ustwo.clockwise.common.WatchShape;
import com.ustwo.clockwise.wearable.WatchFace;

import java.text.SimpleDateFormat;
import java.util.Date;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.complications.BaseComplicationProviderService;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.data.RawDisplayData;
import lecho.lib.hellocharts.view.LineChartView;

/**
 * Created by emmablack on 12/29/14.
 * Updated by andrew-warrington on 02-Jan-2018.
 * Refactored by dlvoy on 2019-11-2019
 */

public abstract class BaseWatchFace extends WatchFace implements SharedPreferences.OnSharedPreferenceChangeListener {
    public final static IntentFilter INTENT_FILTER;
    public static final long[] vibratePattern = {0, 400, 300, 400, 300, 400};

    static {
        INTENT_FILTER = new IntentFilter();
        INTENT_FILTER.addAction(Intent.ACTION_TIME_TICK);
        INTENT_FILTER.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        INTENT_FILTER.addAction(Intent.ACTION_TIME_CHANGED);
    }

    public final Point displaySize = new Point();
    public TextView mTime, mHour, mMinute, mSgv, mDirection, mTimestamp, mUploaderBattery, mRigBattery, mDelta, mAvgDelta, mStatus, mBasalRate, mIOB1, mIOB2, mCOB1, mCOB2, mBgi, mLoop, mDay, mDayName, mMonth, isAAPSv2, mHighLight, mLowLight;
    public ImageView mGlucoseDial, mDeltaGauge, mHourHand, mMinuteHand;
    public RelativeLayout mRelativeLayout;
    public LinearLayout mLinearLayout, mLinearLayout2, mDate, mChartTap, mMainMenuTap;
    public int ageLevel = 1;
    public int loopLevel = 1;
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
    public RawDisplayData rawData = new RawDisplayData();
    public PowerManager.WakeLock wakeLock;
    // related endTime manual layout
    public View layoutView;
    public int specW, specH;
    public boolean forceSquareCanvas = false;  //set to true by the Steampunk watch face.
    public String sMinute = "0";
    public String sHour = "0";
    protected SharedPreferences sharedPrefs;
    private LocalBroadcastManager localBroadcastManager;
    private MessageReceiver messageReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        display.getSize(displaySize);
        wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:BaseWatchFace");

        specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY);
        if (forceSquareCanvas) {
            specH = specW;
        } else {
            specH = View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY);
        }
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);

        BaseComplicationProviderService.turnOff();
    }

    @Override
    protected void onLayout(WatchShape shape, Rect screenBounds, WindowInsets screenInsets) {
        super.onLayout(shape, screenBounds, screenInsets);
        layoutView.onApplyWindowInsets(screenInsets);
        bIsRound = screenInsets.isRound();
    }

    public void performViewSetup() {
        final WatchViewStub stub = layoutView.findViewById(R.id.watch_view_stub);
        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);

        messageReceiver = new MessageReceiver();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(messageReceiver, messageFilter);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
                                             @Override
                                             public void onLayoutInflated(WatchViewStub stub) {
                                                 mTime = stub.findViewById(R.id.watch_time);
                                                 mHour = stub.findViewById(R.id.hour);
                                                 mMinute = stub.findViewById(R.id.minute);
                                                 mDay = stub.findViewById(R.id.day);
                                                 mDayName = stub.findViewById(R.id.dayname);
                                                 mMonth = stub.findViewById(R.id.month);
                                                 mDate = stub.findViewById(R.id.date_time);
                                                 mLoop = stub.findViewById(R.id.loop);
                                                 mSgv = stub.findViewById(R.id.sgv);
                                                 mDirection = stub.findViewById(R.id.direction);
                                                 mTimestamp = stub.findViewById(R.id.timestamp);
                                                 mIOB1 = stub.findViewById(R.id.iob_text);
                                                 mIOB2 = stub.findViewById(R.id.iobView);
                                                 mCOB1 = stub.findViewById(R.id.cob_text);
                                                 mCOB2 = stub.findViewById(R.id.cobView);
                                                 mBgi = stub.findViewById(R.id.bgiView);
                                                 mStatus = stub.findViewById(R.id.externaltstatus);
                                                 mBasalRate = stub.findViewById(R.id.tmpBasal);
                                                 mUploaderBattery = stub.findViewById(R.id.uploader_battery);
                                                 mRigBattery = stub.findViewById(R.id.rig_battery);
                                                 mDelta = stub.findViewById(R.id.delta);
                                                 mAvgDelta = stub.findViewById(R.id.avgdelta);
                                                 isAAPSv2 = stub.findViewById(R.id.AAPSv2);
                                                 mHighLight = stub.findViewById(R.id.highLight);
                                                 mLowLight = stub.findViewById(R.id.lowLight);
                                                 mRelativeLayout = stub.findViewById(R.id.main_layout);
                                                 mLinearLayout = stub.findViewById(R.id.secondary_layout);
                                                 mLinearLayout2 = stub.findViewById(R.id.tertiary_layout);
                                                 mGlucoseDial = stub.findViewById(R.id.glucose_dial);
                                                 mDeltaGauge = stub.findViewById(R.id.delta_pointer);
                                                 mHourHand = stub.findViewById(R.id.hour_hand);
                                                 mMinuteHand = stub.findViewById(R.id.minute_hand);
                                                 mChartTap = stub.findViewById(R.id.chart_zoom_tap);
                                                 mMainMenuTap = stub.findViewById(R.id.main_menu_tap);
                                                 chart = stub.findViewById(R.id.chart);
                                                 layoutSet = true;

                                                 setDataFields();
                                                 setColor();
                                             }
                                         }
        );
        wakeLock.acquire(50);
    }

    public int ageLevel() {
        if (timeSince() <= (1000 * 60 * 12)) {
            return 1;
        } else {
            return 0;
        }
    }

    public double timeSince() {
        return System.currentTimeMillis() - rawData.datetime;
    }

    public String readingAge(boolean shortString) {
        if (rawData.datetime == 0) {
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
        if (localBroadcastManager != null && messageReceiver != null) {
            localBroadcastManager.unregisterReceiver(messageReceiver);
        }
        if (sharedPrefs != null) {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onDestroy();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (layoutSet) {
            setupCharts();

            mRelativeLayout.measure(specW, specH);
            if (forceSquareCanvas) {
                mRelativeLayout.layout(0, 0, displaySize.x, displaySize.x);  //force a square for Steampunk watch face.
            } else {
                mRelativeLayout.layout(0, 0, displaySize.x, displaySize.y);
            }
            mRelativeLayout.draw(canvas);
            Log.d("onDraw", "draw");
        }
    }

    @Override
    protected void onTimeChanged(WatchFaceTime oldTime, WatchFaceTime newTime) {
        if (layoutSet && (newTime.hasHourChanged(oldTime) || newTime.hasMinuteChanged(oldTime))) {
            wakeLock.acquire(50);

            setDataFields();
            setColor();
            missedReadingAlert();
            checkVibrateHourly(oldTime, newTime);

            mRelativeLayout.measure(specW, specH);
            if (forceSquareCanvas) {
                mRelativeLayout.layout(0, 0, displaySize.x, displaySize.x);  //force a square for Steampunk watch face.
            } else {
                mRelativeLayout.layout(0, 0, displaySize.x, displaySize.y);
            }
            invalidate();
        }
    }

    private void checkVibrateHourly(WatchFaceTime oldTime, WatchFaceTime newTime) {
        Boolean hourlyVibratePref = sharedPrefs.getBoolean("vibrate_Hourly", false);
        if (hourlyVibratePref && layoutSet && newTime.hasHourChanged(oldTime)) {
            Log.i("hourlyVibratePref", "true --> " + newTime.toString());
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] vibrationPattern = {0, 150, 125, 100};
            vibrator.vibrate(vibrationPattern, -1);
        }
    }

    public void setDataFields() {

        setDateAndTime();

        if (mSgv != null) {
            if (sharedPrefs.getBoolean("showBG", true)) {
                mSgv.setText(rawData.sSgv);
                mSgv.setVisibility(View.VISIBLE);
            } else {
                //leave the textview there but invisible, as a height holder for the empty space above the white line
                mSgv.setVisibility(View.INVISIBLE);
                mSgv.setText("");
            }
        }

        strikeThroughSgvIfNeeded();

        if (mDirection != null) {
            if (sharedPrefs.getBoolean("show_direction", true)) {
                mDirection.setText(rawData.sDirection);
                mDirection.setVisibility(View.VISIBLE);
            } else {
                mDirection.setVisibility(View.GONE);
            }
        }

        if (mDelta != null) {
            if (sharedPrefs.getBoolean("showDelta", true)) {
                mDelta.setText(rawData.sDelta);
                mDelta.setVisibility(View.VISIBLE);
            } else {
                mDelta.setVisibility(View.GONE);
            }
        }

        if (mAvgDelta != null) {
            if (sharedPrefs.getBoolean("showAvgDelta", true)) {
                mAvgDelta.setText(rawData.sAvgDelta);
                mAvgDelta.setVisibility(View.VISIBLE);
            } else {
                mAvgDelta.setVisibility(View.GONE);
            }
        }

        if (mCOB1 != null && mCOB2 != null) {
            mCOB2.setText(rawData.sCOB2);
            if (sharedPrefs.getBoolean("show_cob", true)) {
                mCOB1.setVisibility(View.VISIBLE);
                mCOB2.setVisibility(View.VISIBLE);
            } else {
                mCOB1.setVisibility(View.GONE);
                mCOB2.setVisibility(View.GONE);
            }
            //deal with cases where there is only the value shown for COB, and not the label
        } else if (mCOB2 != null) {
            mCOB2.setText(rawData.sCOB2);
            if (sharedPrefs.getBoolean("show_cob", true)) {
                mCOB2.setVisibility(View.VISIBLE);
            } else {
                mCOB2.setVisibility(View.GONE);
            }
        }

        if (mIOB1 != null && mIOB2 != null) {
            if (sharedPrefs.getBoolean("show_iob", true)) {
                mIOB1.setVisibility(View.VISIBLE);
                mIOB2.setVisibility(View.VISIBLE);
                if (rawData.detailedIOB) {
                    mIOB1.setText(rawData.sIOB1);
                    mIOB2.setText(rawData.sIOB2);
                } else {
                    mIOB1.setText(getString(R.string.activity_IOB));
                    mIOB2.setText(rawData.sIOB1);
                }
            } else {
                mIOB1.setVisibility(View.GONE);
                mIOB2.setVisibility(View.GONE);
            }
            //deal with cases where there is only the value shown for IOB, and not the label
        } else if (mIOB2 != null) {
            if (sharedPrefs.getBoolean("show_iob", true)) {
                mIOB2.setVisibility(View.VISIBLE);
                if (rawData.detailedIOB) {
                    mIOB2.setText(rawData.sIOB2);
                } else {
                    mIOB2.setText(rawData.sIOB1);
                }
            } else {
                mIOB2.setText("");
            }
        }

        if (mTimestamp != null) {
            if (sharedPrefs.getBoolean("showAgo", true)) {
                if (isAAPSv2 != null) {
                    mTimestamp.setText(readingAge(true));
                } else {
                    if (sharedPrefs.getBoolean("showExternalStatus", true)) {
                        mTimestamp.setText(readingAge(true));
                    } else {
                        mTimestamp.setText(readingAge(false));
                    }
                }
                mTimestamp.setVisibility(View.VISIBLE);
            } else {
                mTimestamp.setVisibility(View.GONE);
            }
        }

        if (mUploaderBattery != null) {
            if (sharedPrefs.getBoolean("show_uploader_battery", true)) {
                if (isAAPSv2 != null) {
                    mUploaderBattery.setText(rawData.sUploaderBattery + "%");
                    mUploaderBattery.setVisibility(View.VISIBLE);
                } else {
                    if (sharedPrefs.getBoolean("showExternalStatus", true)) {
                        mUploaderBattery.setText("U: " + rawData.sUploaderBattery + "%");
                    } else {
                        mUploaderBattery.setText("Uploader: " + rawData.sUploaderBattery + "%");
                    }
                }
            } else {
                mUploaderBattery.setVisibility(View.GONE);
            }
        }

        if (mRigBattery != null) {
            if (sharedPrefs.getBoolean("show_rig_battery", false)) {
                mRigBattery.setText(rawData.sRigBattery);
                mRigBattery.setVisibility(View.VISIBLE);
            } else {
                mRigBattery.setVisibility(View.GONE);
            }
        }

        if (mBasalRate != null) {
            if (sharedPrefs.getBoolean("show_temp_basal", true)) {
                mBasalRate.setText(rawData.sBasalRate);
                mBasalRate.setVisibility(View.VISIBLE);
            } else {
                mBasalRate.setVisibility(View.GONE);
            }
        }

        if (mBgi != null) {
            if (rawData.showBGI) {
                mBgi.setText(rawData.sBgi);
                mBgi.setVisibility(View.VISIBLE);
            } else {
                mBgi.setVisibility(View.GONE);
            }
        }

        if (mStatus != null) {
            if (sharedPrefs.getBoolean("showExternalStatus", true)) {
                mStatus.setText(rawData.externalStatusString);
                mStatus.setVisibility(View.VISIBLE);
            } else {
                mStatus.setVisibility(View.GONE);
            }
        }

        if (mLoop != null) {
            if (sharedPrefs.getBoolean("showExternalStatus", true)) {
                mLoop.setVisibility(View.VISIBLE);
                if (rawData.openApsStatus != -1) {
                    int mins = (int) ((System.currentTimeMillis() - rawData.openApsStatus) / 1000 / 60);
                    mLoop.setText(mins + "'");
                    if (mins > 14) {
                        loopLevel = 0;
                        mLoop.setBackgroundResource(R.drawable.loop_red_25);
                    } else {
                        loopLevel = 1;
                        mLoop.setBackgroundResource(R.drawable.loop_green_25);
                    }
                } else {
                    mLoop.setText("-'");
                }
            } else {
                mLoop.setVisibility(View.GONE);
            }
        }
    }

    public void setDateAndTime() {

        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(BaseWatchFace.this);
        if (mTime != null) {
            mTime.setText(timeFormat.format(System.currentTimeMillis()));
        }

        Date now = new Date();
        SimpleDateFormat sdfHour = new SimpleDateFormat("HH");
        SimpleDateFormat sdfMinute = new SimpleDateFormat("mm");
        sHour = sdfHour.format(now);
        sMinute = sdfMinute.format(now);

        if (mHour != null && mMinute != null) {
            mHour.setText(sHour);
            mMinute.setText(sMinute);
        }

        if (mDate != null && mDay != null && mMonth != null) {
            if (sharedPrefs.getBoolean("show_date", false)) {
                if (mDayName != null) {
                    SimpleDateFormat sdfDayName = new SimpleDateFormat("E");
                    mDayName.setText(sdfDayName.format(now));
                }

                SimpleDateFormat sdfDay = new SimpleDateFormat("dd");
                SimpleDateFormat sdfMonth = new SimpleDateFormat("MMM");
                mDay.setText(sdfDay.format(now));
                mMonth.setText(sdfMonth.format(now));
                mDate.setVisibility(View.VISIBLE);
            } else {
                mDate.setVisibility(View.GONE);
            }
        }
    }

    public void setColor() {
        dividerMatchesBg = sharedPrefs.getBoolean("match_divider", false);
        if (lowResMode) {
            setColorLowRes();
        } else if (sharedPrefs.getBoolean("dark", true)) {
            setColorDark();
        } else {
            setColorBright();
        }
    }

    public void strikeThroughSgvIfNeeded() {
        if (mSgv != null) {
            if (sharedPrefs.getBoolean("showBG", true)) {
                if (ageLevel() <= 0) {
                    mSgv.setPaintFlags(mSgv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    mSgv.setPaintFlags(mSgv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                }
            }
        }
    }

    protected void onWatchModeChanged(WatchMode watchMode) {

        if (lowResMode ^ isLowRes(watchMode)) { //if there was a change in lowResMode
            lowResMode = isLowRes(watchMode);
            setColor();
        } else if (!sharedPrefs.getBoolean("dark", true)) {
            //in bright mode: different colours if active:
            setColor();
        }
    }

    private boolean isLowRes(WatchMode watchMode) {
        return (watchMode == WatchMode.LOW_BIT) || (watchMode == WatchMode.LOW_BIT_BURN_IN); // || (watchMode == WatchMode.LOW_BIT_BURN_IN);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        if ("delta_granularity".equals(key)) {
            ListenerService.requestData(this);
        }

        if (layoutSet) {
            setDataFields();
            setColor();
        }
        invalidate();
    }

    protected abstract void setColorDark();

    protected abstract void setColorBright();

    protected abstract void setColorLowRes();

    public void missedReadingAlert() {
        int minutes_since = (int) Math.floor(timeSince() / (1000 * 60));
        if (minutes_since >= 16 && ((minutes_since - 16) % 5) == 0) {
            ListenerService.requestData(this); // attempt endTime recover missing data
        }
    }

    public void setupCharts() {
        if (rawData.bgDataList.size() > 0) { //Dont crash things just because we dont have values, people dont like crashy things
            int timeframe = Integer.parseInt(sharedPrefs.getString("chart_timeframe", "3"));
            if (lowResMode) {
                bgGraphBuilder = new BgGraphBuilder(getApplicationContext(), rawData, pointSize, midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, Color.GREEN, timeframe);
            } else {
                bgGraphBuilder = new BgGraphBuilder(getApplicationContext(), rawData, pointSize, highColor, lowColor, midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, Color.GREEN, timeframe);
            }

            chart.setLineChartData(bgGraphBuilder.lineData());
            chart.setViewportCalculationEnabled(true);
            chart.setMaximumViewport(chart.getMaximumViewport());
        }
    }

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (layoutSet) {
                final DataMap dataMap = rawData.updateDataFromMessage(intent, wakeLock);
                if (chart != null && dataMap != null) {
                    rawData.addToWatchSet(dataMap);
                    setupCharts();
                }
                rawData.updateStatusFromMessage(intent, wakeLock);
            }

            setDataFields();
            setColor();

            if (layoutSet) {
                rawData.updateBasalsFromMessage(intent, wakeLock);
            }

            mRelativeLayout.measure(specW, specH);
            if (forceSquareCanvas) {
                mRelativeLayout.layout(0, 0, displaySize.x, displaySize.x);  //force a square for Steampunk watch face.
            } else {
                mRelativeLayout.layout(0, 0, displaySize.x, displaySize.y);
            }
            invalidate();
        }
    }

}
