package info.nightscout.androidaps.plugins.general.overview;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 3/2/19.
 */

public enum OverviewColorScheme {

    TempTargetNotSet(R.color.ribbonDefault, R.color.ribbonTextDefault, R.color.tempTargetDisabledBackground, R.color.white),
    TempTargetSet(R.color.ribbonWarning, R.color.ribbonTextWarning, R.color.tempTargetBackground, R.color.black),

    APS_Loop_Enabled(R.color.ribbonDefault, R.color.ribbonTextDefault, R.color.loopenabled, R.color.black),
    APS_SuperBolus(R.color.ribbonWarning, R.color.ribbonTextWarning, R.color.looppumpsuspended, R.color.white),
    APS_Loop_Disconnected(R.color.ribbonCritical, R.color.ribbonTextCritical, R.color.looppumpsuspended, R.color.white),
    APS_Loop_Suspended(R.color.ribbonWarning, R.color.ribbonTextWarning, R.color.looppumpsuspended, R.color.white),
    APS_Pump_Suspended(R.color.ribbonWarning, R.color.ribbonTextWarning, R.color.loopenabled, R.color.white),
    APS_Loop_Disabled(R.color.ribbonCritical, R.color.ribbonTextCritical, R.color.loopdisabled, R.color.white),

    ProfileNormal(R.color.ribbonDefault, R.color.ribbonTextDefault, R.color.gray, R.color.white),
    ProfileChanged(R.color.ribbonWarning, R.color.ribbonTextWarning, R.color.gray, R.color.white),

    ;

    int newBackground;
    int newTextColor;
    int oldBackground;
    int oldTextColor;


    OverviewColorScheme(int newBackground, int newTextColor, int oldBackground, int oldTextColor) {
        this.newBackground = newBackground;
        this.newTextColor = newTextColor;
        this.oldBackground = oldBackground;
        this.oldTextColor = oldTextColor;
    }


    public int getBackground(boolean isNew) {
        return isNew ? this.newBackground : this.oldBackground;
    }


    public int getTextColor(boolean isNew) {
        return isNew ? this.newTextColor : this.oldTextColor;
    }

}
