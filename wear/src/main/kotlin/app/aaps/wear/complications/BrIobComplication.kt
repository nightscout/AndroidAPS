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

/*
 * Created by keweki on 2024-12-26
 */
class BrIobComplication : BaseComplicationProviderService() {

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        if (dataType == ComplicationData.TYPE_SHORT_TEXT) {
            val iob = ComplicationText.TimeDifferenceBuilder()
                .setSurroundingText(SmallestDoubleString(raw.status[0].iobSum, SmallestDoubleString.Units.USE).minimise(DisplayFormat.MIN_FIELD_LEN_IOB))
                .setReferencePeriodStart(raw.singleBg[0].timeStamp)
                .setReferencePeriodEnd(raw.singleBg[0].timeStamp + 60000)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_SHORT_SINGLE_UNIT)
                .setMinimumUnit(TimeUnit.MINUTES)
                .setStyle(ComplicationText.DIFFERENCE_STYLE_STOPWATCH)
                .setShowNowText(false)
                .build()
            val builder = ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(iob)
                .setShortTitle(ComplicationText.plainText(displayFormat.basalRateSymbol() + raw.status[0].currentBasal))
                .setTapAction(complicationPendingIntent)
            complicationData = builder.build()
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = BrIobComplication::class.java.canonicalName!!
}