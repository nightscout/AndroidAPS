package info.nightscout.androidaps.watchfaces;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;
import info.nightscout.shared.logging.LTag;

public class DigitalStyle extends BaseWatchFace {
    private static final long TIME_TAP_THRESHOLD = 800;
    private long sgvTapTime = 0;

    @SuppressLint("InflateParams") @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_digitalstyle, null);
        performViewSetup();
    }


    @Override
    protected void onTapCommand(int tapType, int x, int y, long eventTime) {
        //tapType = TAP_TYPE_TAP;
        aapsLogger.debug(LTag.WEAR,"onTapCommand: DeviceWidth x DeviceHeight   ///  x , y, TapType  >> ", getWidth() + " x " + getHeight() + " ///  " + x + " , " + y + " , " + tapType);

        if (tapType == TAP_TYPE_TAP) {
            if (eventTime - sgvTapTime < TIME_TAP_THRESHOLD) {
                Intent intent = new Intent(this, MainMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            sgvTapTime = eventTime;
        }
    }


    @SuppressWarnings("deprecation")
    @Override
    protected WatchFaceStyle getWatchFaceStyle() {
        return new WatchFaceStyle.Builder(this)
                .setAcceptsTapEvents(true)
                .setHideNotificationIndicator(false)
                .setShowUnreadCountIndicator(true)
                .build();
    }

    protected void setColorDark() {
        if (singleBg.getSgvLevel() == 1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
        } else if (singleBg.getSgvLevel() == 0) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else if (singleBg.getSgvLevel() == -1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
        }

        if (ageLevel == 1) {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_TimestampOld));
        }

        if (status.getBatteryLevel() == 1) {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_uploaderBatteryEmpty));
        }


        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor);
            midColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            gridColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
            basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light);
            pointSize = 1;
            setupCharts();
            setWatchfaceStyle();
        }
    }

    private void setWatchfaceStyle(){
        /* frame styles*/
        LinearLayout mShapesElements = layoutView.findViewById(R.id.shapes_elements);
        if (mShapesElements != null) {
            String displayFormatType = (mShapesElements.getContentDescription().toString().startsWith("round") ? "round" : "rect");
            String displayStyle=sp.getString("digitalstyle_frameStyle", "full");
            String displayFrameColor=sp.getString("digitalstyle_frameColor", "red");
            String displayFrameColorSaturation=sp.getString("digitalstyle_frameColorSaturation",
                    "500");
            String displayFrameColorOpacity=sp.getString("digitalstyle_frameColorOpacity", "1");

            // Load image with shapes
            String styleDrawableName = "digitalstyle_bg_" + displayStyle + "_" + displayFormatType;
            try {
                mShapesElements.setBackground(getResources().getDrawable(getResources().getIdentifier(styleDrawableName, "drawable", getApplicationContext().getPackageName())));
            } catch (Exception e) {
                aapsLogger.error("digitalstyle_frameStyle", "RESOURCE NOT FOUND >> " + styleDrawableName);
            }

            // set background-tint-color
            if (displayFrameColor.equalsIgnoreCase("multicolor") || displayStyle.equalsIgnoreCase("none")) {
                mShapesElements.setBackgroundTintList(null);
            } else {
                String strColorName =((   displayFrameColor.equals("white") || displayFrameColor.equals("black")  )?displayFrameColor:displayFrameColor+"_"+displayFrameColorSaturation);
                aapsLogger.debug(LTag.WEAR,"digitalstyle_strColorName",strColorName);
                try {
                    ColorStateList colorStateList = ContextCompat.getColorStateList(getApplicationContext(), getResources().getIdentifier(strColorName, "color", getApplicationContext().getPackageName()));
                    mShapesElements.setBackgroundTintList(colorStateList);
                } catch (Exception e) {
                    mShapesElements.setBackgroundTintList(null);
                    aapsLogger.error("digitalstyle_colorName", "COLOR NOT FOUND >> " + strColorName);
                }
            }

            // set opacity of shapes
            mShapesElements.setAlpha(Float.parseFloat(displayFrameColorOpacity));

        }

        /* optimize font-size  --> when date is off then increase font-size of time */
        Boolean isShowDate = sp.getBoolean("show_date", false);
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
            Boolean isShowWeekNumber = sp.getBoolean("show_weeknumber", false);
            aapsLogger.info(LTag.WEAR,"---------------------------------","weeknumber refresh ");
            TextView mWeekNumber= layoutView.findViewById(R.id.weeknumber);
            if (isShowWeekNumber) {
                mWeekNumber.setVisibility(View.VISIBLE);
                mWeekNumber.setText("(" + (new SimpleDateFormat("ww")).format(new Date()) + ")");
            } else {
                mWeekNumber.setVisibility(View.GONE);
                mWeekNumber.setText("");
            }
        }


    }

    protected void setColorLowRes() {
        setColorDark();
    }
    protected void setColorBright() { setColorDark();   /* getCurrentWatchMode() == WatchMode.AMBIENT or WatchMode.INTERACTIVE */}

}
