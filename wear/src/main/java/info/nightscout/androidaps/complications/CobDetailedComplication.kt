@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.rx.logging.LTag


/*
 * Created by dlvoy on 2019-11-12
 */
class CobDetailedComplication : BaseComplicationProviderService() {

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            val cob = displayFormat.detailedCob(raw)
            val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(ComplicationText.plainText(cob.first))
                .setTapAction(complicationPendingIntent)
            if (cob.second.isNotEmpty()) {
                builder.setShortTitle(ComplicationText.plainText(cob.second))
            }
            complicationData = builder.build()
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = CobDetailedComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.WIZARD
}