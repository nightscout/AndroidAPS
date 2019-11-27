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
public class LongStatusFlippedComplication extends BaseComplicationProviderService {

    private static final String TAG = LongStatusFlippedComplication.class.getSimpleName();

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        switch (dataType) {
            case ComplicationData.TYPE_LONG_TEXT:

                final String glucoseLine = DisplayFormat.longGlucoseLine(raw);
                final String detailsLine = DisplayFormat.longDetailsLine(raw);

                final ComplicationData.Builder builderLong = new ComplicationData.Builder(ComplicationData.TYPE_LONG_TEXT)
                        .setLongTitle(ComplicationText.plainText(detailsLine))
                        .setLongText(ComplicationText.plainText(glucoseLine))
                        .setTapAction(complicationPendingIntent);
                complicationData = builderLong.build();

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
        return LongStatusFlippedComplication.class.getCanonicalName();
    }

    @Override
    protected boolean usesSinceField() {
        return true;
    }
}
