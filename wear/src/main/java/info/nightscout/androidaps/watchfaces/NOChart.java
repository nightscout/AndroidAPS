package info.nightscout.androidaps.watchfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.wearable.DataMap;
import com.ustwo.clockwise.common.WatchFaceTime;
import com.ustwo.clockwise.common.WatchMode;
import com.ustwo.clockwise.common.WatchShape;
import com.ustwo.clockwise.wearable.WatchFace;

import java.util.ArrayList;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.BasalWatchData;
import info.nightscout.androidaps.data.BgWatchData;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.data.TempWatchData;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;

/**
 * Created by adrianLxM.
 */
public class NOChart extends WatchFace implements SharedPreferences.OnSharedPreferenceChangeListener {
    public final static IntentFilter INTENT_FILTER;
    public static final int SCREENSIZE_SMALL = 280;
    public TextView mTime, mSgv, mTimestamp, mDelta, mAvgDelta;
    public RelativeLayout mRelativeLayout;
    public long sgvLevel = 0;
    public int batteryLevel = 1;
    public int ageLevel = 1;
    public boolean lowResMode = false;
    public boolean layoutSet = false;
    public long datetime;
    public ArrayList<BgWatchData> bgDataList = new ArrayList<>();
    public ArrayList<TempWatchData> tempWatchDataList = new ArrayList<>();
    public ArrayList<BasalWatchData> basalWatchDataList = new ArrayList<>();
    public PowerManager.WakeLock wakeLock;
    public View layoutView;
    private final Point displaySize = new Point();
    private int specW, specH;
    private int animationAngle = 0;
    private boolean isAnimated = false;

    private LocalBroadcastManager localBroadcastManager;
    private MessageReceiver messageReceiver;

    protected SharedPreferences sharedPrefs;
    private String sgvString = "--";
    private String externalStatusString = "no status";
    private TextView statusView;
    private long sgvTapTime = 0l;

