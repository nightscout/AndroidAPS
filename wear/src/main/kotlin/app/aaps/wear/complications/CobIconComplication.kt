@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.data.RawDisplayData

/*
 * Created by dlvoy on 2019-11-12
 */
class CobIconComplication : BaseComplicationProviderService() {

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(ComplicationText.plainText(raw.status[0].cob))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_carbs))
                .setBurnInProtectionIcon(Icon.createWithResource(this, R.drawable.ic_carbs))
                .setTapAction(complicationPendingIntent)
            complicationData = builder.build()
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = CobIconComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.WIZARD
}