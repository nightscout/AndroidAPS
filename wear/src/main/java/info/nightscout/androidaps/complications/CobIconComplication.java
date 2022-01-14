package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.shared.logging.LTag;

/*
 * Created by dlvoy on 2019-11-12
 */
public class CobIconComplication extends BaseComplicationProviderService {

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(raw.sCOB2))
                    .setIcon(
                            Icon.createWithResource(
                                    this, R.drawable.ic_carbs))
                    .setBurnInProtectionIcon(
                            Icon.createWithResource(
                                    this, R.drawable.ic_carbs))
                    .setTapAction(complicationPendingIntent);

            complicationData = builder.build();
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type " + dataType);
        }
        return complicationData;
    }

    @Override
    public String getProviderCanonicalName() {
        return CobIconComplication.class.getCanonicalName();
    }

    public ComplicationAction getComplicationAction() {
        return ComplicationAction.WIZARD;
    }
}
