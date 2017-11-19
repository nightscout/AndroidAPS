package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;

/**
 * Created by Andrew on 18/11/2017.
 */

public class Cockpit extends BaseWatchFace {

    private long sgvTapTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_cockpit, null);
        performViewSetup();
        final WatchViewStub stub = (WatchViewStub) layoutView.findViewById(R.id.watch_view_stub);
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
    protected WatchFaceStyle getWatchFaceStyle(){
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
    }

    protected void setColorDark() {

        /*
        //set text sizes
        float scaleFactor = specH / 400f; //the design assumes 400dp is the default screen height.
        if (mTime != null) mTime.setTextSize(18*scaleFactor);
        if (mSgv != null) mSgv.setTextSize(38*scaleFactor);
        if (mDirection != null) mDirection.setTextSize(30*scaleFactor);
        if (mDelta != null) mDelta.setTextSize(14*scaleFactor);
        if (mBasalRate != null) mBasalRate.setTextSize(14*scaleFactor);
        if (mIOB2 != null) mIOB2.setTextSize(14*scaleFactor);
        if (mCOB2 != null) mCOB2.setTextSize(14*scaleFactor);
        if (mUploaderBattery != null) mUploaderBattery.setTextSize(14*scaleFactor);
        if (mRigBattery != null) mRigBattery.setTextSize(14*scaleFactor);
        if (mTimestamp != null) mTimestamp.setTextSize(14*scaleFactor);
        if (mLoop != null) mLoop.setTextSize(14*scaleFactor);
        if (mHighLight != null) mHighLight.setTextSize(8*scaleFactor);
        if (mLowLight != null) mLowLight.setTextSize(8*scaleFactor);
        if (isAAPSv2 != null) isAAPSv2.setTextSize(16*scaleFactor);
        */

        Log.d("Lights", "mHighLight is " + mHighLight + " and mLowLight is " + mLowLight + ". sgvLevel is " + sgvLevel);
        if (mHighLight != null && mLowLight != null) {
            if (sgvLevel == 1) {
                mHighLight.setBackgroundResource(R.drawable.airplane_led_yellow_lit);
                mLowLight.setBackgroundResource(R.drawable.airplane_led_grey_unlit);
            } else if (sgvLevel == 0) {
                mHighLight.setBackgroundResource(R.drawable.airplane_led_grey_unlit);
                mLowLight.setBackgroundResource(R.drawable.airplane_led_grey_unlit);
            } else if (sgvLevel == -1) {
                mHighLight.setBackgroundResource(R.drawable.airplane_led_grey_unlit);
                mLowLight.setBackgroundResource(R.drawable.airplane_led_red_lit);
            }
        }

        int paddingPixel;
        int paddingDp;
        float density = this.getResources().getDisplayMetrics().density;

        if (sharedPrefs.getBoolean("show_uploader_battery", true) && sharedPrefs.getBoolean("show_rig_battery", false)) {
            paddingPixel = 8;
            mUploaderBattery.setTextSize(10);
            mRigBattery.setTextSize(10);
        } else {
            paddingPixel = 3;
            mUploaderBattery.setTextSize(14);
            mRigBattery.setTextSize(14);
        }
        paddingDp = (int)(paddingPixel * density);
        mUploaderBattery.setPadding(0, paddingDp,0,0);
        mRigBattery.setPadding(0, paddingDp,0,0);

        if (mIOB2 != null) {
            if (detailedIOB) {
                paddingPixel = 8;
                mIOB2.setTextSize(10);
            } else {
                paddingPixel = 3;
                mIOB2.setTextSize(14);
            }
            paddingDp = (int)(paddingPixel * density);
            mIOB2.setPadding(0, paddingDp,0,0);
        }

        invalidate();

    }

    protected void setColorLowRes() {
        setColorDark();
    }

    protected void setColorBright() {
        setColorDark();
    }
}