    @Override
    public void onCreate() {
        super.onCreate();
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        display.getSize(displaySize);
        wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:NOChart");

        specW = View.MeasureSpec.makeMeasureSpec(displaySize.x,
                View.MeasureSpec.EXACTLY);
        specH = View.MeasureSpec.makeMeasureSpec(displaySize.y,
                View.MeasureSpec.EXACTLY);
        sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_nochart, null);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        if(metrics.widthPixels < SCREENSIZE_SMALL || metrics.heightPixels < SCREENSIZE_SMALL){
            layoutView = inflater.inflate(R.layout.activity_nochart_small, null);
        } else {
            layoutView = inflater.inflate(R.layout.activity_nochart, null);
        }
        performViewSetup();
    }

    @Override
    protected void onLayout(WatchShape shape, Rect screenBounds, WindowInsets screenInsets) {
        super.onLayout(shape, screenBounds, screenInsets);
        layoutView.onApplyWindowInsets(screenInsets);
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
                mSgv = (TextView) stub.findViewById(R.id.sgv);
                mTimestamp = (TextView) stub.findViewById(R.id.timestamp);
                mDelta = (TextView) stub.findViewById(R.id.delta);
                mAvgDelta = (TextView) stub.findViewById(R.id.avgdelta);
                mRelativeLayout = (RelativeLayout) stub.findViewById(R.id.main_layout);
                statusView = (TextView) stub.findViewById(R.id.aps_status);
                layoutSet = true;
                showAgeAndStatus();
                mRelativeLayout.measure(specW, specH);
                mRelativeLayout.layout(0, 0, mRelativeLayout.getMeasuredWidth(),
                        mRelativeLayout.getMeasuredHeight());
            }
        });
        ListenerService.requestData(this);
        wakeLock.acquire(50);
    }

    @Override
    protected void onTapCommand(int tapType, int x, int y, long eventTime) {

        int extra = mSgv!=null?(mSgv.getRight() - mSgv.getLeft())/2:0;

        if (tapType == TAP_TYPE_TAP&&
                x + extra >=mSgv.getLeft() &&
                x - extra <= mSgv.getRight()&&
                y >= mSgv.getTop() &&
                y <= mSgv.getBottom()){
            if (eventTime - sgvTapTime < 800){
                Intent intent = new Intent(this, MainMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            sgvTapTime = eventTime;
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
        return (watchMode == WatchMode.LOW_BIT) || (watchMode == WatchMode.LOW_BIT_BURN_IN) || (watchMode == WatchMode.LOW_BIT_BURN_IN);
    }


    @Override
    protected WatchFaceStyle getWatchFaceStyle(){
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
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
        if(localBroadcastManager != null && messageReceiver != null){
            localBroadcastManager.unregisterReceiver(messageReceiver);}
        if (sharedPrefs != null){
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
        if(layoutSet) {
            this.mRelativeLayout.draw(canvas);
            Log.d("onDraw", "draw");
        }
    }

    @Override
    protected void onTimeChanged(WatchFaceTime oldTime, WatchFaceTime newTime) {
        if (layoutSet && (newTime.hasHourChanged(oldTime) || newTime.hasMinuteChanged(oldTime))) {
            wakeLock.acquire(50);
            final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(NOChart.this);
            mTime.setText(timeFormat.format(System.currentTimeMillis()));
            showAgeAndStatus();

            if(ageLevel()<=0) {
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

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getBundleExtra("data");
            if (layoutSet && bundle !=null) {
                DataMap dataMap = DataMap.fromBundle(bundle);
                wakeLock.acquire(50);
                sgvLevel = dataMap.getLong("sgvLevel");
                batteryLevel = dataMap.getInt("batteryLevel");
                datetime = dataMap.getLong("timestamp");
                sgvString = dataMap.getString("sgvString");
                mSgv.setText(dataMap.getString("sgvString"));

                if(ageLevel()<=0) {
                    mSgv.setPaintFlags(mSgv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    mSgv.setPaintFlags(mSgv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                }

                final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(NOChart.this);
                mTime.setText(timeFormat.format(System.currentTimeMillis()));

                showAgeAndStatus();

                String delta = dataMap.getString("delta");

                if (delta.endsWith(" mg/dl")) {
                    mDelta.setText(delta.substring(0, delta.length() - 6));
                } else if (delta.endsWith(" mmol/l")||delta.endsWith(" mmol")) {
                    mDelta.setText(delta.substring(0, delta.length() - 5));
                } else {
                    mDelta.setText(delta);
                }


                String avgDelta = dataMap.getString("avgDelta");

                if (delta.endsWith(" mg/dl")) {
                    mAvgDelta.setText(avgDelta.substring(0, avgDelta.length() - 6));
                } else if (avgDelta.endsWith(" mmol/l")||avgDelta.endsWith(" mmol")) {
                    mAvgDelta.setText(avgDelta.substring(0, avgDelta.length() - 5));
                } else {
                    mAvgDelta.setText(avgDelta);
                }

                mRelativeLayout.measure(specW, specH);
                mRelativeLayout.layout(0, 0, mRelativeLayout.getMeasuredWidth(),
                        mRelativeLayout.getMeasuredHeight());
                invalidate();
                setColor();

                //start animation?
                // dataMap.getDataMapArrayList("entries") == null -> not on "resend data".
                if (!lowResMode && (sharedPrefs.getBoolean("animation", false) && dataMap.getDataMapArrayList("entries") == null && (sgvString.equals("100") || sgvString.equals("5.5") || sgvString.equals("5,5")))) {
                    startAnimation();
                }
            }
            //status
            bundle = intent.getBundleExtra("status");
            if (layoutSet && bundle != null) {
                DataMap dataMap = DataMap.fromBundle(bundle);
                wakeLock.acquire(50);
                externalStatusString = dataMap.getString("externalStatusString");

                showAgeAndStatus();

                mRelativeLayout.measure(specW, specH);
                mRelativeLayout.layout(0, 0, mRelativeLayout.getMeasuredWidth(),
                        mRelativeLayout.getMeasuredHeight());
                invalidate();
                setColor();
            }
            //basals and temps
            bundle = intent.getBundleExtra("basals");
            if (layoutSet && bundle != null) {
                DataMap dataMap = DataMap.fromBundle(bundle);
                wakeLock.acquire(500);

                loadBasalsAndTemps(dataMap);

                mRelativeLayout.measure(specW, specH);
                mRelativeLayout.layout(0, 0, mRelativeLayout.getMeasuredWidth(),
                        mRelativeLayout.getMeasuredHeight());
                invalidate();
                setColor();
            }
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
    }

    private void showAgeAndStatus() {

        if( mTimestamp != null){
            mTimestamp.setText(readingAge(true));
        }
        boolean showAvgDelta = sharedPrefs.getBoolean("showAvgDelta", true);

        if(showAvgDelta){
            mAvgDelta.setVisibility(View.VISIBLE);
        } else {
            mAvgDelta.setVisibility(View.GONE);
        }
            statusView.setText(externalStatusString);
            statusView.setVisibility(View.VISIBLE);
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



    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key){
        setColor();
        if(layoutSet){
            showAgeAndStatus();
            mRelativeLayout.measure(specW, specH);
            mRelativeLayout.layout(0, 0, mRelativeLayout.getMeasuredWidth(),
                    mRelativeLayout.getMeasuredHeight());
        }
        invalidate();
    }

    protected void updateRainbow() {
        animationAngle = (animationAngle + 1) % 360;
        //Animation matrix:
        int[] rainbow = {Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE
                , Color.CYAN};
        Shader shader = new LinearGradient(0, 0, 0, 20, rainbow,
                null, Shader.TileMode.MIRROR);
        Matrix matrix = new Matrix();
        matrix.setRotate(animationAngle);
        shader.setLocalMatrix(matrix);
        mSgv.getPaint().setShader(shader);
        invalidate();
    }

    private synchronized void setIsAnimated(boolean isAnimated) {
        this.isAnimated = isAnimated;
    }

    void startAnimation() {
        Log.d("CircleWatchface", "start startAnimation");

        Thread animator = new Thread() {


            public void run() {
                setIsAnimated(true);
                for (int i = 0; i <= 8 * 1000 / 40; i++) {
                    updateRainbow();
                    SystemClock.sleep(40);
                }
                mSgv.getPaint().setShader(null);
                setIsAnimated(false);
                invalidate();
                setColor();

                System.gc();
            }
        };

        animator.start();
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
        int minutes_since   = (int) Math.floor(timeSince()/(1000*60));
        if(minutes_since >= 16 && ((minutes_since - 16) % 5) == 0) {
            ListenerService.requestData(this); // attempt endTime recover missing data
        }
    }
}
