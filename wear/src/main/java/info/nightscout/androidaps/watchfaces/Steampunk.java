package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;

/**
 * Created by andrew-warrington on 01/12/2017.
 */

public class Steampunk extends BaseWatchFace {

    private long sgvTapTime = 0;
    public ImageView mGlucoseDial;
    private float lastEndDegrees = 0f;

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_steampunk, null);
        performViewSetup();
        mGlucoseDial = (ImageView) layoutView.findViewById(R.id.glucose_dial);
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

        //rotate glucose dial.
        float rotationAngle = 0;
        if (sSgv != "---") {
            rotationAngle = Float.valueOf(sSgv);
        }
        if (rotationAngle > 330) rotationAngle = 330;
        if (rotationAngle != 0 && rotationAngle < 30) rotationAngle = 30;

        RotateAnimation rotate = new RotateAnimation(
                lastEndDegrees, rotationAngle - lastEndDegrees,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setFillAfter(true);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setDuration(2000);
        mGlucoseDial.startAnimation(rotate);
        lastEndDegrees = rotationAngle;

        setTextSizes();

        if (mLoop != null) {
            mLoop.setBackgroundResource(0);
        }

        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.light_highColor);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor);
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
        return;
    }

    protected void setColorBright() {
        setColorDark();
    }

    protected void setTextSizes() {

        if (mUploaderBattery != null && mRigBattery != null) {
            if (bIsRound) {
                mUploaderBattery.setTextSize(13);
                mRigBattery.setTextSize(13);
            } else {
                mUploaderBattery.setTextSize(12);
                mRigBattery.setTextSize(12);
            }
        }
    }
}