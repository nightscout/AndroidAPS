package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;
import android.util.Log;

import info.nightscout.androidaps.data.DisplayRawData;
import info.nightscout.androidaps.interaction.utils.SmallestDoubleString;

import static info.nightscout.androidaps.interaction.utils.DisplayFormat.MAX_SHORT_FIELD;

/*
 * Created by dlvoy on 2019-11-12
 */
public class CobIobComplication extends BaseComplicationProviderService {

    private static final String TAG = CobIobComplication.class.getSimpleName();

    public ComplicationData buildComplicationData(int dataType, DisplayRawData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            final String cob = raw.sCOB2;
            final String iob = new SmallestDoubleString(raw.sIOB1, SmallestDoubleString.Units.USE).minimise(MAX_SHORT_FIELD);

            final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(cob))
                    .setShortTitle(ComplicationText.plainText(iob))
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
        return CobIobComplication.class.getCanonicalName();
    }
}
