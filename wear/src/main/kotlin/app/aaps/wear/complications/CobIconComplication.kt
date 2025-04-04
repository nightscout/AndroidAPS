@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.data.RawDisplayData
import java.util.concurrent.TimeUnit

/*
 * Created by dlvoy on 2019-11-12
 */
class CobIconComplication : BaseComplicationProviderService() {

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            val cob = ComplicationText.TimeDifferenceBuilder()
                .setSurroundingText(raw.status[0].cob)
                .setReferencePeriodStart(raw.singleBg[0].timeStamp)
                .setReferencePeriodEnd(raw.singleBg[0].timeStamp + 60000)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_SHORT_SINGLE_UNIT)
                .setMinimumUnit(TimeUnit.MINUTES)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_STOPWATCH)
                .setShowNowText(false)
                .build()
            val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(cob)
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