package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.interaction.utils.DisplayFormat;
import info.nightscout.shared.logging.LTag;

/*
 * Created by dlvoy on 2019-11-12
 */
public class SgvComplication extends BaseComplicationProviderService {

    @Inject DisplayFormat displayFormat;

    // Not derived from DaggerService, do injection here
    @Override
    public void onCreate() {
        AndroidInjection.inject(this);
        super.onCreate();
    }

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        switch (dataType) {
            case ComplicationData.TYPE_SHORT_TEXT:
                final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                        .setShortText(ComplicationText.plainText(raw.sSgv + raw.sDirection + "\uFE0E"))
                        .setShortTitle(ComplicationText.plainText(displayFormat.shortTrend(raw)))
                        .setTapAction(complicationPendingIntent);

                complicationData = builder.build();
                break;
            default:
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type " + dataType);
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
