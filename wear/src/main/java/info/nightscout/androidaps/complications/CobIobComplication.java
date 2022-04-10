package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;

import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.interaction.utils.SmallestDoubleString;
import info.nightscout.shared.logging.LTag;

/*
 * Created by dlvoy on 2019-11-12
 */
public class CobIobComplication extends BaseComplicationProviderService {

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            final String cob = raw.sCOB2;
            final String iob = new SmallestDoubleString(raw.sIOB1, SmallestDoubleString.Units.USE).minimise(displayFormat.MAX_FIELD_LEN_SHORT);

            final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(cob))
                    .setShortTitle(ComplicationText.plainText(iob))
                    .setTapAction(complicationPendingIntent);

            complicationData = builder.build();
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type " + dataType);
        }
        return complicationData;
    }

    @Override
    public String getProviderCanonicalName() {
        return CobIobComplication.class.getCanonicalName();
    }
}
