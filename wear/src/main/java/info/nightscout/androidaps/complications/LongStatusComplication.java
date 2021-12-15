package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;
import android.util.Log;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.interaction.utils.DisplayFormat;

/*
 * Created by dlvoy on 2019-11-12
 */
public class LongStatusComplication extends BaseComplicationProviderService {

    @Inject DisplayFormat displayFormat;

    // Not derived from DaggerService, do injection here
    @Override
    public void onCreate() {
        AndroidInjection.inject(this);
        super.onCreate();
    }

    private static final String TAG = LongStatusComplication.class.getSimpleName();

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        switch (dataType) {
            case ComplicationData.TYPE_LONG_TEXT:

                final String glucoseLine = displayFormat.longGlucoseLine(raw);
                final String detailsLine = displayFormat.longDetailsLine(raw);

                final ComplicationData.Builder builderLong = new ComplicationData.Builder(ComplicationData.TYPE_LONG_TEXT)
                        .setLongTitle(ComplicationText.plainText(glucoseLine))
                        .setLongText(ComplicationText.plainText(detailsLine))
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
        return LongStatusComplication.class.getCanonicalName();
    }

    @Override
    protected boolean usesSinceField() {
        return true;
    }
}
