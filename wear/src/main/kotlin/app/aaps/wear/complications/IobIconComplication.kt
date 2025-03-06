@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import app.aaps.wear.data.RawDisplayData
import app.aaps.wear.interaction.utils.DisplayFormat
import app.aaps.wear.interaction.utils.SmallestDoubleString
import java.util.concurrent.TimeUnit

/*
 * Created by dlvoy on 2019-11-12
 */
class IobIconComplication : BaseComplicationProviderService() {

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            val iobSum = ComplicationText.TimeDifferenceBuilder()
                .setSurroundingText(SmallestDoubleString(raw.status[0].iobSum, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MAX_FIELD_LEN_SHORT))
                .setReferencePeriodStart(raw.singleBg[0].timeStamp)
                .setReferencePeriodEnd(raw.singleBg[0].timeStamp + 60000)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_SHORT_SINGLE_UNIT)
                .setMinimumUnit(TimeUnit.MINUTES)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_STOPWATCH)
                .setShowNowText(false)
                .build()
            val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(iobSum)
                .setIcon(Icon.createWithResource(this, R.drawable.ic_ins))
                .setBurnInProtectionIcon(Icon.createWithResource(this, R.drawable.ic_ins_burnin))
                .setTapAction(complicationPendingIntent)
            complicationData = builder.build()
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = IobIconComplication::class.java.canonicalName!!
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.BOLUS
}
