package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;
import android.util.Log;

import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.interaction.utils.DisplayFormat;

/*
 * Created by dlvoy on 2019-11-12
 */
public class SgvComplication extends BaseComplicationProviderService {

    private static final String TAG = SgvComplication.class.getSimpleName();

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        switch (dataType) {
            case ComplicationData.TYPE_SHORT_TEXT:
                final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                        .setShortText(ComplicationText.plainText(raw.sSgv + raw.sDirection))
                        .setShortTitle(ComplicationText.plainText(DisplayFormat.shortTrend(raw)))
                        .setTapAction(complicationPendingIntent);

                complicationData = builder.build();
                break;
            default:
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "Unexpected complication type " + dataType);
                }
        }
        return complicationData;
    }

    @Override
    public String getProviderCanonicalName() {
        return SgvComplication.class.getCanonicalName();
    }

    @Override
    protected boolean usesSinceField() {
        return true;
    }
}
