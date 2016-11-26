package info.nightscout.androidaps;

import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;

import com.ustwo.clockwise.common.WatchMode;

public class Home extends BaseWatchFace {

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_home, null);
        performViewSetup();
    }

    protected void setColorDark() {
        mLinearLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_statusView));
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        if (sgvLevel == 1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
        } else if (sgvLevel == 0) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else if (sgvLevel == -1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
        }

        if (ageLevel == 1) {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTimestamp1_home));
        } else {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTimestamp));
        }

        if (batteryLevel == 1) {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_uploaderBattery));
        } else {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_uploaderBatteryEmpty));
        }

        mStatus.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mStatus_home));

        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor);
            midColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            singleLine = false;
            pointSize = 2;
            setupCharts();
        }
    }


    protected void setColorBright() {

        if (getCurrentWatchMode() == WatchMode.INTERACTIVE) {
            mLinearLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.light_stripe_background));
            mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.light_background));
            if (sgvLevel == 1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
            } else if (sgvLevel == 0) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
            } else if (sgvLevel == -1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
            }

            if (ageLevel == 1) {
                mTimestamp.setTextColor(Color.WHITE);
            } else {
                mTimestamp.setTextColor(Color.RED);
            }

            if (batteryLevel == 1) {
                mUploaderBattery.setTextColor(Color.WHITE);
            } else {
                mUploaderBattery.setTextColor(Color.RED);
            }
            mStatus.setTextColor(Color.WHITE);

            mTime.setTextColor(Color.BLACK);
            if (chart != null) {
                highColor = ContextCompat.getColor(getApplicationContext(), R.color.light_highColor);
                lowColor = ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor);
                midColor = ContextCompat.getColor(getApplicationContext(), R.color.light_midColor);
                singleLine = false;
                pointSize = 2;
                setupCharts();
            }
        } else {
            mRelativeLayout.setBackgroundColor(Color.BLACK);
            mLinearLayout.setBackgroundColor(Color.WHITE);
            if (sgvLevel == 1) {
                mSgv.setTextColor(Color.YELLOW);
                mDirection.setTextColor(Color.YELLOW);
                mDelta.setTextColor(Color.YELLOW);
            } else if (sgvLevel == 0) {
                mSgv.setTextColor(Color.WHITE);
                mDirection.setTextColor(Color.WHITE);
                mDelta.setTextColor(Color.WHITE);
            } else if (sgvLevel == -1) {
                mSgv.setTextColor(Color.RED);
                mDirection.setTextColor(Color.RED);
                mDelta.setTextColor(Color.RED);
            }
            mStatus.setTextColor(Color.BLACK);
            mUploaderBattery.setTextColor(Color.BLACK);
            mTimestamp.setTextColor(Color.BLACK);

            mTime.setTextColor(Color.WHITE);
            if (chart != null) {
                highColor = Color.YELLOW;
                midColor = Color.WHITE;
                lowColor = Color.RED;
                singleLine = true;
                pointSize = 2;
                setupCharts();
            }
        }
    }
}