package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.LayoutInflater;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;

/**
 * Created by andrew-warrington on 01/12/2017.
 */

public class Steampunk extends BaseWatchFace {

    private long sgvTapTime = 0;
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

        if (mSgv != null) {

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
    }

    @Override
    protected WatchFaceStyle getWatchFaceStyle() {
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
    }

    protected void setColorDark() {

        //ensure the glucose dial is the right units
        if (!sUnits.equals("-")) {
            if (sUnits.equals("mmol")) {
                mGlucoseDial.setImageResource(R.drawable.steampunk_dial_mmol);
            } else {
                mGlucoseDial.setImageResource(R.drawable.steampunk_dial_mgdl);
            }
        }

        //rotate glucose dial.
        float rotationAngle = 0f;                                           //by default, show ? on the dial (? is at 0 degrees on the dial)
        if (!sSgv.equals("---")) {
            if (sUnits.equals("mmol")) {
                rotationAngle = Float.valueOf(sSgv) * 18f;  //convert to mg/dL, which is equivalent to degrees
            } else {
                rotationAngle = Float.valueOf(sSgv);       //if glucose a value is received, use it to determine the amount of rotation of the dial.
            }
        }
        if (rotationAngle > 330) rotationAngle = 330;                       //if the glucose value is higher than 330 then show "HIGH" on the dial. ("HIGH" is at 330 degrees on the dial)
        if (rotationAngle != 0 && rotationAngle < 30) rotationAngle = 30;   //if the glucose value is lower than 30 show "LOW" on the dial. ("LOW" is at 30 degrees on the dial)

        RotateAnimation rotate = new RotateAnimation(
                lastEndDegrees, rotationAngle - lastEndDegrees,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setFillAfter(true);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setDuration(2000);
        mGlucoseDial.startAnimation(rotate);
        lastEndDegrees = rotationAngle;     //store the final angle as a starting point for the next rotation.

        //set the delta gauge and rotate the delta pointer
        float deltaIsNegative = 1f;         //by default go clockwise
        if (!sAvgDelta.equals("--")) {      //if a legitimate delta value is received, then...
            if (sAvgDelta.substring(0,1).equals("-")) deltaIsNegative = -1f;  //if the delta is negative, go counter-clockwise

            //ensure the delta gauge is the right units and granularity
            if (!sUnits.equals("-")) {
                if (sUnits.equals("mmol")) {
                    if (sharedPrefs.getString("delta_granularity", "2").equals("1")) {  //low
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mmol_10);
                        deltaRotationAngle = (Float.valueOf(sAvgDelta.substring(1)) * 30f);   //get rid of the sign so it can be converted to float.
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("2")) {  //medium
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mmol_05);
                        deltaRotationAngle = (Float.valueOf(sAvgDelta.substring(1)) * 60f);   //get rid of the sign so it can be converted to float.
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("3")) {  //high
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mmol_03);
                        deltaRotationAngle = (Float.valueOf(sAvgDelta.substring(1)) * 100f);   //get rid of the sign so it can be converted to float.
                    }
                } else {
                    if (sharedPrefs.getString("delta_granularity", "2").equals("1")) {  //low
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_20);
                        deltaRotationAngle = (Float.valueOf(sAvgDelta.substring(1)) * 1.5f);   //get rid of the sign so it can be converted to float.
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("2")) {  //medium
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_10);
                        deltaRotationAngle = (Float.valueOf(sAvgDelta.substring(1)) * 3f);   //get rid of the sign so it can be converted to float.
                    }
                    if (sharedPrefs.getString("delta_granularity", "2").equals("3")) {  //high
                        mLinearLayout.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_5);
                        deltaRotationAngle = (Float.valueOf(sAvgDelta.substring(1)) * 6f);   //get rid of the sign so it can be converted to float.
                    }
                }
            }
            if (deltaRotationAngle > 35) deltaRotationAngle = 35f;
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
            basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light);
            pointSize = 2;
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

        if (bIsRound) {
            mCOB2.setTextSize(13);
            mBasalRate.setTextSize(13);
            mIOB2.setTextSize(12);
            mTimestamp.setTextSize(12);
            mLoop.setTextSize(12);
            mUploaderBattery.setTextSize(11);
            mRigBattery.setTextSize(11);
        } else {
            mCOB2.setTextSize(11);
            mBasalRate.setTextSize(11);
            mIOB2.setTextSize(10);
            mTimestamp.setTextSize(9);
            mLoop.setTextSize(9);
            mUploaderBattery.setTextSize(9);
            mRigBattery.setTextSize(9);
        }
    }
}