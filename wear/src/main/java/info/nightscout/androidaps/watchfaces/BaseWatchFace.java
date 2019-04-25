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
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
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

import com.google.android.gms.wearable.DataMap;
import com.ustwo.clockwise.common.WatchMode;
import com.ustwo.clockwise.wearable.WatchFace;
import com.ustwo.clockwise.common.WatchFaceTime;
import com.ustwo.clockwise.common.WatchShape;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import info.nightscout.androidaps.data.BasalWatchData;
import info.nightscout.androidaps.data.BgWatchData;
import info.nightscout.androidaps.data.BolusWatchData;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.TempWatchData;
import lecho.lib.hellocharts.view.LineChartView;

/**
 * Created by emmablack on 12/29/14.
 * Updated by andrew-warrington on 02-Jan-2018.
 */

public  abstract class BaseWatchFace extends WatchFace implements SharedPreferences.OnSharedPreferenceChangeListener {
    public final static IntentFilter INTENT_FILTER;
    public static final long[] vibratePattern = {0,400,300,400,300,400};
    public TextView mTime, mSgv, mDirection, mTimestamp, mUploaderBattery, mRigBattery, mDelta, mAvgDelta, mStatus, mBasalRate, mIOB1, mIOB2, mCOB1, mCOB2, mBgi, mLoop, mDay, mMonth, isAAPSv2, mHighLight, mLowLight;
    public ImageView mGlucoseDial, mDeltaGauge, mHourHand, mMinuteHand;
    public long datetime;
    public RelativeLayout mRelativeLayout;
    public LinearLayout mLinearLayout, mLinearLayout2, mDate, mChartTap, mMainMenuTap;
    public long sgvLevel = 0;
    public int ageLevel = 1;
    public int loopLevel = 1;
    public int batteryLevel = 1;
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
    public int pointSize = 2;
    public BgGraphBuilder bgGraphBuilder;
    public LineChartView chart;
    public ArrayList<BgWatchData> bgDataList = new ArrayList<>();
    public ArrayList<TempWatchData> tempWatchDataList = new ArrayList<>();
    public ArrayList<BasalWatchData> basalWatchDataList = new ArrayList<>();
    public ArrayList<BolusWatchData> bolusWatchDataList = new ArrayList<>();
    public ArrayList<BgWatchData> predictionList = new ArrayList<>();

    public PowerManager.WakeLock wakeLock;
    // related endTime manual layout
    public View layoutView;
    public final Point displaySize = new Point();
    public int specW, specH;
    private LocalBroadcastManager localBroadcastManager;
    private MessageReceiver messageReceiver;

    protected SharedPreferences sharedPrefs;

