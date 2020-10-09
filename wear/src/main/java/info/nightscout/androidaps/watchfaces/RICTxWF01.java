package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Vibrator;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.ustwo.clockwise.common.WatchFaceTime;
import com.ustwo.clockwise.common.WatchMode;

import java.text.SimpleDateFormat;
import java.util.Date;

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
            String displayStyle=sharedPrefs.getString("rictxwf01_frameStyle", "full");
            String displayFrameColor=sharedPrefs.getString("rictxwf01_frameColor", "red");
            String displayFrameColorSaturation=sharedPrefs.getString("rictxwf01_frameColorSaturation", "500");
            String displayFrameColorOpacity=sharedPrefs.getString("rictxwf01_frameColorOpacity", "1");

            // Load image with shapes
            String styleDrawableName = "rictxwf01_bg_" + displayStyle + "_" + displayFormatType;
            try {
                mShapesElements.setBackground(getResources().getDrawable(getResources().getIdentifier(styleDrawableName, "drawable", getApplicationContext().getPackageName())));
            } catch (Exception e) {
                Log.e("rictxwf01_frameStyle", "RESOURCE NOT FOUND >> " + styleDrawableName);
            }

            // set background-tint-color
            if (displayFrameColor.equalsIgnoreCase("multicolor") || displayStyle.equalsIgnoreCase("none")) {
                mShapesElements.setBackgroundTintList(null);
            } else {
                String strColorName =((   displayFrameColor.equals("white") || displayFrameColor.equals("black")  )?displayFrameColor:displayFrameColor+"_"+displayFrameColorSaturation);
                Log.v("rictxwf01_strColorName",strColorName);
                try {
                    ColorStateList colorStateList = ContextCompat.getColorStateList(getApplicationContext(), getResources().getIdentifier(strColorName, "color", getApplicationContext().getPackageName()));
                    mShapesElements.setBackgroundTintList(colorStateList);
                } catch (Exception e) {
                    mShapesElements.setBackgroundTintList(null);
                    Log.e("rictxwf01_ColorName", "COLOR NOT FOUND >> " + strColorName);
                }
            }

            // set opacity of shapes
            mShapesElements.setAlpha(Float.parseFloat(displayFrameColorOpacity));

        }

        /* optimize font-size  --> when date is off then increase font-size of time */
        Boolean isShowDate = sharedPrefs.getBoolean("show_date", false);
        if (!isShowDate) {
            layoutView.findViewById(R.id.date_time).setVisibility(View.GONE);
            mHour.setTextSize(62);
            mMinute.setTextSize(40);
            mHour.setLetterSpacing((float) -0.066);
            mMinute.setLetterSpacing((float) -0.066);
        } else {
            layoutView.findViewById(R.id.date_time).setVisibility(View.VISIBLE);
            mHour.setTextSize(40);
            mMinute.setTextSize(26);
            mHour.setLetterSpacing((float) 0);
            mMinute.setLetterSpacing((float) 0);

            /* display week number */
            Boolean isShowWeekNumber = sharedPrefs.getBoolean("show_weeknumber", false);
            Log.i("---------------------------------","weeknumber refresh ");
            TextView mWeekNumber= layoutView.findViewById(R.id.weeknumber);
            if (isShowWeekNumber) {
                mWeekNumber.setVisibility(View.VISIBLE);
                mWeekNumber.setText("(" + (new SimpleDateFormat("ww")).format(new Date()) + ")");
            } else {
                mWeekNumber.setVisibility(View.GONE);
                mWeekNumber.setText("");
            }
        }


        /* @ToDo optimize font-size  --> when direction and time-ago is off, increase font-size of sgv */

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


    @Override
    protected void onTimeChanged(WatchFaceTime oldTime, WatchFaceTime newTime) {
        super.onTimeChanged(oldTime,newTime);

        /* hourly vibration*/
        Boolean hourlyVibratePref = sharedPrefs.getBoolean("rictxwf01_vibrateHourly", false);
        if (hourlyVibratePref && layoutSet && newTime.hasHourChanged(oldTime)) {
            Log.i("hourlyVibratePref", "true --> " + newTime.toString());
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] vibrationPattern = {0, 150, 125, 100};
            vibrator.vibrate(vibrationPattern, -1);
        }

    }
}
