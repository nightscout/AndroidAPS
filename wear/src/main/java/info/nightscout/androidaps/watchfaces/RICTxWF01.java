package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.ustwo.clockwise.common.WatchMode;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;

/**
 * Created by rICTx-T1D on 16/Sep/20  (see https://github.com/rICTx-T1D)
 */

public class RICTxWF01 extends BaseWatchFace {
    private long chartTapTime = 0;
    private long sgvTapTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_rictxwf01, null);
        performViewSetup();
    }


    @Override
    protected void onTapCommand(int tapType, int x, int y, long eventTime) {
        //tapType = TAP_TYPE_TAP;
        Log.d("onTapCommand: DeviceWidth x DeviceHeight   ///  x , y, TapType  >> ", Integer.toString(getWidth()) + " x " + Integer.toString(getHeight()) + " ///  " + Integer.toString(x) + " , " + Integer.toString(y) + " , " + Integer.toString(tapType));

        if (tapType == TAP_TYPE_TAP) {
            if (eventTime - sgvTapTime < 800) {
                Intent intent = new Intent(this, MainMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            sgvTapTime = eventTime;
        }
    }


    @Override
    protected WatchFaceStyle getWatchFaceStyle() {
        return new WatchFaceStyle.Builder(this)
                .setAcceptsTapEvents(true)
                .setHideNotificationIndicator(false)
                .setShowUnreadCountIndicator(true)
                .build();
    }

    protected void setColorDark() {
        if (rawData.sgvLevel == 1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
        } else if (rawData.sgvLevel == 0) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else if (rawData.sgvLevel == -1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
        }

        if (ageLevel == 1) {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_TimestampOld));
        }

        if (rawData.batteryLevel == 1) {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_uploaderBatteryEmpty));
        }

        /* not be implement
        if (loopLevel == 1) {
            mLoop.setBackgroundResource(R.drawable.loop_green_25);
        } else {
            mLoop.setBackgroundResource(R.drawable.loop_red_25);
        }
        */

        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor);
            midColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            gridColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
            basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light);
            pointSize = 1;
            setupCharts();
        }

        /* frame styles*/
        LinearLayout mShapesElements = layoutView.findViewById(R.id.shapes_elements);
        if (mShapesElements != null) {
            String displayFormatType = (mShapesElements.getContentDescription().toString().startsWith("round") ? "round" : "rect");
            String styleDrawableName = "rictxwf01_bg_" + sharedPrefs.getString("rictxwf01_frameStyle", "red") + "_" + displayFormatType;
            Log.d("rictxwf01_frameStyle", styleDrawableName);
            try {
                mShapesElements.setBackground(getResources().getDrawable(getResources().getIdentifier(styleDrawableName, "drawable", getApplicationContext().getPackageName())));
            } catch (Exception e) {
                Log.e("rictxwf01_frameStyle", "RESOURCE NOT FOUND >> " + styleDrawableName);
            }
        }


        /* ToDo  Implement a configurable background image
         *  layoutView.setBackground();
         */


        /* ToDo  Implement hourly vibartion
        Boolean hourlyVibratePref = sharedPrefs.getBoolean("rictxwf01_vibrateHourly", false);
        Log.i("hourlyVibratePref",Boolean.toString(hourlyVibratePref));
        */


    }

    protected void setColorLowRes() {
        setColorDark();
    }

    protected void setColorBright() {
        if (getCurrentWatchMode() == WatchMode.INTERACTIVE) {
            setColorDark();
        } else {
            setColorDark();
        }
    }

}
