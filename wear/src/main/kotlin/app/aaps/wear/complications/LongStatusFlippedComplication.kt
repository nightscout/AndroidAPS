@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.data.RawDisplayData
import dagger.android.AndroidInjection
import java.util.concurrent.TimeUnit

/*
 * Created by dlvoy on 2019-11-12
 */
class LongStatusFlippedComplication : BaseComplicationProviderService() {

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData? {
        var complicationData: ComplicationData? = null
        when (dataType) {
            ComplicationData.TYPE_LONG_TEXT -> {
                val glucoseLine = ComplicationText.TimeDifferenceBuilder()
                    .setSurroundingText(displayFormat.longGlucoseLine(raw, 0))
                    .setReferencePeriodStart(raw.singleBg[0].timeStamp)
                    .setReferencePeriodEnd(raw.singleBg[0].timeStamp + 60000)
                    .setStyle(ComplicationText.DIFFERENCE_STYLE_SHORT_SINGLE_UNIT)
                    .setMinimumUnit(TimeUnit.MINUTES)
                    .setStyle(ComplicationText.DIFFERENCE_STYLE_STOPWATCH)
                    .setShowNowText(false)
                    .build()
                val detailsLine = ComplicationText.TimeDifferenceBuilder()
                    .setSurroundingText(displayFormat.longDetailsLine(raw, 0))
                    .setReferencePeriodStart(raw.singleBg[0].timeStamp)
                    .setReferencePeriodEnd(raw.singleBg[0].timeStamp + 60000)
                    .setStyle(ComplicationText.DIFFERENCE_STYLE_SHORT_SINGLE_UNIT)
                    .setMinimumUnit(TimeUnit.MINUTES)
                    .setStyle(ComplicationText.DIFFERENCE_STYLE_STOPWATCH)
                    .setShowNowText(false)
                    .build()
                val builderLong = ComplicationData.Builder(ComplicationData.TYPE_LONG_TEXT)
                    .setLongTitle(detailsLine)
                    .setLongText(glucoseLine)
                    .setTapAction(complicationPendingIntent)
                complicationData = builderLong.build()
            }

            else                            -> aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    override fun getProviderCanonicalName(): String = LongStatusFlippedComplication::class.java.canonicalName!!
    override fun usesSinceField(): Boolean = true
}