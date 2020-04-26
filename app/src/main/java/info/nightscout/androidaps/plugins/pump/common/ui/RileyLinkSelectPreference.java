package info.nightscout.androidaps.plugins.pump.common.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by andy on 10/18/18.
 */

public class RileyLinkSelectPreference extends Preference {

    public RileyLinkSelectPreference(Context context) {
        super(context);
        setInitialSummaryValue();

        RileyLinkUtil.getInstance().setRileyLinkSelectPreference(this);
    }


    public RileyLinkSelectPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setInitialSummaryValue();

        RileyLinkUtil.getInstance().setRileyLinkSelectPreference(this);
    }


    private void setInitialSummaryValue() {
        String value = SP.getString("pref_rileylink_mac_address", null);

        setSummary(value == null ? MainApp.gs(R.string.rileylink_error_address_not_set_short) : value);
    }

}
