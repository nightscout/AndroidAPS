package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;
import android.util.Log;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.interaction.utils.DisplayFormat;
import info.nightscout.androidaps.interaction.utils.SmallestDoubleString;

/*
 * Created by dlvoy on 2019-11-12
 */
public class BrCobIobComplication extends BaseComplicationProviderService {

    @Inject DisplayFormat displayFormat;

    // Not derived from DaggerService, do injection here
    @Override
    public void onCreate() {
        AndroidInjection.inject(this);
        super.onCreate();
    }

    private static final String TAG = BrCobIobComplication.class.getSimpleName();

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            final String cob = new SmallestDoubleString(raw.sCOB2, SmallestDoubleString.Units.USE).minimise(displayFormat.MIN_FIELD_LEN_COB);
            final String iob = new SmallestDoubleString(raw.sIOB1, SmallestDoubleString.Units.USE).minimise(Math.max(displayFormat.MIN_FIELD_LEN_IOB, (displayFormat.MAX_FIELD_LEN_SHORT -1) - cob.length()));

            final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(displayFormat.basalRateSymbol()+raw.sBasalRate))
                    .setShortTitle(ComplicationText.plainText(cob + " " + iob))
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
        return BrCobIobComplication.class.getCanonicalName();
    }
}
