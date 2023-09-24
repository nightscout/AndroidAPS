@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.androidaps.interaction.utils.DisplayFormat
import info.nightscout.androidaps.interaction.utils.SmallestDoubleString

/*
 * Created by dlvoy on 2019-11-12
 */
class CobIobComplication : BaseComplicationProviderService() {

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            val cob = raw.status.cob
            val iob = SmallestDoubleString(raw.status.iobSum, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MAX_FIELD_LEN_SHORT)
            val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(ComplicationText.plainText(cob))
                .setShortTitle(ComplicationText.plainText(iob))
                .setTapAction(complicationPendingIntent)
            complicationData = builder.build()
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = CobIobComplication::class.java.canonicalName!!
}
