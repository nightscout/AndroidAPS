@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.data.RawDisplayData
import app.aaps.wear.interaction.utils.DisplayFormat
import app.aaps.wear.interaction.utils.SmallestDoubleString
import dagger.android.AndroidInjection
import java.util.concurrent.TimeUnit
import kotlin.math.max

/*
 * Created by dlvoy on 2019-11-12
 */
class BrCobIobComplication : BaseComplicationProviderService() {

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            val cob = SmallestDoubleString(raw.status[0].cob, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MIN_FIELD_LEN_COB)
            val iob = SmallestDoubleString(raw.status[0].iobSum, SmallestDoubleString.Units.USE).minimise(max(DisplayFormat.MIN_FIELD_LEN_IOB, DisplayFormat.MAX_FIELD_LEN_SHORT - 1 - cob.length))
            val iobCob = ComplicationText.TimeDifferenceBuilder()
                .setSurroundingText("$cob $iob")
                .setReferencePeriodStart(raw.singleBg[0].timeStamp)
                .setReferencePeriodEnd(raw.singleBg[0].timeStamp + 60000)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_SHORT_SINGLE_UNIT)
                .setMinimumUnit(TimeUnit.MINUTES)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_STOPWATCH)
                .setShowNowText(false)
                .build()
            val tbr = ComplicationText.TimeDifferenceBuilder()
                .setSurroundingText(displayFormat.basalRateSymbol() + raw.status[0].currentBasal)
                .setReferencePeriodStart(raw.singleBg[0].timeStamp)
                .setReferencePeriodEnd(raw.singleBg[0].timeStamp + 60000)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_SHORT_SINGLE_UNIT)
                .setMinimumUnit(TimeUnit.MINUTES)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_STOPWATCH)
                .setShowNowText(false)
                .build()
            val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(tbr)
                .setShortTitle(iobCob)
                .setTapAction(complicationPendingIntent)
            complicationData = builder.build()
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = BrCobIobComplication::class.java.canonicalName!!
}