    public boolean detailedIOB = false;
    public boolean showBGI = false;
    public boolean forceSquareCanvas = false;  //set to true by the Steampunk watch face.
    public long openApsStatus;
    public String externalStatusString = "no status";
    public String sSgv = "---";
    public String sDirection = "--";
    public String sUploaderBattery = "--";
    public String sRigBattery = "--";
    public String sDelta = "--";
    public String sAvgDelta = "--";
    public String sBasalRate = "-.--U/h";
    public String sIOB1 = "IOB";
    public String sIOB2 = "-.--";
    public String sCOB1 = "Carb";
    public String sCOB2 = "--g";
    public String sBgi = "--";
    public String sMinute = "0";
    public String sHour = "0";
    public String sUnits = "-";

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
    }

    @Override
    protected void onLayout(WatchShape shape, Rect screenBounds, WindowInsets screenInsets) {
        super.onLayout(shape, screenBounds, screenInsets);
        layoutView.onApplyWindowInsets(screenInsets);
        bIsRound = screenInsets.isRound();
    }

    public void performViewSetup() {
        final WatchViewStub stub = (WatchViewStub) layoutView.findViewById(R.id.watch_view_stub);
        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);

        messageReceiver = new MessageReceiver();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(messageReceiver, messageFilter);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTime = (TextView) stub.findViewById(R.id.watch_time);
                mDay = (TextView) stub.findViewById(R.id.day);
                mMonth = (TextView) stub.findViewById(R.id.month);
                mDate = (LinearLayout) stub.findViewById(R.id.date_time);
                mLoop = (TextView) stub.findViewById(R.id.loop);
                mSgv = (TextView) stub.findViewById(R.id.sgv);
                mDirection = (TextView) stub.findViewById(R.id.direction);
                mTimestamp = (TextView) stub.findViewById(R.id.timestamp);
                mIOB1 = (TextView) stub.findViewById(R.id.iob_text);
                mIOB2 = (TextView) stub.findViewById(R.id.iobView);
                mCOB1 = (TextView) stub.findViewById(R.id.cob_text);
                mCOB2 = (TextView) stub.findViewById(R.id.cobView);
                mBgi =  (TextView) stub.findViewById(R.id.bgiView);
                mStatus = (TextView) stub.findViewById(R.id.externaltstatus);
                mBasalRate = (TextView) stub.findViewById(R.id.tmpBasal);
                mUploaderBattery = (TextView) stub.findViewById(R.id.uploader_battery);
                mRigBattery = (TextView) stub.findViewById(R.id.rig_battery);
                mDelta = (TextView) stub.findViewById(R.id.delta);
                mAvgDelta = (TextView) stub.findViewById(R.id.avgdelta);
                isAAPSv2 = (TextView) stub.findViewById(R.id.AAPSv2);
                mHighLight = (TextView) stub.findViewById(R.id.highLight);
                mLowLight = (TextView) stub.findViewById(R.id.lowLight);
                mRelativeLayout = (RelativeLayout) stub.findViewById(R.id.main_layout);
                mLinearLayout = (LinearLayout) stub.findViewById(R.id.secondary_layout);
                mLinearLayout2 = (LinearLayout) stub.findViewById(R.id.tertiary_layout);
                mGlucoseDial = (ImageView) stub.findViewById(R.id.glucose_dial);
                mDeltaGauge = (ImageView) stub.findViewById(R.id.delta_pointer);
                mHourHand = (ImageView) stub.findViewById(R.id.hour_hand);
                mMinuteHand = (ImageView) stub.findViewById(R.id.minute_hand);
                mChartTap = (LinearLayout) stub.findViewById(R.id.chart_zoom_tap);
                mMainMenuTap = (LinearLayout) stub.findViewById(R.id.main_menu_tap);
                chart = (LineChartView) stub.findViewById(R.id.chart);
                layoutSet = true;

                setDataFields();
                setColor();
                }
            }
        );
        wakeLock.acquire(50);
    }

    public int ageLevel() {
        if(timeSince() <= (1000 * 60 * 12)) {
            return 1;
        } else {
            return 0;
        }
    }

    public double timeSince() {
        return System.currentTimeMillis() - datetime;
    }

    public String readingAge(boolean shortString) {
        if (datetime == 0) { return shortString?"--'":"-- Minute ago"; }
        int minutesAgo = (int) Math.floor(timeSince()/(1000*60));
        if (minutesAgo == 1) {
            return minutesAgo + (shortString?"'":" Minute ago");
        }
        return minutesAgo + (shortString?"'":" Minutes ago");
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

    static {
        INTENT_FILTER = new IntentFilter();
        INTENT_FILTER.addAction(Intent.ACTION_TIME_TICK);
        INTENT_FILTER.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        INTENT_FILTER.addAction(Intent.ACTION_TIME_CHANGED);
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

            mRelativeLayout.measure(specW, specH);
            if (forceSquareCanvas) {
                mRelativeLayout.layout(0, 0, displaySize.x, displaySize.x);  //force a square for Steampunk watch face.
            } else {
                mRelativeLayout.layout(0, 0, displaySize.x, displaySize.y);
            }
            invalidate();
        }
    }

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            Bundle bundle = intent.getBundleExtra("data");
            if (layoutSet && bundle != null) {
                DataMap dataMap = DataMap.fromBundle(bundle);
                wakeLock.acquire(50);
                sgvLevel = dataMap.getLong("sgvLevel");
                datetime = dataMap.getLong("timestamp");
                sSgv = dataMap.getString("sgvString");
                sDirection = dataMap.getString("slopeArrow");
                sDelta = dataMap.getString("delta");
                sAvgDelta = dataMap.getString("avgDelta");
                sUnits = dataMap.getString("glucoseUnits");
                if (chart != null) {
                    addToWatchSet(dataMap);
                    setupCharts();
                }
            }

            bundle = intent.getBundleExtra("status");
            if (layoutSet && bundle != null) {
                DataMap dataMap = DataMap.fromBundle(bundle);
                wakeLock.acquire(50);
                sBasalRate = dataMap.getString("currentBasal");
                sUploaderBattery = dataMap.getString("battery");
                sRigBattery = dataMap.getString("rigBattery");
                detailedIOB = dataMap.getBoolean("detailedIob");
                sIOB1 = dataMap.getString("iobSum") + "U";
                sIOB2 = dataMap.getString("iobDetail");
                sCOB1 = "Carb";
                sCOB2 = dataMap.getString("cob");
                sBgi = dataMap.getString("bgi");
                showBGI = dataMap.getBoolean("showBgi");
                externalStatusString = dataMap.getString("externalStatusString");
                batteryLevel = dataMap.getInt("batteryLevel");
                openApsStatus = dataMap.getLong("openApsStatus");
            }

            setDataFields();
            setColor();

            bundle = intent.getBundleExtra("basals");
            if (layoutSet && bundle != null) {
                DataMap dataMap = DataMap.fromBundle(bundle);
                wakeLock.acquire(500);
                loadBasalsAndTemps(dataMap);
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

    public void setDataFields() {

        setDateAndTime();

        if (mSgv != null) {
            if (sharedPrefs.getBoolean("showBG", true)) {
                mSgv.setText(sSgv);
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
                mDirection.setText(sDirection);
                mDirection.setVisibility(View.VISIBLE);
            } else {
                mDirection.setVisibility(View.GONE);
            }
        }

        if (mDelta != null) {
            if (sharedPrefs.getBoolean("showDelta", true)) {
                mDelta.setText(sDelta);
                mDelta.setVisibility(View.VISIBLE);
            } else {
                mDelta.setVisibility(View.GONE);
            }
        }

        if (mAvgDelta != null) {
            if (sharedPrefs.getBoolean("showAvgDelta", true)) {
                mAvgDelta.setText(sAvgDelta);
                mAvgDelta.setVisibility(View.VISIBLE);
            } else {
                mAvgDelta.setVisibility(View.GONE);
            }
        }

        if (mCOB1 != null && mCOB2 != null) {
            mCOB2.setText(sCOB2);
            if (sharedPrefs.getBoolean("show_cob", true)) {
                mCOB1.setVisibility(View.VISIBLE);
                mCOB2.setVisibility(View.VISIBLE);
            } else {
                mCOB1.setVisibility(View.GONE);
                mCOB2.setVisibility(View.GONE);
            }
        //deal with cases where there is only the value shown for COB, and not the label
        } else if (mCOB2 != null) {
            mCOB2.setText(sCOB2);
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
                if (detailedIOB) {
                    mIOB1.setText(sIOB1);
                    mIOB2.setText(sIOB2);
                } else {
                    mIOB1.setText("IOB");
                    mIOB2.setText(sIOB1);
                }
            } else {
                mIOB1.setVisibility(View.GONE);
                mIOB2.setVisibility(View.GONE);
            }
        //deal with cases where there is only the value shown for IOB, and not the label
        } else if (mIOB2 != null) {
            if (sharedPrefs.getBoolean("show_iob", true)) {
                mIOB2.setVisibility(View.VISIBLE);
                if (detailedIOB) {
                    mIOB2.setText(sIOB2);
                } else {
                    mIOB2.setText(sIOB1);
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
                        mUploaderBattery.setText(sUploaderBattery + "%");
                        mUploaderBattery.setVisibility(View.VISIBLE);
                } else {
                    if (sharedPrefs.getBoolean("showExternalStatus", true)) {
                        mUploaderBattery.setText("U: " + sUploaderBattery + "%");
                    } else {
                        mUploaderBattery.setText("Uploader: " + sUploaderBattery + "%");
                    }
                }
            } else {
                mUploaderBattery.setVisibility(View.GONE);
            }
        }

        if (mRigBattery != null) {
            if (sharedPrefs.getBoolean("show_rig_battery", false)) {
                mRigBattery.setText(sRigBattery);
                mRigBattery.setVisibility(View.VISIBLE);
            } else {
                mRigBattery.setVisibility(View.GONE);
            }
        }

        if (mBasalRate != null) {
            if (sharedPrefs.getBoolean("show_temp_basal", true)) {
                mBasalRate.setText(sBasalRate);
                mBasalRate.setVisibility(View.VISIBLE);
            } else {
                mBasalRate.setVisibility(View.GONE);
            }
        }

        if (mBgi != null) {
            if (showBGI) {
                mBgi.setText(sBgi);
                mBgi.setVisibility(View.VISIBLE);
            } else {
                mBgi.setVisibility(View.GONE);
            }
        }
        
        if (mStatus != null) {
            if (sharedPrefs.getBoolean("showExternalStatus", true)) {
                mStatus.setText(externalStatusString);
                mStatus.setVisibility(View.VISIBLE);
            } else {
                mStatus.setVisibility(View.GONE);
            }
        }

        if (mLoop != null) {
            if (sharedPrefs.getBoolean("showExternalStatus", true)) {
                mLoop.setVisibility(View.VISIBLE);
                if (openApsStatus != -1) {
                    int mins = (int) ((System.currentTimeMillis() - openApsStatus) / 1000 / 60);
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

        if (mDate != null && mDay != null && mMonth != null) {
            if (sharedPrefs.getBoolean("show_date", false)) {
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
        if(lowResMode){
            setColorLowRes();
        } else if (sharedPrefs.getBoolean("dark", true)) {
            setColorDark();
        } else {
            setColorBright();
        }
    }

    public void strikeThroughSgvIfNeeded() {
        if (mSgv !=null) {
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

        if(lowResMode ^ isLowRes(watchMode)){ //if there was a change in lowResMode
            lowResMode = isLowRes(watchMode);
            setColor();
        } else if (! sharedPrefs.getBoolean("dark", true)){
            //in bright mode: different colours if active:
            setColor();
        }
    }

    private boolean isLowRes(WatchMode watchMode) {
        return (watchMode == WatchMode.LOW_BIT) || (watchMode == WatchMode.LOW_BIT_BURN_IN); // || (watchMode == WatchMode.LOW_BIT_BURN_IN);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key){

        if("delta_granularity".equals(key)){
            ListenerService.requestData(this);
        }
        
        if(layoutSet){
            setDataFields();
            setColor();
        }
        invalidate();
    }

    protected abstract void setColorDark();
    protected abstract void setColorBright();
    protected abstract void setColorLowRes();

    public void missedReadingAlert() {
        int minutes_since   = (int) Math.floor(timeSince()/(1000*60));
        if(minutes_since >= 16 && ((minutes_since - 16) % 5) == 0) {
            ListenerService.requestData(this); // attempt endTime recover missing data
        }
    }

    public void addToWatchSet(DataMap dataMap) {

        ArrayList<DataMap> entries = dataMap.getDataMapArrayList("entries");
        if (entries != null) {
            bgDataList = new ArrayList<BgWatchData>();
            for (DataMap entry : entries) {
                double sgv = entry.getDouble("sgvDouble");
                double high = entry.getDouble("high");
                double low = entry.getDouble("low");
                long timestamp = entry.getLong("timestamp");
                int color = entry.getInt("color", 0);
                bgDataList.add(new BgWatchData(sgv, high, low, timestamp, color));
            }
        } else {
            double sgv = dataMap.getDouble("sgvDouble");
            double high = dataMap.getDouble("high");
            double low = dataMap.getDouble("low");
            long timestamp = dataMap.getLong("timestamp");
            int color = dataMap.getInt("color", 0);

            final int size = bgDataList.size();
            if (size > 0) {
                if (bgDataList.get(size - 1).timestamp == timestamp)
                    return; // Ignore duplicates.
            }

            bgDataList.add(new BgWatchData(sgv, high, low, timestamp, color));
        }

        for (int i = 0; i < bgDataList.size(); i++) {
            if (bgDataList.get(i).timestamp < (System.currentTimeMillis() - (1000 * 60 * 60 * 5))) {
                bgDataList.remove(i); //Get rid of anything more than 5 hours old
                break;
            }
        }
    }

    public void setupCharts() {
        if(bgDataList.size() > 0) { //Dont crash things just because we dont have values, people dont like crashy things
            int timeframe = Integer.parseInt(sharedPrefs.getString("chart_timeframe", "3"));
            if (lowResMode) {
                bgGraphBuilder = new BgGraphBuilder(getApplicationContext(), bgDataList, predictionList, tempWatchDataList, basalWatchDataList, bolusWatchDataList, pointSize, midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, Color.GREEN, timeframe);
            } else {
                bgGraphBuilder = new BgGraphBuilder(getApplicationContext(), bgDataList,predictionList, tempWatchDataList, basalWatchDataList, bolusWatchDataList, pointSize, highColor, lowColor, midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, Color.GREEN, timeframe);
            }

            chart.setLineChartData(bgGraphBuilder.lineData());
            chart.setViewportCalculationEnabled(true);
            chart.setMaximumViewport(chart.getMaximumViewport());
        }
    }

    private void loadBasalsAndTemps(DataMap dataMap) {
        ArrayList<DataMap> temps = dataMap.getDataMapArrayList("temps");
        if (temps != null) {
            tempWatchDataList = new ArrayList<>();
            for (DataMap temp : temps) {
                TempWatchData twd = new TempWatchData();
                twd.startTime = temp.getLong("starttime");
                twd.startBasal =  temp.getDouble("startBasal");
                twd.endTime = temp.getLong("endtime");
                twd.endBasal = temp.getDouble("endbasal");
                twd.amount = temp.getDouble("amount");
                tempWatchDataList.add(twd);
            }
        }
        ArrayList<DataMap> basals = dataMap.getDataMapArrayList("basals");
        if (basals != null) {
            basalWatchDataList = new ArrayList<>();
            for (DataMap basal : basals) {
                BasalWatchData bwd = new BasalWatchData();
                bwd.startTime = basal.getLong("starttime");
                bwd.endTime = basal.getLong("endtime");
                bwd.amount = basal.getDouble("amount");
                basalWatchDataList.add(bwd);
            }
        }
        ArrayList<DataMap> boluses = dataMap.getDataMapArrayList("boluses");
        if (boluses != null) {
            bolusWatchDataList = new ArrayList<>();
            for (DataMap bolus : boluses) {
                BolusWatchData bwd = new BolusWatchData();
                bwd.date = bolus.getLong("date");
                bwd.bolus = bolus.getDouble("bolus");
                bwd.carbs = bolus.getDouble("carbs");
                bwd.isSMB = bolus.getBoolean("isSMB");
                bwd.isValid = bolus.getBoolean("isValid");
                bolusWatchDataList.add(bwd);
            }
        }
        ArrayList<DataMap> predictions = dataMap.getDataMapArrayList("predictions");
        if (boluses != null) {
            predictionList = new ArrayList<>();
            for (DataMap prediction : predictions) {
                BgWatchData bwd = new BgWatchData();
                bwd.timestamp = prediction.getLong("timestamp");
                bwd.sgv = prediction.getDouble("sgv");
                bwd.color = prediction.getInt("color");
                predictionList.add(bwd);
            }
        }
    }
}
