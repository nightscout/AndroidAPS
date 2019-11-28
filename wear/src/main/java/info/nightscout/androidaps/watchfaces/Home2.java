package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.LayoutInflater;

import com.ustwo.clockwise.common.WatchMode;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;

public class Home2 extends BaseWatchFace {

    private long chartTapTime = 0;
    private long sgvTapTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_home_2, null);
        performViewSetup();
    }

    @Override
    protected void onTapCommand(int tapType, int x, int y, long eventTime) {

        int extra = mSgv!=null?(mSgv.getRight() - mSgv.getLeft())/2:0;

        if (tapType == TAP_TYPE_TAP&&
                x >=chart.getLeft() &&
                x <= chart.getRight()&&
                y >= chart.getTop() &&
                y <= chart.getBottom()){
            if (eventTime - chartTapTime < 800){
                changeChartTimeframe();
            }
            chartTapTime = eventTime;
        } else if (tapType == TAP_TYPE_TAP&&
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

    private void changeChartTimeframe() {
        int timeframe = Integer.parseInt(sharedPrefs.getString("chart_timeframe", "3"));
        timeframe = (timeframe%5) + 1;
        sharedPrefs.edit().putString("chart_timeframe", "" + timeframe).commit();
    }

    @Override
    protected WatchFaceStyle getWatchFaceStyle(){
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
    }

    protected void setColorDark() {
        @ColorInt final int dividerTxtColor = dividerMatchesBg ?
                ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor) : Color.BLACK;
        @ColorInt final int dividerBatteryOkColor = ContextCompat.getColor(getApplicationContext(),
                dividerMatchesBg ? R.color.dark_midColor : R.color.dark_uploaderBattery);
        @ColorInt final int dividerBgColor = ContextCompat.getColor(getApplicationContext(),
                dividerMatchesBg ? R.color.dark_background : R.color.dark_statusView);

        mLinearLayout.setBackgroundColor(dividerBgColor);
        mLinearLayout2.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mIOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mIOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mCOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mCOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mDay.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mMonth.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mLoop.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));

        setTextSizes();

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
            mUploaderBattery.setTextColor(dividerBatteryOkColor);
        } else {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_uploaderBatteryEmpty));
        }
        mRigBattery.setTextColor(dividerTxtColor);
        mDelta.setTextColor(dividerTxtColor);
        mAvgDelta.setTextColor(dividerTxtColor);
        mBasalRate.setTextColor(dividerTxtColor);
        mBgi.setTextColor(dividerTxtColor);

        if (loopLevel == 1) {
            mLoop.setBackgroundResource(R.drawable.loop_green_25);
        } else {
            mLoop.setBackgroundResource(R.drawable.loop_red_25);
        }

        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor);
            midColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            gridColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
            basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light);
            pointSize = 2;
            setupCharts();
        }
    }

    protected void setColorLowRes() {
        @ColorInt final int dividerTxtColor = dividerMatchesBg ?
                ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor) : Color.BLACK;
        @ColorInt final int dividerBgColor = ContextCompat.getColor(getApplicationContext(),
                dividerMatchesBg ? R.color.dark_background : R.color.dark_statusView);

        mLinearLayout.setBackgroundColor(dividerBgColor);
        mLinearLayout2.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mLoop.setBackgroundResource(R.drawable.loop_grey_25);
        mLoop.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_Timestamp));
        mDelta.setTextColor(dividerTxtColor);
        mAvgDelta.setTextColor(dividerTxtColor);
        mRigBattery.setTextColor(dividerTxtColor);
        mUploaderBattery.setTextColor(dividerTxtColor);
        mBasalRate.setTextColor(dividerTxtColor);
        mBgi.setTextColor(dividerTxtColor);
        mIOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mIOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mCOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mCOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mDay.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mMonth.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            midColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            gridColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark_lowres);
            basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light_lowres);
            pointSize = 2;
            setupCharts();
        }
        setTextSizes();
    }

    protected void setColorBright() {

        if (getCurrentWatchMode() == WatchMode.INTERACTIVE) {

            @ColorInt final int dividerTxtColor = dividerMatchesBg ?  Color.BLACK :
                    ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            @ColorInt final int dividerBgColor = ContextCompat.getColor(getApplicationContext(),
                    dividerMatchesBg ? R.color.light_background : R.color.light_stripe_background);

            mLinearLayout.setBackgroundColor(dividerBgColor);
            mLinearLayout2.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.light_background));
            mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.light_background));
            mTime.setTextColor(Color.BLACK);
            mIOB1.setTextColor(Color.BLACK);
            mIOB2.setTextColor(Color.BLACK);
            mCOB1.setTextColor(Color.BLACK);
            mCOB2.setTextColor(Color.BLACK);
            mDay.setTextColor(Color.BLACK);
            mMonth.setTextColor(Color.BLACK);
            mLoop.setTextColor(Color.BLACK);

            setTextSizes();

            if (rawData.sgvLevel == 1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
            } else if (rawData.sgvLevel == 0) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
            } else if (rawData.sgvLevel == -1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
            }

            if (ageLevel == 1) {
                mTimestamp.setTextColor(Color.BLACK);
            } else {
                mTimestamp.setTextColor(Color.RED);
            }

            if (rawData.batteryLevel == 1) {
                mUploaderBattery.setTextColor(dividerTxtColor);
            } else {
                mUploaderBattery.setTextColor(Color.RED);
            }
            mRigBattery.setTextColor(dividerTxtColor);
            mDelta.setTextColor(dividerTxtColor);
            mAvgDelta.setTextColor(dividerTxtColor);
            mBasalRate.setTextColor(dividerTxtColor);
            mBgi.setTextColor(dividerTxtColor);

            if (loopLevel == 1) {
                mLoop.setBackgroundResource(R.drawable.loop_green_25);
            } else {
                mLoop.setBackgroundResource(R.drawable.loop_red_25);
            }

            if (chart != null) {
                highColor = ContextCompat.getColor(getApplicationContext(), R.color.light_highColor);
                lowColor = ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor);
                midColor = ContextCompat.getColor(getApplicationContext(), R.color.light_midColor);
                gridColor = ContextCompat.getColor(getApplicationContext(), R.color.light_gridColor);
                basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light);
                basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
                pointSize = 2;
                setupCharts();
            }
        } else {
            setColorDark();
        }
    }

    protected void setTextSizes() {

        if (mIOB1 != null && mIOB2 != null) {

            if (rawData.detailedIOB) {
                mIOB1.setTextSize(14);
                mIOB2.setTextSize(10);
            } else {
                mIOB1.setTextSize(10);
                mIOB2.setTextSize(14);
            }
        }
    }
}
