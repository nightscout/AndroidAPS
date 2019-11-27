package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;
import android.util.Log;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.interaction.utils.SmallestDoubleString;

import static info.nightscout.androidaps.interaction.utils.DisplayFormat.MAX_FIELD_LEN_SHORT;

/*
 * Created by dlvoy on 2019-11-12
 */
public class IobIconComplication extends BaseComplicationProviderService {

    private static final String TAG = IobIconComplication.class.getSimpleName();

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            final String iob = new SmallestDoubleString(raw.sIOB1, SmallestDoubleString.Units.USE).minimise(MAX_FIELD_LEN_SHORT);

            final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(iob))
                    .setIcon(Icon.createWithResource(
                            this, R.drawable.ic_ins))
                    .setBurnInProtectionIcon(
                            Icon.createWithResource(
                                    this, R.drawable.ic_ins_burnin))
                    .setTapAction(complicationPendingIntent);

            complicationData = builder.build();
        } else {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Unexpected complication type " + dataType);
            }
        }
        return complicationData;
    }

    @Override
    public String getProviderCanonicalName() {
        return IobIconComplication.class.getCanonicalName();
    }

    @Override
    public ComplicationAction getComplicationAction() {
        return ComplicationAction.BOLUS;
    };
}
