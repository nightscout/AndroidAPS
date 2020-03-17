package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import androidx.core.content.ContextCompat;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.LayoutInflater;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;
import info.nightscout.androidaps.interaction.utils.SafeParse;
/**
 * Created by andrew-warrington on 01/12/2017.
 */

public class Steampunk extends BaseWatchFace {

    private long chartTapTime = 0;
    private long mainMenuTapTime = 0;
    private float lastEndDegrees = 0f;
    private float deltaRotationAngle = 0f;

    @Override
    public void onCreate() {
        forceSquareCanvas = true;
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_steampunk, null);
        performViewSetup();
    }

    @Override
    protected void onTapCommand(int tapType, int x, int y, long eventTime) {

        if (tapType == TAP_TYPE_TAP&&
                x >= mChartTap.getLeft() &&
                x <= mChartTap.getRight()&&
                y >= mChartTap.getTop() &&
                y <= mChartTap.getBottom()){
            if (eventTime - chartTapTime < 800){
                changeChartTimeframe();
            }
            chartTapTime = eventTime;

        } else if (tapType == TAP_TYPE_TAP&&
                x >= mMainMenuTap.getLeft() &&
                x <= mMainMenuTap.getRight()&&
                y >= mMainMenuTap.getTop() &&
                y <= mMainMenuTap.getBottom()){
            if (eventTime - mainMenuTapTime < 800){
                Intent intent = new Intent(this, MainMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            mainMenuTapTime = eventTime;
        }
    }

    @Override
    protected WatchFaceStyle getWatchFaceStyle() {
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
    }

    protected void setColorDark() {

        if (mLinearLayout2 != null) {
            if (ageLevel() <= 0) {
                mLinearLayout2.setBackgroundResource(R.drawable.redline);
                mTimestamp.setTextColor(getResources().getColor(R.color.red_600));
            } else {
                mLinearLayout2.setBackgroundResource(0);
                mTimestamp.setTextColor(getResources().getColor(R.color.black_86p));
            }
        }

        if (mLoop != null) {
            if (loopLevel == 0) {
                mLoop.setTextColor(getResources().getColor(R.color.red_600));
            } else {
                mLoop.setTextColor(getResources().getColor(R.color.black_86p));
            }
        }

        if (!rawData.sSgv.equals("---")) {

            float rotationAngle = 0f;                                           //by default, show ? on the dial (? is at 0 degrees on the dial)

            if (!rawData.sUnits.equals("-")) {

                //ensure the glucose dial is the correct units
                if (rawData.sUnits.equals("mmol")) {
                    mGlucoseDial.setImageResource(R.drawable.steampunk_dial_mmol);
                } else {
                    mGlucoseDial.setImageResource(R.drawable.steampunk_dial_mgdl);
                }

                //convert the Sgv to degrees of rotation
                if (rawData.sUnits.equals("mmol")) {
                    rotationAngle = Float.valueOf(rawData.sSgv) * 18f;  //convert to mg/dL, which is equivalent to degrees
                } else {
                    rotationAngle = Float.valueOf(rawData.sSgv);       //if glucose a value is received, use it to determine the amount of rotation of the dial.
                }

            }

            if (rotationAngle > 330) rotationAngle = 330;                       //if the glucose value is higher than 330 then show "HIGH" on the dial. ("HIGH" is at 330 degrees on the dial)
            if (rotationAngle != 0 && rotationAngle < 30) rotationAngle = 30;   //if the glucose value is lower than 30 show "LOW" on the dial. ("LOW" is at 30 degrees on the dial)

            //rotate glucose dial
            RotateAnimation rotate = new RotateAnimation(
                    lastEndDegrees, rotationAngle - lastEndDegrees,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            rotate.setFillAfter(true);
            rotate.setInterpolator(new LinearInterpolator());
            rotate.setDuration(1);
            mGlucoseDial.startAnimation(rotate);
            lastEndDegrees = rotationAngle;     //store the final angle as a starting point for the next rotation.
        }

        //set the delta gauge and rotate the delta pointer
        float deltaIsNegative = 1f;         //by default go clockwise
        if (!rawData.sAvgDelta.equals("--")) {      //if a legitimate delta value is received, then...
            if (rawData.sAvgDelta.substring(0,1).equals("-")) deltaIsNegative = -1f;  //if the delta is negative, go counter-clockwise
            Float AbssAvgDelta = SafeParse.stringToFloat(rawData.sAvgDelta.substring(1)) ;   //get rid of the sign so it can be converted to float.
            String autogranularity = "0" ;                                                   //autogranularity off
            //ensure the delta gauge is the right units and granularity
            if (!rawData.sUnits.equals("-")) {
                if (rawData.sUnits.equals("mmol")) {
                    if (sharedPrefs.getString("delta_granularity", "2").equals("4")) {  //Auto granularity
                        autogranularity = "1";                                                  // low (init)
                        if (AbssAvgDelta < 0.3 ) {
                            autogranularity = "3" ;                                             // high if below 0.3 mmol/l
                        } else if (AbssAvgDelta < 0.5) {
                            autogranularity = "2" ;                                             // medium if below 0.5 mmol/l
                        }
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("1") || autogranularity.equals("1")) {  //low
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mmol_10);
                        deltaRotationAngle = (AbssAvgDelta * 30f);
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("2") || autogranularity.equals("2")) {  //medium
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mmol_05);
                        deltaRotationAngle = (AbssAvgDelta * 60f);
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("3") || autogranularity.equals("3")) {  //high
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mmol_03);
                        deltaRotationAngle = (AbssAvgDelta * 100f);
                    }
                } else {
                    if (sharedPrefs.getString("delta_granularity", "2").equals("4")) {  //Auto granularity
                        autogranularity = "1";                                                  // low (init)
                        if (AbssAvgDelta < 5 ) {
                            autogranularity = "3" ;                                             // high if below 5 mg/dl
                        } else if (AbssAvgDelta < 10) {
                            autogranularity = "2" ;                                             // medium if below 10 mg/dl
                        }
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("1") || autogranularity.equals("1")) {  //low
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_20);
                        deltaRotationAngle = (AbssAvgDelta * 1.5f);
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("2") || autogranularity.equals("2")) {  //medium
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_10);
                        deltaRotationAngle = (AbssAvgDelta * 3f);
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("3") || autogranularity.equals("3")) {  //high
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_5);
                        deltaRotationAngle = (AbssAvgDelta * 6f);
                    }
                }
            }
            if (deltaRotationAngle > 40) deltaRotationAngle = 40f;
            mDeltaGauge.setRotation(deltaRotationAngle * deltaIsNegative);
        }

        //rotate the minute hand.
        mMinuteHand.setRotation(Float.valueOf(sMinute) * 6f);

        //rotate the hour hand.
        mHourHand.setRotation((Float.valueOf(sHour) * 30f) + (Float.valueOf(sMinute) * 0.5f));

        setTextSizes();

        if (mLoop != null) {
            mLoop.setBackgroundResource(0);
        }

        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.black);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.black);
            midColor = ContextCompat.getColor(getApplicationContext(), R.color.black);
            gridColor = ContextCompat.getColor(getApplicationContext(), R.color.grey_steampunk);
            basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
            basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
            if (Integer.parseInt(sharedPrefs.getString("chart_timeframe", "3")) < 3) {
                pointSize = 2;
            } else {
                pointSize = 1;
            }
            setupCharts();
        }

        invalidate();

    }

    protected void setColorLowRes() {
        setColorDark();
    }

    protected void setColorBright() {
        setColorDark();
    }

    protected void setTextSizes() {

        float fontSmall = 10f;
        float fontMedium = 11f;
        float fontLarge = 12f;

        if (bIsRound) {
            fontSmall = 11f;
            fontMedium = 12f;
            fontLarge = 13f;
        }

        //top row. large font unless text too big (i.e. detailedIOB)
        mCOB2.setTextSize(fontLarge);
        mBasalRate.setTextSize(fontLarge);
        if (rawData.sIOB2.length() < 7) {
            mIOB2.setTextSize(fontLarge);
        } else {
            mIOB2.setTextSize(fontSmall);
        }

        //bottom row. font medium unless text too long (i.e. longer than 9' timestamp)
        if (mTimestamp.getText().length() < 3 || mLoop.getText().length() < 3) {     //always resize these fields together, for symmetry.
            mTimestamp.setTextSize(fontMedium);
            mLoop.setTextSize(fontMedium);
        } else {
            mTimestamp.setTextSize(fontSmall);
            mLoop.setTextSize(fontSmall);
        }

        //if both batteries are shown, make them smaller.
        if (sharedPrefs.getBoolean("show_uploader_battery", true) && sharedPrefs.getBoolean("show_rig_battery", false)) {
            mUploaderBattery.setTextSize(fontSmall);
            mRigBattery.setTextSize(fontSmall);
        } else {
            mUploaderBattery.setTextSize(fontMedium);
            mRigBattery.setTextSize(fontMedium);
        }
    }

    private void changeChartTimeframe() {
        int timeframe = Integer.parseInt(sharedPrefs.getString("chart_timeframe", "3"));
        timeframe = (timeframe%5) + 1;
        if (timeframe < 3) {
            pointSize = 2;
        } else {
            pointSize = 1;
        }
        setupCharts();
        sharedPrefs.edit().putString("chart_timeframe", "" + timeframe).commit();
    }
}