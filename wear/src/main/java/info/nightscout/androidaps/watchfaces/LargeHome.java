package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.LayoutInflater;

import com.ustwo.clockwise.common.WatchMode;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;

public class LargeHome extends BaseWatchFace {

    private long sgvTapTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_home_large, null);
        performViewSetup();
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

    @Override
    protected WatchFaceStyle getWatchFaceStyle(){
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
    }

    @Override
    protected void setColorDark(){
        mLinearLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ?
                R.color.dark_background : R.color.dark_mLinearLayout));
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        if (rawData.sgvLevel == 1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
        } else if (rawData.sgvLevel == 0) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else if (rawData.sgvLevel == -1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
        }

        if (ageLevel == 1) {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ?
                    R.color.dark_midColor : R.color.dark_mTimestamp1_home));
        } else {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_TimestampOld));
        }

        if (rawData.batteryLevel == 1) {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ?
                    R.color.dark_midColor : R.color.dark_uploaderBattery));
        } else {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_uploaderBatteryEmpty));
        }

        mStatus.setTextColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ? R.color.dark_midColor : R.color.dark_mStatus_home));
    }

    @Override
    protected void setColorBright() {
        if (getCurrentWatchMode() == WatchMode.INTERACTIVE) {
            mLinearLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ?
                    R.color.light_background : R.color.light_stripe_background));
            mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.light_background));
            if (rawData.sgvLevel == 1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
            } else if (rawData.sgvLevel == 0) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
            } else if (rawData.sgvLevel == -1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
            }

            if (ageLevel == 1) {
                mTimestamp.setTextColor(dividerMatchesBg ? Color.BLACK : Color.WHITE);
            } else {
                mTimestamp.setTextColor(Color.RED);
            }

            if (rawData.batteryLevel == 1) {
                mUploaderBattery.setTextColor(dividerMatchesBg ? Color.BLACK : Color.WHITE);
            } else {
                mUploaderBattery.setTextColor(Color.RED);
            }
            mStatus.setTextColor(dividerMatchesBg ? Color.BLACK : Color.WHITE);
            mTime.setTextColor(Color.BLACK);
        } else {
            mRelativeLayout.setBackgroundColor(Color.BLACK);
            mLinearLayout.setBackgroundColor(dividerMatchesBg ? Color.BLACK : Color.LTGRAY);
            if (rawData.sgvLevel == 1) {
                mSgv.setTextColor(Color.YELLOW);
                mDirection.setTextColor(Color.YELLOW);
                mDelta.setTextColor(Color.YELLOW);
            } else if (rawData.sgvLevel == 0) {
                mSgv.setTextColor(Color.WHITE);
                mDirection.setTextColor(Color.WHITE);
                mDelta.setTextColor(Color.WHITE);
            } else if (rawData.sgvLevel == -1) {
                mSgv.setTextColor(Color.RED);
                mDirection.setTextColor(Color.RED);
                mDelta.setTextColor(Color.RED);
            }

            mUploaderBattery.setTextColor(dividerMatchesBg ? Color.WHITE : Color.BLACK);
            mTimestamp.setTextColor(dividerMatchesBg ? Color.WHITE : Color.BLACK);
            mStatus.setTextColor(dividerMatchesBg ? Color.WHITE : Color.BLACK);
            mTime.setTextColor(Color.WHITE);
        }
    }

    @Override
    protected void setColorLowRes() {
        mLinearLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ? R.color.dark_background : R.color.dark_mLinearLayout));
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ? R.color.dark_midColor : R.color.dark_mTimestamp1_home));
        mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ? R.color.dark_midColor : R.color.dark_uploaderBattery));
        mStatus.setTextColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ? R.color.dark_midColor : R.color.dark_mStatus_home));
    }
}
